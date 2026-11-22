# New Customer & Developer App Validation Status

**Status: NOT RELEASE-READY — no production APK has been built or distributed. The requested GitHub Actions run will build QA-only APKs if its gates pass; those artifacts are not production releases.**

## Owner-approved permanent Android identities

| Edition | `applicationId` | App label | Version |
|---|---|---|---|
| Customer | `com.aistudio.tofanqat.qatledger.customer` | `QAT-LEDGER Customer` | `2.1.0+201` (`versionCode=201`) |
| Developer | `com.aistudio.tofanqat.qatledger.developer` | `QAT-LEDGER Developer` | `2.1.0-dev` (`versionCode=201`) |

These proposed IDs are distinct from each other and legacy Customer `com.aistudio.tofanqat.nxzwmb` (`2.0.0`, code `200`). The new Customer is a **separate install, not an update**. Do not uninstall, overwrite, or clear the legacy app or its data. Their availability still needs to be checked in the intended distribution channels before distribution.

## Checks completed in this workspace

- GitHub Actions YAML syntax parsed successfully for `.github/workflows/flutter_build.yml` and its documentation mirror.
- `bash -n` passed for all 19 multiline shell steps in each workflow file.
- Static assertions passed for both application IDs, version `2.1.0+201`, package-label checks, `${applicationId}.fileprovider`, and the legacy non-upgrade gate.
- A synthetic `.qatbackup` cross-install test was added to `test/repository_accounting_test.dart`. It compares every SQLite table and row, settings, logo bytes, integrity checks, and foreign-key checks. **It has not run locally; it is included in the requested Actions test run.**
- The workflow no longer generates an ephemeral Ed25519 trust key. With no historical public keys configured, a QA build is explicitly blocked for license readiness; production requires owner-recovered historical public keys only.
- `git diff --check` passed. The original Kotlin source-archive checksum and the pre-change safety snapshot checksums passed.
- No APK/AAB, keystore, or `android/key.properties` file was created or found.

## BLOCKED locally — GitHub Actions QA run pending

| Gate | Status | Reason / next evidence required |
|---|---|---|
| `flutter analyze`, `flutter test` | **BLOCKED** | Flutter/Dart SDKs are not installed. |
| Android Gradle Customer/Developer build | **BLOCKED** | Java, Gradle, and Android SDK build tools are not installed. |
| APK Manifest/application IDs, version, signature and certificate audit | **BLOCKED** | No APK was built; `aapt2`, `apksigner`, and `keytool` are unavailable. |
| Emulator installation, launch and data-directory separation | **BLOCKED** | `adb` and Android emulator are unavailable. |
| Install beside the original app without changing its data | **BLOCKED** | Requires an Android device with the original app and a new QA APK. |
| Import `.qatbackup` inside new Customer on a separate device | **BLOCKED** | The synthetic automated test is unrun; the required separate-device test has not happened. Use synthetic data only; verify all tables/records, financial totals, settings, logo, `integrity_check`, and `foreign_key_check`; keep the original backup unchanged and never upload it. |
| License activation/re-activation and two-device behavior | **BLOCKED** | No historical Ed25519 public keys or test license have been recovered and verified. Do not invent a replacement key. |
| Production signing and secret verification | **BLOCKED** | No production keystore is available here; GitHub secret names could not be inspected (HTTP 403). No key was generated. The production Environment must be owner-verified and properly provisioned. |
| Final release approval / distribution | **BLOCKED** | No final commit, owner approval of the IDs, passing device evidence, verified signing, or release authorization exists. No release was published. |

## Repository state

Work remains on `arena/e82ef661-qat-ledger`; PR #1 stays open and `main` is not changed or merged. The pre-existing broad worktree changes and safety snapshots under `.workspace-safety-backup/` are preserved. This report describes the checked-out source and does not imply that a production release was built or approved.
