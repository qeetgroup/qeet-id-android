# GitHub Copilot — qeet-id-android

**Canonical instructions: [`AGENTS.md`](../AGENTS.md).** This file is a summary; it adds no architecture.

## Repository

The **Qeet ID authenticator for Android** — Kotlin, Jetpack Compose, one module `app`, package
`com.qeetgroup.qeetid`, built only from Qeetrix for Android (a composite build of `qeetrix-android` at a pinned tag).

## Structure

`codes/` (accounts, TOTP, scanning, typed keys) · `approvals/` (linking, sign-in requests, `push/` for FCM and the
notification) · `lock/` (app lock, `BiometricPrompt`) · `onboarding/` · `settings/` · `scanner/` (CameraX + ZXing) ·
`data/` (`api/` the Qeet ID client, `secure/` Keystore sealing) · `system/` (clipboard, connectivity, notifications) ·
`ui/` (root, routes, shared composables) · `AppContainer.kt` (manual DI).

## Rules

1. **Secrets sealed by the Keystore, never in UI state, logs, `toString` or backups.**
2. **The device credential travels only in request bodies over HTTPS; no bearer.**
3. **Linking only with a pairing code the person confirms.**
4. **Approving needs the 2-digit number and `BiometricPrompt`.**
5. **Qeetrix components, tokens, type and icons only.**
6. **Every string in `strings.xml`.**

## Commands

`./gradlew build` · `./gradlew :app:testDebugUnitTest` · `./gradlew :app:ktlintFormat`

## Do not

- suggest EncryptedSharedPreferences or security-crypto — the app seals with its own Keystore key
- store a secret in a `StateFlow`, a preference, a log or a notification
- add a cleartext or localhost default to release builds
- link a phone or approve a request without the person's action
- hand-draw a colour, font, shape or icon that Qeetrix provides
- invent Gradle tasks — read `app/build.gradle.kts`
