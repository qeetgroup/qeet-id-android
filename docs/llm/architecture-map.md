# Architecture Map — qeet-id-android

**Level:** L2 · **Last verified:** 2026-11-06
**Verification scope:** every path confirmed to exist.

All Kotlin paths are under `app/src/main/kotlin/com/qeetgroup/qeetid/` unless they say otherwise.

| Need | Path |
|---|---|
| Repository identity | [`qeet-repo.yml`](../../qeet-repo.yml) |
| Agent instructions | [`AGENTS.md`](../../AGENTS.md) |
| Versions of everything | `gradle/libs.versions.toml` |
| Qeetrix for Android pin (tag + commit) | `gradle.properties` (`qeetrix.tag`, `qeetrix.commit`) |
| The composite build of Qeetrix | `settings.gradle.kts`, `scripts/fetch-qeetrix.sh` |
| App build: API URL, Firebase config, signing, lint | `app/build.gradle.kts` |
| Manifest: permissions, deep link, FCM service | `app/src/main/AndroidManifest.xml` |
| Backup and network rules | `app/src/main/res/xml/` (`data_extraction_rules`, `backup_rules`, `network_security_config`) |
| Debug-only cleartext and push entry point | `app/src/debug/` |
| Every string | `app/src/main/res/values/strings.xml` |
| Launcher, monochrome, notification and in-app icons | `app/src/main/res/drawable/`, `mipmap-anydpi/`, `ic_launcher-playstore.png` |

## The app

| Concern | Path |
|---|---|
| Application, lifecycle hooks (lock, pending check, token sync) | `QeetIdApplication.kt` |
| Manual dependency injection | `AppContainer.kt` |
| The activity: FLAG_SECURE, overlays, notification taps, pairing links | `MainActivity.kt` |
| Root composable, theme, lock gate, navigation | `ui/QeetIdApp.kt` |
| Routes | `ui/Routes.kt` |
| Shared composables (Screen, AppMark, LetterTile, SheetAction) | `ui/Common.kt` |

## Features

| Screen (mockup) | Path |
|---|---|
| Welcome, Lock Qeet ID, notifications primer (1–3) | `onboarding/OnboardingScreens.kt` |
| Locked (21) | `lock/LockedScreen.kt` |
| Accounts, actions, delete, rename, alerts (4, 5, 9, 10, 24–26) | `codes/AccountsScreen.kt`, `codes/AccountsViewModel.kt` |
| Scan a QR code, camera denied, type the key (6–8) | `codes/AddAccountScreens.kt`, `codes/TypeKeyForm.kt`, `scanner/QrScanner.kt` |
| Link this phone, scan, name, linked (11–14) | `approvals/LinkScreens.kt`, `approvals/LinkingViewModel.kt` |
| Sign-in request and its outcomes (16–20) | `approvals/RequestScreen.kt`, `approvals/RequestViewModel.kt`, `approvals/BrowserName.kt` |
| Settings, unlink (22, 23) | `settings/SettingsScreen.kt`, `settings/SettingsViewModel.kt` |

## Logic

| Concern | Path |
|---|---|
| **RFC 6238 TOTP**, HOTP | `codes/totp/Totp.kt` |
| Base32 | `codes/totp/Base32.kt` |
| `otpauth://` parsing | `codes/totp/OtpAuthUri.kt` |
| **The account vault** (list + one sealed secret per account, codes) | `codes/AccountStore.kt` |
| **Keystore sealing** (AES-256-GCM) | `data/secure/SecretBox.kt` |
| Sealed files (atomic, no-backup directory) | `data/secure/SealedFiles.kt` |
| **The device credential** | `approvals/PhoneLinkStore.kt` |
| Pairing codes and links | `approvals/PairingCode.kt` |
| What to open next (requests, pairing links, the pending check) | `approvals/RequestInbox.kt` |
| **The Qeet ID API client** | `data/api/QeetIdApi.kt`, `data/api/ApiModels.kt` |
| Push tokens (Firebase, debug stand-in, none) | `approvals/push/PushTokens.kt` |
| Sending a rotated push token | `approvals/push/PushTokenSync.kt` |
| FCM service, message handling | `approvals/push/QeetIdMessagingService.kt`, `approvals/push/PushMessages.kt` |
| The sign-in request notification (`push-mfa`) | `approvals/push/SignInNotifications.kt` |
| **BiometricPrompt** | `lock/UserVerification.kt` |
| The app lock (30 s grace) | `lock/AppLock.kt` |
| Preferences (not secret) | `settings/Preferences.kt` |
| Clipboard (sensitive, cleared after 30 s), connectivity, notifications, device name | `system/` |

## Sealed files (`noBackupFilesDir/vault/`)

```text
accounts           the account list (no secrets), JSON
secret-<uuid>      one TOTP secret per account, raw bytes
phone-link         { userId, deviceId, deviceToken, deviceName, linkedAtMillis, accountEmail, pushTokenHash }
```

Each file is `version | nonce | AES-GCM(ciphertext + tag)`, with its own name as the associated data.

## Tests (`app/src/test/kotlin/com/qeetgroup/qeetid/`)

| What | Path |
|---|---|
| RFC 6238 / RFC 4226 vectors, Base32, `otpauth://`, typed keys, pairing codes, browser names, the lock | `codes/totp/`, `codes/TypeKeyFormTest.kt`, `approvals/PairingCodeTest.kt`, `approvals/BrowserNameTest.kt`, `lock/AppLockTest.kt` |
| Sealed storage, vault, phone link | `data/secure/StorageTest.kt` |
| The API client against a fake server (MockWebServer) | `data/api/QeetIdApiTest.kt` |
| Linking, requests, pending check, push tokens, unlinking | `approvals/ApprovalsTest.kt` |
| The accounts view model | `codes/AccountsViewModelTest.kt` |
| **Every flow through the screens** (Robolectric + Compose) | `ui/AppFlowsTest.kt` |
| Fakes, the software box, the test application | `TestSupport.kt`, `TestQeetIdApplication.kt` |
