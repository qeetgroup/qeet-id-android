# Repository Context — qeet-id-android

**Level:** L2 · **Status:** active · **Evidence state:** verified · **Last verified:** 2026-11-06
**Verification scope:** screens, storage, routes and gaps read from source; flows run on an emulator against a local
`qeet-id-server` (`feat/native-push`).

## Identity

The **Qeet ID authenticator for Android** — Kotlin 2.4, Jetpack Compose (BOM 2026.09.00), AGP 9.5, minSdk 29,
compile and target SDK 37, one module. Application id `com.qeetgroup.qeetid`, name "Qeet ID", deep link scheme
`qeetidauth`. Replaces the Expo app in `qeet-id-auth`, keeping its rules.

## Context inheritance

```text
qeet-context (L0)  →  qeet-id-context (L1)  →  qeet-id-server (contract + enforcement)
                                            →  qeet-id-android (L2 — this document)
```

## Responsibilities

1. **Sign-in codes** — TOTP (RFC 6238) for any site: scan an `otpauth://` QR code or type the key; SHA-1/256/512,
   6 to 8 digits, any period up to 300 s. Offline.
2. **Sign-in approvals** — link the phone with a pairing code, then approve or deny each Qeet ID sign-in: the 2-digit
   number from the sign-in screen and `BiometricPrompt` before approving.
3. **App lock** — fingerprint, face or screen lock to open, and again after 30 seconds away.

It verifies nothing and decides nothing: the server owns challenge validity, the number match, policy and sessions.

## Storage — the security core

**Evidence:** `data/secure/`, `codes/AccountStore.kt`, `approvals/PhoneLinkStore.kt`

| Sealed file | Contents |
|---|---|
| `accounts` | Account list: id, issuer, label, algorithm, digits, period — **no secrets** |
| `secret-<uuid>` | **One TOTP secret per account**, raw bytes |
| `phone-link` | `{ userId, deviceId, deviceToken, deviceName, linkedAtMillis, accountEmail, pushTokenHash }` |

- Sealed with an **AES-256-GCM key in the Android Keystore** (`KeystoreSecretBox`), never the deprecated
  security-crypto library. Each file binds its own name as associated data, so files cannot be swapped.
- In `noBackupFilesDir`, with `allowBackup="false"`, backup rules and data extraction rules excluding everything:
  nothing reaches a cloud backup or a device transfer.
- A file sealed by a key the phone no longer has (Keystore reset) is set aside: the accounts list says so; a phone link
  is dropped (linking again replaces it).
- Secrets are read into a private cache inside `AccountStore` for codes, never into a `StateFlow`, and dropped when the
  app locks. Preferences (onboarded, app lock, hide codes) are plain and hold nothing secret.

**The device token is a long-lived credential**: with `userId` it approves with no session until the server stops
accepting it 180 days after the last link. Treat it as a password.

## API interaction

`BuildConfig.API_BASE_URL`: `https://api.id.qeet.in` in release builds (HTTPS enforced in `AppContainer.apiBaseUrl`
and by the network security config); debug builds read `qeetid.apiUrl` from `local.properties`, cleartext allowed —
`http://127.0.0.1:4001` with `adb reverse`, since Android 17 blocks private addresses for apps targeting it.
OkHttp follows no redirects (the credential is in bodies).

| Mode | Route | Auth |
|---|---|---|
| Linking | `POST /v1/mfa/push/pairing/claim` | the one-time pairing code; sends the FCM token, `platform: android` |
| Reading a request | `GET /v1/mfa/push/challenges/{id}` | none |
| Answering | `POST /v1/mfa/push/challenges/{id}/respond` | `user_id` + `device_token`; `number` to approve |
| Waiting requests | `POST /v1/mfa/push/devices/{id}/pending` | `user_id` + `device_token` |
| New push token | `POST /v1/mfa/push/devices/{id}/push-token` | `user_id` + `device_token` |
| Unlinking | `POST /v1/mfa/push/devices/{id}/unlink` | `user_id` + `device_token` |

Status mapping — linking: `401 mfa.pairing_code_invalid` → wrong, used or expired code (shown on the field) ·
`429` → wait `retry_after`. Answering: `403 mfa.push_number_mismatch` → the server refused the sign-in ("This request
has ended") · `410`/`404` → expired · `409` → already answered (reloaded) · `401` → not this phone's account.
Unlinking: `204` → removed · `401 mfa.push_unauthorized` → already removed, cleared here too · anything else keeps
the link. Pending and push-token: `404`/`405` without a Qeet ID error code → an older server, ignored · `401` → the
credential is dead, the link is cleared.

The expiry countdown uses the server's clock (its `Date` header), as the Expo app did.

## Push

- **FCM data messages**: `{type: "sign_in_request", challenge_id, action}`, priority high, TTL 120 s. The app writes
  the notification itself on the `push-mfa` channel (high importance): "Sign-in request" / "Someone is signing in to
  your Qeet ID account. Tap to review.", the Q-key as its small icon, timed out after two minutes, no Approve button.
  A tap opens the request after unlocking; a message while the app is in front opens it directly.
- **Tokens**: Firebase's automatic token creation is off until the phone links; unlinking deletes the token. A
  rotated token (`onNewToken`) is sent to `push-token`; the app re-checks at every start and re-sends if a send failed.
- **Without Firebase** (no `app/google-services.json`): release builds say they cannot be linked; debug builds link
  with a stand-in token. Either way, **the pending check** on every return to the foreground (and pull to refresh on
  Accounts) opens the newest waiting request — the path for local testing and for lost pushes.

## Linking

A pairing code arrives scanned (the app's scanner), from the phone's camera opening `qeetidauth://pair?code=…`, or
typed. Each only fills the Name this phone form; **Link phone** sends it. The phone's name is prefilled from Settings ›
About phone. The account's email is shown when the server returns it (`account.email`), otherwise "Your Qeet ID
account".

## Testing and CI

`./gradlew build` is the gate: debug and release (R8) builds, JVM unit tests, Robolectric Compose tests for every flow,
Android Lint and ktlint, all with warnings as errors; run it locally before every push. CI (`ci.yml`, Verify) runs
ktlint and Android Lint on pull requests into `develop` and `main`, and on demand. No instrumented tests and no screenshot tests.

## Known gaps

| Gap | Detail |
|---|---|
| **No Firebase project yet** | Real push needs the owner's Firebase project, `app/google-services.json` and the server's FCM service account. Until then requests arrive by the pending check |
| **A wiped app stays registered** | An app deleted without unlinking stays on the account until the credential expires (180 days) or the person removes it in Qeet ID |
| **No release workflow** | No Play Store or signing workflow yet; release signing reads a local `keystore.properties` |

## Documentation authority

Source > `qeet-id-server`'s `/v1/mfa/push/*` handlers > the mockup > README. Product-level narrative:
`qeet-id-context/FLOWS/mfa.md`.
