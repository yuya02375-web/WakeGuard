# WakeGuard stable signing setup

WakeGuard must use one permanent signing key for every installable update. Android rejects an update when the package name matches but the signing certificate differs.

## One-time GitHub Actions secrets

Add these repository secrets under **Settings → Secrets and variables → Actions**:

- `WAKEGUARD_KEYSTORE_B64`
- `WAKEGUARD_KEYSTORE_PASSWORD`
- `WAKEGUARD_KEY_ALIAS`
- `WAKEGUARD_KEY_PASSWORD`

The private signing key itself must never be committed to this public repository.

After the secrets are configured, run the **Build WakeGuard Stable APK** workflow. It builds the APK and re-signs it with the same permanent key on every release.

## Permanent certificate

Expected release certificate SHA-256 fingerprint:

`DB:95:A0:0E:32:A6:B4:C3:F5:4D:43:B9:1C:F6:71:55:DC:A7:05:7C:2C:0E:E1:45:F5:FB:98:17:8F:31:1C:2E`

Do not replace or regenerate this key. Losing it means future in-place updates cannot be signed for existing installs.
