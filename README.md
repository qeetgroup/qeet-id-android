# Qeet ID for Android

The Qeet ID authenticator for Android. It shows one-time codes for two-step sign-in, and lets you
approve or deny a Qeet ID sign-in from your phone. It is a native Kotlin and Jetpack Compose app,
built on [Qeetrix for Android](https://github.com/qeetgroup/qeetrix-android).

It replaces the Expo / React Native app in [qeet-id-auth](https://github.com/qeetgroup/qeet-id-auth).
The iOS app is [qeet-id-ios](https://github.com/qeetgroup/qeet-id-ios).

## Status

New repository. The app has not been started yet.

## What it does

- **One-time codes (TOTP):** computed on the phone, so they work offline
- **Push approvals:** approve or deny a sign-in on the phone
- **Linking:** link the phone to an account with a one-time pairing code
- **Biometric lock:** fingerprint or face unlock before codes and approvals

## Stack

| | |
|---|---|
| Android | API 29 (Android 10) or later (the Qeetrix for Android minimum) |
| Language | Kotlin, Jetpack Compose |
| Design system | [Qeetrix for Android](https://github.com/qeetgroup/qeetrix-android), through Gradle |
| Secrets | Android Keystore |
| Backend | [qeet-id-server](https://github.com/qeetgroup/qeet-id-server) |

## Server endpoints the app calls

| Purpose | Endpoint | Credential |
|---|---|---|
| Link the phone | `POST /v1/mfa/push/pairing/claim` | One-time pairing code |
| Read a sign-in request | `GET /v1/mfa/push/challenges/{id}` | Device credential |
| Approve or deny it | `POST /v1/mfa/push/challenges/{id}/respond` | Device credential |
| Unlink the phone | `POST /v1/mfa/push/devices/{id}/unlink` | Device credential |
