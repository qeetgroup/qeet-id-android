# 🔥 Firebase Cloud Messaging

Sign-in requests reach a phone as Firebase Cloud Messaging (FCM) data messages. The app builds, tests and runs without
any Firebase setup; this page says what that means, and what is needed for real push.

## 📨 The message

The server sends an FCM HTTP v1 **data-only** message, priority `HIGH`, TTL 120 s:

```json
{ "type": "sign_in_request", "challenge_id": "7f9c2a40-1b8e-4d2c-9a51-3e6f0b2d8c11", "action": "sign_in" }
```

The app writes the notification itself on its `push-mfa` channel (high importance): **Sign-in request** — *Someone is
signing in to your Qeet ID account. Tap to review.* A message that is not `sign_in_request`, or whose `challenge_id` is
not a UUID, is ignored; so is any message while the phone is not linked.

## 🔑 Tokens

| When | What happens |
|:--|:--|
| Before linking | Nothing: automatic token creation is off (`firebase_messaging_auto_init_enabled=false`), so a phone that never links never registers with Firebase |
| Linking | The app asks Firebase for a token and sends it in the pairing claim (`push_token`, `platform: "android"`) |
| Firebase rotates the token | `onNewToken` sends it to `POST /v1/mfa/push/devices/{id}/push-token` with the device credential |
| Every app start | If the current token is not the one the server last accepted (offline, older server), it is sent again |
| Unlinking | The token is deleted and automatic creation turned off |

## 🧩 Without Firebase

There is no `app/google-services.json` in the repository. Without it:

| Build | Behaviour |
|:--|:--|
| Release | Cannot be linked; the linking screens say *This build can't get sign-in requests* |
| Debug | Links with a stand-in token (`debug-stand-in-<uuid>`) and says so on the linking screens |
| Both | Codes, the lock and settings work as usual |

Requests still reach a linked phone: each time the app comes to the foreground — and on pull to refresh on
Accounts — it asks `POST /v1/mfa/push/devices/{id}/pending` and opens the newest waiting request.

A debug build also has an entry point that feeds a message into the same handler Firebase's service uses (only the
shell can call it; it requires `android.permission.DUMP`):

```sh
adb shell am broadcast -n com.qeetgroup.qeetid/.debug.DebugPushReceiver \
    --es type sign_in_request --es challenge_id <request id> --es action sign_in
```

## ✅ What the owner provides for real push

1. **A Firebase project** (free Spark plan is enough; FCM is free) with an **Android app** registered for the package
   `com.qeetgroup.qeetid`. Add the release signing certificate's SHA-256 if other Firebase features are added later
   (FCM does not need it).
2. **`google-services.json`** from that app, placed at `app/google-services.json` on the machine (or CI runner) that
   builds releases. It is git-ignored; the build turns its values into the resources Firebase reads, so no Google
   Services Gradle plugin is involved. A build with the file reports `BuildConfig.FIREBASE_CONFIGURED = true`.
3. **A service account** with the *Firebase Cloud Messaging API Admin* role, for `qeet-id-server`'s
   `FCM_SERVICE_ACCOUNT`, so the server can send. The *Firebase Cloud Messaging API (V1)* must be enabled on the
   project.
4. For CI release builds later: the file as a repository secret, written to `app/google-services.json` before the
   build.
