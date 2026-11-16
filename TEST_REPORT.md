# TEST REPORT — QAT LEDGER (`ميزان القات`) Flutter Edition

**Environment:** Debian GNU/Linux 12 (`x86_64`), Python 3.11.2 (`sqlite3 3.40.1`, `cryptography 50.0.2`, `Pillow 13.0.0`), `zip`/`unzip`.

---

## 1. Empirical Tests Executed & Verified in Sandbox

| Test Suite / Verification | Command Executed | Result | Evidence / Details |
|---|---|---|---|
| **Pre-Change Kotlin Backup Integrity** | `git archive --format=zip ... && sha256sum && unzip -t` | **PASS** | `backup/qat_ledger_kotlin_original_516e118.zip` (787 KB, 103/103 tracked files, SHA-256: `ec5b2c7435467dcd2780ea4b427de041d8960e3a6eb3d93e2e64475d3b36c55c`, `unzip -t` zero errors). |
| **SQLite v2 DDL & Room v18 Migration Reconciliation** | `python3 scripts/run_empirical_checks.py` | **PASS** | Extracted all **20 `CREATE TABLE` statements** and **12 `CREATE INDEX` statements** directly from `lib/data/schema.dart`; verified against real Room v18 SQLite DB (`tofan_al_aqsa_qat_db`): `PRAGMA integrity_check == 'ok'`, `PRAGMA foreign_key_check == []`, `"دفعة جزئية"` normalized to `PARTIAL`, YER (`1500000`) & SAR (`25050`) separated per `(customer_id, currency_code)`, unlinked transfer (`recipientId=0`) migrated as `NULL` without fabricating recipients. |
| **Ed25519 Canonical Payload & KeyID Compatibility** | `python3 scripts/run_empirical_checks.py` | **PASS** | Verified X.509 SPKI DER prefix (`302a300506032b6570032100`, 44 bytes), deterministic key ID (`QLK-646D6BE4`), `QAT_LEDGER_CANONICAL_PAYLOAD_V1` sorted features JSON, 64-byte Ed25519 signing/verification, and `QL1.` compact encoding. |
| **Arabic Font Glyph Coverage (`IBMPlexSansArabic`)** | `python3 scripts/run_empirical_checks.py` | **PASS** | Replaced legacy Latin-only `cairo.ttf` (which lacked Arabic glyphs in its `cmap` table) with `IBMPlexSansArabic-{Regular,Medium,SemiBold,Bold}.ttf`; binary `cmap` parser verified both `U+0600..U+06FF` and Presentation Forms-B (`U+FE70..U+FEFC` including `U+FEFB` Lam-Alef) for RTL PDF shaping. |
| **Dart Lexer, Relative Imports & Forbidden Residue Audit** | `python3 scripts/run_empirical_checks.py` + interpolation-aware Python Dart lexer | **PASS** | Verified all **41 `.dart` files** (`lib/` + `test/`): zero broken relative imports, balanced delimiters (`()`, `{}`, `[]`, string `${...}` interpolation), zero `.firstOrNull` missing-extension hazards, zero hardcoded `150.0` currency conversion fallback, zero `WavingPalestineFlag` / `tofan_backups`. |
| **Brand & Adaptive Icon Asset Suite** | `python3 scripts/generate_brand_icons.py` + visual inspection | **PASS** | Generated `store_icon_1024.png` (1024x1024), `app_logo.png` (512x512), `adaptive_foreground.png` (432x432), `adaptive_monochrome.png` (432x432), and `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher*.png` + `mipmap-anydpi-v26` XMLs. |

---

## 2. Checks NOT Executed in This Sandbox (And Exact Reasons)

The following Flutter & Android build/test commands **could not be executed** in this sandbox and therefore **their outputs are not claimed**:

| Command | Status | Root Cause in Sandbox |
|---|---|---|
| `flutter pub get` | **NOT RUN** | `flutter` & `dart` SDK binaries are not installed in the sandbox, and outbound network policy blocks `pub.dev` and `storage.googleapis.com`. |
| `flutter analyze` | **NOT RUN** | Requires Dart/Flutter SDK (`flutter` analyzer). |
| `flutter test` (`test/*.dart`) | **NOT RUN** | Requires Flutter test runner + Dart VM (`test/money_and_invoice_test.dart`, `test/room_migration_test.dart`, `test/repository_accounting_test.dart`, `test/licensing_ed25519_test.dart`, `test/arabic_pdf_shaper_test.dart`, `test/widget_shell_test.dart` are authored and ready to execute once Flutter is available). |
| `flutter build apk` (Customer & Developer APKs) | **NOT RUN** | Requires Flutter SDK, OpenJDK 17, Android SDK/Build-Tools 35, and Gradle Maven repositories (`dl.google.com` / `repo.maven.apache.org` blocked by sandbox egress rules). Consequently, **no compiled `.apk` binary was generated inside this sandbox**. |

---

## 3. Recommended Verification Sequence on Target Machine / GitHub Actions

A ready-to-run workflow is included at `.github/workflows/flutter_build.yml`. On any machine or CI runner with Flutter stable (`>= 3.29`), JDK 17, and Android SDK 35:

```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --release --flavor customer --dart-define=APP_EDITION=customer --dart-define=QAT_LICENSE_PUBLIC_KEYS="<SPKI_BASE64>"
flutter build apk --debug --flavor developer --dart-define=APP_EDITION=developer
```
