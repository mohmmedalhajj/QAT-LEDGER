# New Customer & Developer App Validation Status

**Status: QA workflow passed; NOT RELEASE-READY.** QA-only APKs were built and exercised by GitHub Actions. No production APK or public release has been created.

## Owner-approved permanent Android identities

| Edition | `applicationId` | App label | Version |
|---|---|---|---|
| Customer | `com.aistudio.tofanqat.qatledger.customer` | `QAT-LEDGER Customer` | `2.1.0+201` (`versionCode=201`) |
| Developer | `com.aistudio.tofanqat.qatledger.developer` | `QAT-LEDGER Developer` | `2.1.0-dev` (`versionCode=201`) |

The owner approved both IDs. Their availability in the intended distribution channels remains to be checked. They are distinct from each other and legacy Customer `com.aistudio.tofanqat.nxzwmb` (`2.0.0`, code `200`). The new Customer is a **separate install, not an update**. Do not uninstall, overwrite, or clear the legacy app or its data.

## QA workflow result

- **Latest full QA on reviewed source:** [GitHub Actions push run 45079727583](https://github.com/mohmmedalhajj/QAT-LEDGER/actions/runs/45079727583) and [PR run 45079734485](https://github.com/mohmmedalhajj/QAT-LEDGER/actions/runs/45079734485), commit `5d101d8e2d5a5da2aa9de7ca9de6e4dd454b20a1`; analysis, tests, Customer/Developer QA builds, APK audit, emulator test passed, and Production publish was skipped. Earlier passing runs include [45072055632](https://github.com/mohmmedalhajj/QAT-LEDGER/actions/runs/45072055632) on `0fcecf3041758d178f6786670b21861ae802ea1c` and [45070939692](https://github.com/mohmmedalhajj/QAT-LEDGER/actions/runs/45070939692) on `9754be46068ec92155e8aa5645a98e032f283116`.
- `flutter analyze` and the full `flutter test` suite **passed** in CI. The added synthetic `.qatbackup` cross-install test ran within this suite and compares every SQLite table/row, settings, logo bytes, `integrity_check`, and `foreign_key_check`.
- Customer and Developer QA builds **passed**. CI’s APK manifest/binary audit passed for package IDs, labels, version names/codes, edition isolation, and signatures. The Android Emulator smoke test passed for installing and launching both new apps side-by-side with distinct app data directories.
- Both the production package-preparation and publication paths were **skipped**. Artifacts use `QAT_LEDGER_CUSTOMER_QA_DEBUG_ONLY.apk` and `QAT_LEDGER_DEVELOPER_QA_DEBUG_ONLY.apk`; the workflow artifact is named `qat-ledger-QA-DEBUG-ONLY-build-and-audit`. The latest run's successful `apksigner` evidence is in `customer-signature.txt` and `developer-signature.txt` within its artifact; certificate fingerprints are build-specific and should be read from that run, not copied from an earlier QA APK. These are QA/debug signatures only—not production identities—and must not be given to customers as production builds or used to update the legacy app.
- No historical Ed25519 public keys were configured or verified. The workflow generated no replacement key; it marks license readiness **BLOCKED**. The QA run does not establish production licensing compatibility.
- An earlier QA run at commit `1389541d256728c8193233d645300883ad1f7d26` failed analysis on one redundant test import; that import was removed, and the follow-up run above passed.

## Owner-reported physical QA and remaining BLOCKED release gates

The owner reports **Customer QA PASS** on their phone: Customer QA was installed beside the legacy Customer and a `.qatbackup` was restored with data appearing as expected. This is explicitly **owner testimony**, not a device test performed or independently inspected by Arena. The owner did not provide a separate checklist result for every table/financial total, settings, logo, SQLite integrity or foreign-key check. Do not upload the backup; preserve the old app and original backup.

Developer physical QA has **NOT BEEN RUN** as of this report. It must use synthetic data only.

| Gate | Status | Required next evidence/action |
|---|---|---|
| Customer coexistence beside legacy app | **PASS — OWNER-REPORTED** | Owner reports both installed beside each other. Arena did not inspect legacy data on-device; retain the old app and its data unchanged. |
| `.qatbackup` import inside the new Customer on a separate device | **PASS — OWNER-REPORTED, LIMITED EVIDENCE** | Owner reports successful restore and expected data. Before Production, complete the synthetic-backup checklist: all tables/records, financial totals, settings and logo; SQLite `integrity_check` and `foreign_key_check`; preserve the input unchanged. Never upload a real backup to GitHub/Actions or a public link. |
| Developer QA physical install and feature test | **BLOCKED — NOT RUN** | Test the Developer QA APK on an owner-controlled test phone with synthetic data only; verify Developer-only features and no replacement of either Customer app. |
| Historical-license activation, re-activation and persistence | **BLOCKED** | Recover and verify historical Ed25519 public key(s) and test licenses; exercise the new Customer on device. Do not invent replacement keys or include private seeds. |
| New production signing identity | **BLOCKED** | After QA, the owner must generate a new RSA-3072 JKS key on a trusted computer, retain two encrypted owner-controlled backups, verify its fingerprint, and configure the protected `production` Environment secrets. No key was created in Arena. See [`RELEASE_KEY_SETUP.md`](RELEASE_KEY_SETUP.md). |
| Production `apksigner` verification | **BLOCKED** | No production APK exists. The CI `apksigner` result above is for QA/debug APKs only. Production must confirm both new APKs use the owner-approved new certificate. |
| Final production authorization/distribution | **BLOCKED** | Requires the physical-device and license evidence, new-key and Environment verification, exact release commit/fingerprints/results, and the owner's separate final approval. No Release was published. |

## Repository state

Work remains on `arena/e82ef661-qat-ledger`; PR #1 is open and `main` is unchanged. Latest reviewed source commit `5d101d8e2d5a5da2aa9de7ca9de6e4dd454b20a1` passed QA on push run `45079727583` and PR run `45079734485`. The stale task-switcher color was aligned to the existing brand green and a static regression assertion was added. Production-signing and publication branches were intentionally skipped; historical license trust and owner-controlled production signing remain blocked. Safety snapshots remain under `.workspace-safety-backup/` and are not part of the Release.
