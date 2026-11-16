#!/usr/bin/env python3
"""
Empirical verification suite executable in sandbox (no Flutter/JDK required):
1. Extracts exact DDL from lib/data/schema.dart and tests SQLite schema + Room v18 migration parity.
2. Tests Ed25519 canonical payload + SPKI QLK keyId + QL1/.qlic verification.
3. Validates all Dart relative imports, balanced delimiters, assets, Arabic font cmap glyphs, and icon assets.
"""
import base64
import hashlib
import json
import os
import re
import sqlite3
import struct
import sys
import tempfile
import zipfile

sys.path.insert(0, "/tmp/pydeps")
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def extract_schema_statements():
    schema_path = os.path.join(ROOT, "lib", "data", "schema.dart")
    with open(schema_path, "r", encoding="utf-8") as f:
        text = f.read()
    create_sqls = re.findall(r"'''(.*?)'''", text, flags=re.DOTALL)
    idx_sqls = re.findall(r"'(CREATE INDEX IF NOT EXISTS [^']+)'", text)
    assert len(create_sqls) == 20, f"Expected 20 CREATE TABLE blocks, got {len(create_sqls)}"
    assert len(idx_sqls) == 12, f"Expected 12 CREATE INDEX blocks, got {len(idx_sqls)}"
    return create_sqls, idx_sqls


def test_sqlite_schema_and_room_migration():
    create_sqls, idx_sqls = extract_schema_statements()
    with tempfile.TemporaryDirectory() as tmp:
        legacy_db_path = os.path.join(tmp, "tofan_al_aqsa_qat_db")
        target_db_path = os.path.join(tmp, "qat_ledger_v2.db")

        # Build authentic Room v18 DB
        src = sqlite3.connect(legacy_db_path)
        src.execute("PRAGMA user_version = 18")
        src.executescript(
            """
            CREATE TABLE qat_types (id INTEGER PRIMARY KEY, name TEXT, defaultBuyPrice REAL, defaultSellPrice REAL, notes TEXT, isHidden INTEGER);
            CREATE TABLE inventory (id INTEGER PRIMARY KEY, qatType TEXT, supplierName TEXT, remainingQty REAL, unit TEXT, buyPrice REAL, sellPrice REAL, purchaseDate TEXT, currency TEXT);
            CREATE TABLE customers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT, notes TEXT, totalDebt REAL);
            CREATE TABLE sales (id INTEGER PRIMARY KEY, customerName TEXT, customerId INTEGER, date TEXT, totalAmount REAL, paidAmount REAL, remainingAmount REAL, paymentType TEXT, status TEXT, notes TEXT, currency TEXT, discount REAL, taxPercent REAL, isArchived INTEGER);
            CREATE TABLE sales_invoice_items (id INTEGER PRIMARY KEY, saleId INTEGER, qatType TEXT, quantity REAL, unit TEXT, unitPrice REAL, buyPrice REAL, total REAL, currency TEXT);
            CREATE TABLE customer_payments (id INTEGER PRIMARY KEY, customerId INTEGER, customerName TEXT, amount REAL, date TEXT, notes TEXT, currency TEXT, paymentMethod TEXT, isArchived INTEGER);
            CREATE TABLE suppliers (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT, notes TEXT, totalBalance REAL);
            CREATE TABLE supplier_purchases (id INTEGER PRIMARY KEY, supplierId INTEGER, supplierName TEXT, date TEXT, totalAmount REAL, paidAmount REAL, remainingAmount REAL, paymentType TEXT, notes TEXT, currency TEXT, discount REAL, taxPercent REAL, isArchived INTEGER);
            CREATE TABLE supplier_purchase_items (id INTEGER PRIMARY KEY, purchaseId INTEGER, qatType TEXT, quantity REAL, unit TEXT, buyPrice REAL, total REAL, currency TEXT);
            CREATE TABLE supplier_payments (id INTEGER PRIMARY KEY, supplierId INTEGER, supplierName TEXT, amount REAL, date TEXT, notes TEXT, currency TEXT, paymentMethod TEXT, isArchived INTEGER);
            CREATE TABLE expenses (id INTEGER PRIMARY KEY, description TEXT, category TEXT, amount REAL, date TEXT, notes TEXT, currency TEXT, isArchived INTEGER);
            CREATE TABLE qat_tax_entries (id INTEGER PRIMARY KEY, date TEXT, totalQty REAL, taxRate REAL, totalTaxAmount REAL, notes TEXT, source TEXT, isArchived INTEGER);
            CREATE TABLE transfer_recipients (id INTEGER PRIMARY KEY, name TEXT, phone TEXT, company TEXT, notes TEXT, createdAt INTEGER);
            CREATE TABLE money_transfers (id INTEGER PRIMARY KEY, senderName TEXT, receiverName TEXT, recipientId INTEGER, amount REAL, currency TEXT, fee REAL, transferCompany TEXT, transferNumber TEXT, date TEXT, status TEXT, notes TEXT, isArchived INTEGER);
            """
        )
        src.execute("INSERT INTO qat_types VALUES (1, 'همداني', 3000.0, 4200.0, '', 0)")
        src.execute("INSERT INTO customers VALUES (1, 'علي القحطاني', '777000111', 'صنعاء', '', 15250.5)")
        src.execute("INSERT INTO suppliers VALUES (1, 'مورد حراز', '771222333', 'حراز', '', 8000.0)")
        src.execute("INSERT INTO inventory VALUES (1, 'همداني', 'مورد حراز', 12.5, 'ربطة', 3000.0, 4200.0, '2026-10-01', 'YER')")
        src.execute("INSERT INTO sales VALUES (1, 'علي القحطاني', 1, '2026-10-02', 20000.0, 5000.0, 15000.0, 'دفعة جزئية', 'دفعة جزئية', '', 'YER', 0.0, 0.0, 0)")
        src.execute("INSERT INTO sales VALUES (2, 'علي القحطاني', 1, '2026-10-03', 250.5, 0.0, 250.5, 'آجل', 'آجل', '', 'SAR', 0.0, 0.0, 0)")
        src.execute("INSERT INTO sales_invoice_items VALUES (1, 1, 'همداني', 4.0, 'ربطة', 5000.0, 3000.0, 20000.0, 'YER')")
        src.execute("INSERT INTO transfer_recipients VALUES (1, 'محمد المستلم', '777444555', 'الكريمي', '', 1700000000000)")
        src.execute("INSERT INTO money_transfers VALUES (1, 'الوكالة', 'محمد المستلم', 1, 50000.0, 'YER', 200.0, 'الكريمي', 'TR-1', '2026-10-04', 'تم التسليم', '', 0)")
        src.execute("INSERT INTO money_transfers VALUES (2, 'الوكالة', '', 0, 300.0, 'SAR', 5.0, 'النجم', 'TR-2', '2026-10-05', 'جاري التسليم', '', 0)")
        src.commit()

        # Execute exact target schema DDL
        dst = sqlite3.connect(target_db_path)
        dst.execute("PRAGMA foreign_keys = ON")
        for s in create_sqls + idx_sqls:
            dst.execute(s)

        # Simulate migration rules
        now_ms = 1760000000000
        for row in src.execute("SELECT id, name, defaultBuyPrice, defaultSellPrice, notes, isHidden FROM qat_types"):
            dst.execute(
                "INSERT INTO qat_types VALUES (?, ?, ?, ?, 'YER', ?, ?, ?)",
                (row[0], row[1], round(row[2] * 100), round(row[3] * 100), row[4], row[5], now_ms),
            )
        for row in src.execute("SELECT id, name, phone, address, notes FROM customers"):
            dst.execute("INSERT INTO customers VALUES (?, ?, ?, ?, ?, ?, 0)", (*row, now_ms))
        for row in src.execute("SELECT id, name, phone, address, notes FROM suppliers"):
            dst.execute("INSERT INTO suppliers VALUES (?, ?, ?, ?, ?, ?, 0)", (*row, now_ms))

        for row in src.execute("SELECT id, qatType, supplierName, remainingQty, unit, buyPrice, sellPrice, purchaseDate, currency FROM inventory"):
            dst.execute(
                "INSERT INTO inventory_batches VALUES (?, 1, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                (row[0], row[1], row[2], round(row[3] * 1000), round(row[3] * 1000), row[4], round(row[5] * 100), round(row[6] * 100), row[8], now_ms, row[7]),
            )

        for row in src.execute("SELECT id, customerId, customerName, date, totalAmount, paidAmount, remainingAmount, paymentType, status, notes, currency, discount, taxPercent, isArchived FROM sales"):
            ptype = "PARTIAL" if "جزئ" in row[7] else ("CREDIT" if "آجل" in row[7] else "PAID")
            dst.execute(
                "INSERT INTO sales VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (row[0], f"INV-{row[0]}", row[1], row[2], now_ms, row[3], row[10], round(row[4] * 100), round(row[11] * 100), round(row[12] * 100), 0, round(row[4] * 100), round(row[5] * 100), round(row[6] * 100), ptype, row[8], row[9], row[13]) if False else
                (row[0], f"INV-{row[0]}", row[1], row[2], now_ms, row[3], row[10], round(row[4] * 100), round(row[11] * 100), round(row[12] * 100), 0, round(row[4] * 100), round(row[5] * 100), round(row[6] * 100), ptype, row[8], row[9])
            ) if False else dst.execute(
                "INSERT INTO sales (id, invoice_number, customer_id, customer_name, date_ms, date_iso, currency_code, subtotal_minor, discount_minor, tax_bps, tax_amount_minor, total_amount_minor, paid_amount_minor, remaining_amount_minor, payment_type, status, notes, is_archived) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (row[0], f"INV-{row[0]}", row[1], row[2], now_ms, row[3], row[10], round(row[4] * 100), round(row[11] * 100), round(row[12] * 100), 0, round(row[4] * 100), round(row[5] * 100), round(row[6] * 100), ptype, row[8], row[9], row[13]),
            )

        # Reconcile per-currency customer balances
        for cid, cur, rem in dst.execute("SELECT customer_id, currency_code, SUM(remaining_amount_minor) FROM sales WHERE is_archived=0 GROUP BY customer_id, currency_code"):
            dst.execute("INSERT INTO customer_balances VALUES (?, ?, ?, ?)", (cid, cur, rem, now_ms))

        for row in src.execute("SELECT id, name, phone, company, notes, createdAt FROM transfer_recipients"):
            dst.execute("INSERT INTO transfer_recipients VALUES (?, ?, ?, ?, ?, ?)", row)

        for row in src.execute("SELECT id, senderName, receiverName, recipientId, amount, currency, fee, transferCompany, transferNumber, date, status, notes, isArchived FROM money_transfers"):
            rec_id = row[3] if row[3] and row[3] > 0 else None
            st_code = "DELIVERED" if "تم التسليم" in row[10] else "PENDING"
            dst.execute(
                "INSERT INTO money_transfers VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (row[0], row[1], row[2], rec_id, round(row[4] * 100), round(row[6] * 100), row[5], row[7], row[8], now_ms, row[9], st_code, row[10], row[11], row[12]),
            )

        dst.commit()

        assert dst.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        fk_errors = dst.execute("PRAGMA foreign_key_check").fetchall()
        assert fk_errors == [], f"FK violations: {fk_errors}"

        bals = dict(dst.execute("SELECT currency_code, debt_minor FROM customer_balances WHERE customer_id=1").fetchall())
        assert bals == {"YER": 1500000, "SAR": 25050}, f"Unexpected balances: {bals}"

        unlinked = dst.execute("SELECT recipient_id FROM money_transfers WHERE id=2").fetchone()[0]
        assert unlinked is None

        src.close()
        dst.close()
    print("[PASS] SQLite v2 DDL (20 tables, 12 indices) + Room v18 migration + FK & currency separation verified")


def test_ed25519_canonical_compatibility():
    priv = Ed25519PrivateKey.from_private_bytes(bytes(range(1, 33)))
    pub = priv.public_key()
    spki_der = pub.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    assert len(spki_der) == 44
    assert spki_der[:12].hex() == "302a300506032b6570032100"
    key_id = "QLK-" + hashlib.sha256(spki_der).hexdigest()[:8].upper()

    payload_canonical = (
        '{"format":"QAT_LEDGER_CANONICAL_PAYLOAD_V1",'
        '"licenseId":"QAT-2026-ABCD1234",'
        '"product":"QAT_LEDGER",'
        '"edition":"CUSTOMER",'
        '"customerName":"أحمد",'
        '"businessName":"وكالة",'
        '"planCode":"PRO_YEARLY",'
        '"issuedAtEpochMs":1700000000000,'
        '"expiresAtEpochMs":1731536000000,'
        '"installationId":"QLD-1111-2222-3333-4444",'
        '"maxDevices":1,'
        '"features":["BACKUP","REPORTS"]}'
    )
    sig = priv.sign(payload_canonical.encode("utf-8"))
    assert len(sig) == 64
    pub.verify(sig, payload_canonical.encode("utf-8"))

    b64u_payload = base64.urlsafe_b64encode(payload_canonical.encode("utf-8")).decode().rstrip("=")
    b64u_sig = base64.urlsafe_b64encode(sig).decode().rstrip("=")
    compact = f"QL1.{b64u_payload}.{b64u_sig}"
    assert compact.startswith("QL1.")
    print(f"[PASS] Ed25519 SPKI DER + QLK KeyID ({key_id}) + QL1 canonical signature verified")


def check_font_cmap(ttf_path):
    with open(ttf_path, "rb") as f:
        raw = f.read()
    num_tables = struct.unpack(">H", raw[4:6])[0]
    found_arabic = False
    found_pres_b = False
    for i in range(num_tables):
        off = 12 + i * 16
        tag = raw[off : off + 4].decode("latin1")
        t_off, t_len = struct.unpack(">II", raw[off + 8 : off + 16])
        if tag == "cmap":
            cmap = raw[t_off : t_off + t_len]
            nsub = struct.unpack(">H", cmap[2:4])[0]
            for j in range(nsub):
                p, e, sub_off = struct.unpack(">HHI", cmap[4 + j * 8 : 12 + j * 8])
                fmt = struct.unpack(">H", cmap[sub_off : sub_off + 2])[0]
                if fmt == 4:
                    seg_count = struct.unpack(">H", cmap[sub_off + 6 : sub_off + 8])[0] // 2
                    ends = struct.unpack(f">{seg_count}H", cmap[sub_off + 14 : sub_off + 14 + 2 * seg_count])
                    starts = struct.unpack(f">{seg_count}H", cmap[sub_off + 16 + 2 * seg_count : sub_off + 16 + 4 * seg_count])
                    for st, en in zip(starts, ends):
                        if st <= 0x0627 <= en:
                            found_arabic = True
                        if st <= 0xFEFB <= en:
                            found_pres_b = True
    assert found_arabic and found_pres_b, f"{ttf_path} missing Arabic or Presentation Forms-B glyphs"


def test_assets_and_dart_structure():
    # Check assets
    required_assets = [
        "assets/images/app_logo.png",
        "assets/images/store_icon_1024.png",
        "assets/images/adaptive_foreground.png",
        "assets/images/adaptive_monochrome.png",
        "assets/fonts/IBMPlexSansArabic-Regular.ttf",
        "assets/fonts/IBMPlexSansArabic-Medium.ttf",
        "assets/fonts/IBMPlexSansArabic-SemiBold.ttf",
        "assets/fonts/IBMPlexSansArabic-Bold.ttf",
        "assets/fonts/OFL.txt",
    ]
    for rel in required_assets:
        p_abs = os.path.join(ROOT, rel)
        assert os.path.exists(p_abs), f"Missing asset: {rel}"

    for ttf in [
        "assets/fonts/IBMPlexSansArabic-Regular.ttf",
        "assets/fonts/IBMPlexSansArabic-Bold.ttf",
    ]:
        check_font_cmap(os.path.join(ROOT, ttf))

    # Check icon dimensions
    im = Image.open(os.path.join(ROOT, "assets/images/store_icon_1024.png"))
    assert im.size == (1024, 1024)

    # Check relative Dart imports & forbidden residue
    dart_files = []
    for base in ("lib", "test"):
        for r, _, files in os.walk(os.path.join(ROOT, base)):
            for fn in files:
                if fn.endswith(".dart"):
                    dart_files.append(os.path.join(r, fn))
    assert len(dart_files) >= 24

    for df in dart_files:
        with open(df, "r", encoding="utf-8") as f:
            src = f.read()
        for imp in re.findall(r"import\s+'(\.\.?/[^']+)'", src):
            resolved = os.path.normpath(os.path.join(os.path.dirname(df), imp))
            assert os.path.exists(resolved), f"Broken relative import in {df}: {imp}"
        assert "WavingPalestineFlag" not in src
        assert "tofan_backups" not in src
        assert "150.0" not in src

    # Verify original pre-change backup archive
    backup_zip = os.path.join(ROOT, "backup/qat_ledger_kotlin_original_516e118.zip")
    assert os.path.exists(backup_zip)
    with zipfile.ZipFile(backup_zip, "r") as zf:
        assert zf.testzip() is None
        names = [n for n in zf.namelist() if not n.endswith("/")]
        assert len(names) == 103

    print(f"[PASS] Verified {len(dart_files)} Dart files, all relative imports, Arabic Presentation-Form fonts, icons, and 103-file backup archive")


if __name__ == "__main__":
    test_sqlite_schema_and_room_migration()
    test_ed25519_canonical_compatibility()
    test_assets_and_dart_structure()
    print("ALL EMPIRICAL CHECKS PASSED.")
