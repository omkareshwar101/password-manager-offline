# Security Policy

OfflineVault stores secrets on the device. Please do not include vault
contents, master PINs, security answers, backup PINs, or personal account data
in any issue, discussion, or pull request.

## Reporting a vulnerability

Please report security issues **privately** using GitHub's private
vulnerability reporting:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Include the app version, Android version/device, reproduction steps, and
   the impact you believe the issue has.

Please do not open a public issue for a security bug until a fix or mitigation
has been released. There is no bug bounty program.

## Scope

In scope:

- Vault encryption, key handling, PIN/security-answer verification
- Lockout and auto-lock behavior
- Backup encryption and restore validation
- Data leakage through logs, backups, intents, clipboard, or app-private files
- Permission or manifest regressions

Out of scope:

- Rooted or physically compromised devices with an attacker who can read app
  memory or modify the installed APK
- Shoulder surfing, cameras pointed at the screen, or screenshots taken with an
  external device
- Loss of the master PIN or security answers without an encrypted backup
- Weak user-chosen PINs or backup PINs
- Vulnerabilities in third-party dependencies that are already publicly known
  and do not affect OfflineVault's threat model

## Security model and limitations

See the "Security model" and "What OfflineVault does not protect against"
sections of the README. OfflineVault has not undergone an independent security
audit and is provided without warranty.
