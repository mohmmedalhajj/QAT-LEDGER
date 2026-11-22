# New Customer & Developer App Validation Status

**Status: QA workflow passed; NOT RELEASE-READY.** QA-only APKs were built and exercised by GitHub Actions. No production APK or public release has been created.

## Owner-approved permanent Android identities

| Edition | `applicationId` | App label | Version |
|---|---|---|---|
| Customer | `com.aistudio.tofanqat.qatledger.customer` | `QAT-LEDGER Customer` | `2.1.0+201` (`versionCode=201`) |
| Developer | `com.aistudio.tofanqat.qatledger.developer` | `QAT-LEDGER Developer` | `2.1.0-dev` (`versionCode=201`) |

The owner approved both IDs. Their availability in the intended distribution channels remains to be checked. They are distinct from each other and legacy Customer `com.aistudio.tofanqat.nxzwmb` (`2.0.0`, code `200`). The new Customer is a **separate install, not an update**. Do not uninstall, overwrite, or clear the legacy app or its data.

## QA workflow result

- **Passing run:** [GitHub Actions run 45070939692](https://github.com/mohmmedalhajj/QAT-LEDGER/actions/runs/45070939692), commit `9754be46068ec92155e8aa5645a98e032f283116`, triggered by push on the current branch. The corresponding pull-request run also passed.
- `flutter analyze` and the full `flutter test` suite **passed** in CI. The added synthetic `.qatbackup` cross-install test ran within this suite and compares every SQLite table/row, settings, logo bytes, `integrity_check`, and `foreign_key_check`.
- Customer and Developer QA builds **passed**. CI’s APK manifest/binary audit passed for package IDs, labels, version names/codes, edition isolation, and signatures. The Android Emulator smoke test passed for installing and launching both new apps side-by-side with distinct app data directories.
- Both the production package-preparation and publication paths were **skipped**. Artifacts use `QAT_LEDGER_CUSTOMER_QA_DEBUG_ONLY.apk` and `QAT_LEDGER_DEVELOPER_QA_DEBUG_ONLY.apk`; the workflow artifact is named `qat-ledger-QA-DEBUG-ONLY-build-and-audit`. These are QA/debug-signed only and must not be given to customers as production builds or used to update the legacy app.
- No historical Ed25519 public keys were configured or verified. The workflow generated no replacement key; it marks license readiness **BLOCKED**. The QA run does not establish production licensing compatibility.
- An earlier QA run at commit `1389541d256728c8193233d645300883ad1f7d26` failed analysis on one redundant test import; that import was removed, and the follow-up run above passed.

## Remaining BLOCKED release gates

| Gate | Status | Required next evidence/action |
|---|---|---|
| Physical coexistence with the original Customer installation | **BLOCKED** | On a separate owner-controlled Android device, install Customer alongside legacy `com.aistudio.tofanqat.nxzwmb`, launch both, and verify the old app's data remains unchanged. CI only installed the two new apps together. |
| `.qatbackup` import inside the new Customer on a separate device | **BLOCKED** | Test with synthetic data only. Compare all tables/records, financial totals, settings and logo; run SQLite `integrity_check` and `foreign_key_check`; preserve the input file unchanged. Never upload a real backup to GitHub/Actions or a public link. |
| Historical-license activation, re-activation and persistence | **BLOCKED** | Recover and verify historical Ed25519 public key(s) and test licenses. Do not invent replacement keys or include private seeds. |
| New production signing identity | **BLOCKED** | After QA, the owner must generate a new RSA-3072 JKS key on a trusted computer, retain two encrypted owner-controlled backups, verify its fingerprint, and configure the protected `production` Environment secrets. No key was created in Arena. See [`RELEASE_KEY_SETUP.md`](RELEASE_KEY_SETUP.md). |
| Production `apksigner` verification | **BLOCKED** | No production APK exists. The CI `apksigner` result above is for QA/debug APKs only. Production must confirm both new APKs use the owner-approved new certificate. |
| Final production authorization/distribution | **BLOCKED** | Requires the physical-device and license evidence, new-key and Environment verification, exact release commit/fingerprints/results, and the owner's separate final approval. No Release was published. |

## Repository state

Current work remains on `arena/e82ef661-qat-ledger`; PR #1 is open. No merge or change to `main` occurred. QA run `45070939692` tested app source at commit `9754be46068ec92155e8aa5645a98e032f283116`. Subsequent edits to production-key policy and its owner setup guide require their own QA rerun before those workflow edits are treated as validated. Safety snapshots remain under `.workspace-safety-backup/` and are not part of the Release.
