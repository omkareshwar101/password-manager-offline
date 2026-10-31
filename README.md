# OfflineVault

OfflineVault is a fully offline Android password manager built with Kotlin and Jetpack Compose. It stores logins, notes, and optional account images in an encrypted local vault, with no network permission, no cloud sync, and no analytics.

> **Your vault cannot be recovered without your master PIN or your security answers.** There is no server, reset link, or support back door. Encrypted backups are the only way to move a vault to another device, and the backup PIN is also unrecoverable.

> **Security notice:** OfflineVault is an independent, local-first project and has not undergone a professional security audit. Review the security model before trusting it with high-value secrets, and keep encrypted backups.

## Install

### Download

Check this repository's **Releases** page for the latest signed APK. If no release has been published yet, build from source using the instructions below.

- Minimum Android version: **8.0 (API 26)**
- Android may ask you to allow installing apps from your browser or file manager.
- Only install OfflineVault from this repository or a build you made yourself. Avoid unofficial mirrors.

### Build from source

See [Build](#build). Debug builds are intended for development and testing; use a signed release build for daily use.

## Features

- **Three unlock methods**
  - Device biometrics (fingerprint / face), when enabled and enrolled
  - Master PIN (6–12 digits)
  - Any 2 of 3 security answers, normalized for case and whitespace
- **Brute-force resistance**
  - 5 wrong PIN entries force the security-questions path
  - 10 total failed attempts trigger a 5-minute lockout
  - PINs and answers are stored only as salted PBKDF2-HMAC-SHA256 hashes (210,000 iterations)
- **Vault**
  - Search with case-insensitive substring and fuzzy subsequence matching
  - Category chips: Social, Banking, Work, Shopping, Other
  - Favorites, per-account details, copy username/URL/notes/password
  - Warnings for weak, old, reused, or duplicate passwords
  - Biometric-gated password reveal with a 15-second auto-hide timer
  - 61 built-in emoji icons plus gallery/camera images
- **Editing**
  - Add and edit accounts, categories, notes, URLs, usernames, passwords
  - Local password generator (8–32 characters, character-group and ambiguous-character controls)
  - Camera capture through the external camera app with no `CAMERA` permission
- **Security**
  - `FLAG_SECURE` blocks screenshots, screen recording, and recent-app thumbnails
  - Clipboard auto-clears after a configurable delay (default 30 seconds)
  - Auto-lock on background, inactivity, and screen-off
  - Encrypted `.vault` export/import via the system file picker (SAF)
  - Destructive "delete all data" flow with PIN and confirmation
- **UX**
  - Material 3, indigo/teal palette, light/dark/system themes
  - Edge-to-edge layout, rounded cards, accessible content descriptions
  - English and Hindi localization
  - Single activity with splash screen and onboarding/auth/vault root states

## Security model

OfflineVault applies several independent layers:

| Layer | Implementation |
| --- | --- |
| Whole database | SQLCipher 4 (`net.zetetic:android-database-sqlcipher:4.5.4`) with a random 256-bit passphrase kept in EncryptedSharedPreferences |
| Sensitive fields | AES-256-GCM per field (name, URL, username, password, notes, category) through a non-exportable Android Keystore key |
| Auth metadata | EncryptedSharedPreferences (AES-256-SIV keys, AES-256-GCM values) |
| PIN / answers | PBKDF2-HMAC-SHA256, 210,000 iterations, random 16-byte salt per secret, constant-time comparison |
| Backups | PBKDF2-HMAC-SHA256 (210,000 iterations) + AES-256-GCM with a random salt and IV; authenticated with AAD `OfflineVaultBackup:v1` |
| Screen privacy | `WindowManager.LayoutParams.FLAG_SECURE` for the entire app |
| Session | Unlocked state exists only in memory; process death always returns to the lock screen |

Every field-encryption blob is `[12-byte IV][ciphertext + 128-bit tag]`. The Keystore key is generated with `setUserAuthenticationRequired(false)` so background field decryption works while the app is unlocked; access is guarded by the app's own lock screen. The key never leaves Android Keystore on devices with a hardware-backed implementation.

### Images and metadata

Account images are decoded, bounded to 768 px, re-encoded (which strips EXIF/GPS metadata), and written under `filesDir/account_images/` with random UUID filenames. Draft images live in the app cache and are committed only when the account is saved. Image files are protected by Android app sandboxing rather than a separate AES layer; image bytes are included inside encrypted backups. The database stores only the random filename or a built-in icon reference.

### Backup format

Exports use the `.vault` extension and contain:

1. Magic bytes `OVLT`, version, PBKDF2 salt, and GCM IV
2. AES-256-GCM ciphertext of a JSON document containing accounts and Base64-encoded images
3. A 128-bit GCM authentication tag

The backup PIN is independent of the master PIN and is never stored. Import authenticates and decrypts the file first, validates and bounds entries, writes imported images, then replaces the current vault in a transaction. A failed import rolls back newly written images.

### Anti-forensics and deletion behavior

- Export/import uses content URIs from the Storage Access Framework. No broad storage permission is requested.
- Import never leaves decrypted data on disk; plaintext exists only in memory during the operation.
- "Delete all data" vacuums/erases the database, removes images and pending drafts, and clears encrypted preferences while retaining only the random database passphrase so onboarding can create a fresh vault.

### What OfflineVault does not protect against

- A rooted, compromised, or physically unlocked device with a malicious app running as the same user
- Screenshots taken with an external camera pointed at the screen
- Shoulder surfing while a password is revealed
- Loss of the master PIN/security answers without a backup
- Backup PIN brute force if the user chooses a weak PIN (backups are intentionally portable, so they cannot use Keystore)

## Permissions

The merged manifest requests only:

- `android.permission.USE_BIOMETRIC`
- `android.permission.USE_FINGERPRINT` (legacy devices)
- `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — a signature-level permission generated by AndroidX for the app's own non-exported screen-off receiver; it grants no network or storage access

The manifest explicitly removes `INTERNET`, network-state, Bluetooth, NFC, camera, and storage permissions so transitive libraries cannot add them. `allowBackup` is `false`, `usesCleartextTraffic` is `false`, and data-extraction rules exclude all app data.

Verify after building:

```powershell
Select-String -Path app\build\intermediates\merged_manifests\debug\processDebugManifest\AndroidManifest.xml -Pattern 'uses-permission'
```

## Project structure

```text
OfflineVault/
├── app/src/main/java/com/offlinevault/app/
│   ├── MainActivity.kt                 # Splash, FLAG_SECURE, theme, onboarding/auth root
│   ├── OfflineVaultApplication.kt      # Process lifecycle, screen-off receiver, Coil config
│   ├── data/                           # Repositories, image store, encrypted backups
│   │   ├── local/                      # Room entities/DAO and SQLCipher database
│   │   └── model/                      # Accounts, categories, settings, drafts
│   ├── di/AppContainer.kt              # Manual dependency container
│   ├── security/                       # Keystore crypto, prefs, session, clipboard, biometrics
│   ├── ui/                             # Compose theme, components, screens, navigation
│   ├── util/                           # Generator, strength estimator, validators, search
│   └── viewmodel/                      # State holders for each screen
├── app/src/main/res/                   # English + Hindi strings, icons, XML rules
├── app/src/test/                       # JVM unit tests for pure utilities
├── gradle/                             # Gradle wrapper
└── README.md
```

Architecture: single-activity Compose UI, manual dependency injection through `AppContainer`, `StateFlow`-based view models, Room + SQLCipher for persistence, and a `SessionManager` that observes process lifecycle and user interaction for auto-lock.

## Build

Requirements:

- JDK 17
- Android SDK Platform 34 and Build Tools 34.0.0
- Windows, macOS, or Linux (the wrapper is included; Gradle 8.7)

Create `local.properties` with your SDK path (or set `ANDROID_HOME`):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Then build:

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"   # or your JDK 17 path
.\gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release build and signing

`.\gradlew.bat :app:assembleRelease` writes an **unsigned** APK to `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign it with your own private key before distribution:

```powershell
keytool -genkeypair -v -keystore offlinevault-release.jks -alias offlinevault `
  -keyalg RSA -keysize 4096 -validity 10000

& "$env:ANDROID_HOME\build-tools\34.0.0\zipalign.exe" -p -f 4 `
  app\build\outputs\apk\release\app-release-unsigned.apk `
  app\build\outputs\apk\release\app-release-aligned.apk

& "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" sign `
  --ks offlinevault-release.jks --ks-key-alias offlinevault `
  --out app\build\outputs\apk\release\OfflineVault-release.apk `
  app\build\outputs\apk\release\app-release-aligned.apk
```

Never commit the keystore or its passwords; `.gitignore` already excludes `*.jks`, `*.keystore`, and `keystore.properties`. Back up the keystore securely, because losing it prevents shipping updates under the same app identity.

Run unit tests and lint:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

All dependencies must be available in the local Gradle cache or a reachable Maven repository for the first build. The app itself has no runtime network access.

## First run

1. Read the privacy/security introduction and continue.
2. Create a 6–12 digit master PIN.
3. Choose three different security questions and answers.
4. Optionally enable biometric unlock.
5. Add an account with the **+** button, or restore an encrypted backup from **Settings → Backup & restore**.

You can change the PIN, security questions, biometrics, auto-lock delay, clipboard timeout, and theme in Settings. Security-question and sensitive-setting changes require the current PIN.

## Testing

The unit tests cover pure, deterministic logic:

- Password generation constraints, character groups, ambiguity filtering
- Strength estimation and meter segments
- Fuzzy search matching
- PIN/URL validation and answer normalization

Instrumented Compose tests are configured in the build but not included; screen behavior depends on Android Keystore, biometric hardware, and SAF, so it is best exercised on a device or emulator with enrolled biometrics.

## Troubleshooting

- **Biometric prompt unavailable**: enroll a fingerprint/face in system settings and re-enable biometrics in OfflineVault settings.
- **Camera unavailable**: OfflineVault launches the device's camera app; emulators without a camera app cannot capture photos.
- **Forgot PIN**: use two security answers. If those are also unavailable, restore an encrypted backup or erase the app data; there is no recovery service.
- **Build cannot find SDK**: check `local.properties` and `ANDROID_HOME`.

## License

OfflineVault is released under the [MIT License](LICENSE).

It is provided "as is", without warranty of any kind. OfflineVault has not been independently audited; assess the security design before using it for high-value secrets.
