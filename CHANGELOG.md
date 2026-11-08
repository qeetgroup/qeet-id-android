# Changelog

All notable changes to Qeet ID for Android are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the app adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Sign-in codes**: time-based one-time codes (RFC 6238; SHA-1, SHA-256, SHA-512; 6 to 8 digits; any period up to
  300 s) from a scanned `otpauth://` QR code or a typed setup key; search, copy (marked sensitive, cleared after
  30 s), rename and delete; hide codes until tapped.
- **Sign-in approvals**: link the phone with a pairing code (scanned, typed, or opened from `qeetidauth://pair`),
  name it, and see which account it approves for and until when; approve or deny each request with the 2-digit
  number from the sign-in screen and Android's biometric prompt; approved, blocked, ended and expired outcomes.
- **Push**: Firebase Cloud Messaging data messages written as the app's own notification on the `push-mfa` channel;
  rotated tokens sent to the server; requests waiting for the phone opened when the app comes to the foreground and
  on pull to refresh.
- **App lock**: fingerprint, face or screen lock to open, asked again after 30 seconds away.
- **Welcome pages**: what the app does, the app lock, and the notification primer.
- **Settings**: the linked account and whether requests reach it, unlinking, app lock, hidden codes, about and help.
- **Protection**: secrets sealed by an Android Keystore AES-256-GCM key, kept out of backups and device transfer;
  screens kept out of screenshots and the recent-apps preview; other apps' overlays hidden; HTTPS only.
- Built entirely from Qeetrix for Android 0.1.0, in light and dark.
- **Administration**: an administrator signs in with their passkey (Android's Credential Manager) and gets their own
  session, kept apart from the phone's link: access requests to approve (after a fresh passkey check) or deny with a
  reason, critical events of the last day, and two emergency actions, suspend a person and stop all AI agents, each
  behind the phone's own check. Reached from Settings.
- **Requests waiting**: when more than one sign-in is waiting, one list with each request's time left and Deny all.
- **Move to a new phone**, **About and help** and **Update required** (shown when Qeet ID answers 426).

### Changed

- Every screen redrawn to the Qeet ID mockups: the Qeet orange icon and its rings, ruled settings-style groups,
  result pages for approved, denied, ended and linked, and the notification now names the browser and place.
