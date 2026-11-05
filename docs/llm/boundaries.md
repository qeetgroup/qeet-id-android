# Boundaries — qeet-id-android

**Level:** L2 · **Last verified:** 2026-11-06
**Verification scope:** ownership from `qeet-id-context`; routes and storage read from source.

## Owns

The Android authenticator: offline TOTP generation and storage, push sign-in approval on a linked phone, the app lock,
the on-device vault, and the sign-in request notification's wording.

## Does not own

| Not owned | Owner |
|---|---|
| **Challenge validity, the number match, MFA policy, session issuance** | `qeet-id-server` |
| Pairing codes, device-credential issuance, expiry and revocation | `qeet-id-server` |
| The API contract and the push payload | `qeet-id-server` |
| The pairing QR code and the list of linked phones | `qeet-id-console` (Account › Security › Link a phone) |
| The design system | `qeetrix-android` — **consumed from source, never edited here** |
| The Firebase project and the server's FCM credentials | The owner (docs/firebase.md) |

## Consumes

`api.id.qeet.in` — the six `/v1/mfa/push/*` routes in [context.md](context.md#api-interaction). Firebase Cloud
Messaging (optional at build time). Qeetrix for Android at the tag and commit in `gradle.properties`. **Nothing else.**

## Provides

Sign-in codes and approvals to a person. **No API, no package, no output another repository consumes.**

## Security boundaries

**The phone is the boundary.** This app holds real secrets on hardware Qeet does not control.

| Boundary | Enforcement |
|---|---|
| App → storage | Keystore AES-256-GCM sealing; `noBackupFilesDir`; no backup, no device transfer |
| Secret → memory | Read into `AccountStore`'s private cache for codes; never UI state; dropped on lock |
| Screen → outside | `FLAG_SECURE` (no screenshots, recordings or recents preview); other apps' overlays hidden |
| App launch → use | App lock (`BiometricPrompt`), again after 30 s away |
| Approve → server | The 2-digit number and `BiometricPrompt` first; a phone with no screen lock cannot approve |
| Phone → server | HTTPS only in release; device credential in the body; no bearer; no redirects |
| Link → phone | Only a pairing code the person confirms with a tap |
| Clipboard | Codes marked sensitive; cleared after 30 s |

## Cross-repository dependencies

```text
qeet-id-server   ──/v1/mfa/push/* + FCM data messages──►  qeet-id-android
qeetrix-android  ──Compose components, tokens, icons────►  qeet-id-android
```

## Safe to change without coordination

Layout and copy within the mockup · accessibility · a new Qeetrix component replacing a composed one · tests ·
anything that does not touch storage, the credential, a route or the push payload.

## Requires cross-repository coordination

| Change | Reaches |
|---|---|
| **A route's shape or status codes** | `qeet-id-server` owns `/v1/mfa/push/*` |
| **The push payload** (`type`, `challenge_id`, `action`) | `qeet-id-server` sends it |
| **Device-credential semantics** | `qeet-id-server` issues and revokes it |
| The pairing link format | `qeet-id-console` shows it |
| A missing Qeetrix component or icon | `qeetrix-android` |

## Hard limits

1. **Never put a TOTP secret or the device token in UI state, a log, a notification, a `toString` or a backup.**
2. **Never store a secret outside the sealed files** — not preferences, not plain files, not the clipboard beyond 30 s.
3. **Never approve without the number and `BiometricPrompt`.**
4. **Never link without the person's tap**, and never accept an access token.
5. **Never add a bearer to the phone's routes**, and never weaken the device-credential model.
6. **Never allow cleartext or a non-production API in a release build.**
7. **Never edit Qeetrix for Android** from this repository.
