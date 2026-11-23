# New Production Release Key — Owner-Only Setup

**Timing:** Follow these steps only after the QA workflow passes. Latest verified QA-only commit: `4048e059ce97ac004626103c4ace49e7f39e4ceb` (push run `45098384810`; pull-request run `45098390733`). The latest run reported the five signing inputs `MISSING` at build time and emitted no secret values. A direct secret-list request was denied (HTTP 403), so current Environment-secret state cannot be independently verified and may have changed. No key was generated here. Historical Ed25519 public trust keys were not available to the build (`public_key_id=NONE`); production remains blocked until the device, licensing, restore, and owner-approval gates are complete. The workflow intentionally skipped production signing and publication.

**Scope:** This new RSA/JKS signing identity is only for the new Customer (`com.aistudio.tofanqat.qatledger.customer`) and Developer (`com.aistudio.tofanqat.qatledger.developer`) packages and their future updates. It cannot update, replace, or sign the legacy `com.aistudio.tofanqat.nxzwmb` app. Never use its certificate to overwrite or sign the legacy package.

## 1. Generate once on the owner's trusted computer

Do not generate or store this private key in Arena, the repository, CI, or a GitHub artifact. Use a trusted owner-controlled computer with encrypted storage, keep shell tracing disabled, and create a protected directory:

```bash
umask 077
install -d -m 700 "$HOME/qat-ledger-signing"
KEYSTORE="$HOME/qat-ledger-signing/qat-ledger-production.jks"

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -storetype JKS \
  -alias qat-ledger-release \
  -keyalg RSA \
  -keysize 3072 \
  -sigalg SHA256withRSA \
  -validity 10000 \
  -dname "CN=QAT Ledger, OU=Software Development, O=QAT Ledger, L=Taiz, ST=TTT, C=YE"
```

`keytool` prompts interactively for the store and key passwords. Use strong, unique values kept in the owner's password manager. **Do not** put passwords on the command line, print the private key, paste passwords or key material into chat, or enable `set -x`. Use the fixed alias and certificate subject above so the current CI policy can validate the new identity.

Inspect the public certificate metadata locally:

```bash
keytool -list -v -storetype JKS \
  -keystore "$KEYSTORE" \
  -alias qat-ledger-release
```

Confirm `Entry type: PrivateKeyEntry`, alias `qat-ledger-release`, the exact subject, RSA-3072, `SHA256withRSA`, and a non-expired certificate. Record the certificate **SHA-256 fingerprint** in the owner's secure records. The fingerprint is public certificate metadata, not a private key; nevertheless, do not post it publicly before the owner's final release approval.

## 2. Verify and back up the signing identity

1. Perform a local signing/verification operation on the trusted computer to confirm the key password works. Do not share terminal output containing credentials or private material.
2. Make at least two independent, encrypted, owner-controlled backups of the `.jks` file. Keep them outside the repository, Arena workspace, GitHub, Actions artifacts, and public/cloud links. Document where the backups are and test that a backup can be read without exposing it.
3. Preserve the file name, alias, passwords, and the certificate fingerprint in the owner's password manager/secure records. Do not create a replacement key merely to recover from a missing copy.

## 3. Configure the protected GitHub Environment (owner action)

In **Repository Settings → Environments → `production` → Environment secrets**, add these values. Do not put them in chat or repository-level secrets:

- `KEYSTORE_B64` — one-line Base64 encoding of the new `.jks` file.
- `KEYSTORE_PASSWORD` — the JKS store password.
- `KEY_ALIAS` — exactly `qat-ledger-release`.
- `KEY_PASSWORD` — the private-key entry password.
- `RELEASE_KEYSTORE_SHA256` — the new certificate SHA-256 fingerprint, 64 hex characters (colons are accepted by the workflow).
- `QAT_LICENSE_PUBLIC_KEYS` — **only owner-recovered historical Ed25519 public key(s)**. Do not invent a key or put any private seed/key here. No historical public key has been verified in this workspace, so this secret must remain unset until legitimate recovery and test-license verification are complete.

To make the Base64 value without echoing it to the terminal, create a mode-600 temporary file on the trusted computer, on encrypted storage:

```bash
umask 077
base64 -w 0 "$KEYSTORE" > "$HOME/qat-ledger-signing/qat-ledger-production.jks.b64"
chmod 600 "$HOME/qat-ledger-signing/qat-ledger-production.jks.b64"
```

Use the protected local file to enter the value directly into the `production` Environment secret form. The Base64 file is equivalent to the private key: keep it private and remove the temporary copy when the secret is confirmed. Never upload it, include it in an issue, commit it, or add it to Actions artifacts.

Keep the `production` Environment restricted to `main`, with administrator bypass disabled. The workflow fails closed if required secrets, the alias, certificate subject, RSA size, signature algorithm, or the actual keystore certificate fingerprint do not match. `RELEASE_KEYSTORE_SHA256` becomes the owner-approved fingerprint pin for this new key; do not use the old signing fingerprint. GitHub secret presence cannot be independently verified from this Arena session.

## 4. Required checks before a production dispatch

- Complete physical-device evidence for the exact source commit: install the new Customer beside the legacy Customer without altering legacy data; import and verify a synthetic `.qatbackup` in the new Customer; compare all tables/records, financial totals, settings and logo; run `integrity_check` and `foreign_key_check`; separately test license activation/re-activation and persistence.
- Recover and verify historical Ed25519 public keys and a test license. If this cannot be proven, **stop; do not create a replacement key to bypass the gate**.
- Run the production signing workflow only from the protected `main` tip after the owner's final explicit approval. CI must verify the new JKS with `keytool`, and `apksigner` must verify both APKs and confirm they use the same owner-approved fingerprint. Confirm their package IDs, versions, checksums, labels, and QA/production signing status.
- Preserve the new signing identity for future updates to these new package IDs. Keep it entirely separate from the legacy app and its signing certificate.

No Release, public APK link, or production artifact is authorized by this setup guide. The owner must review the complete test results and give separate final production approval first.
