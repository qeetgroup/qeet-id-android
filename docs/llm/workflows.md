# Workflows — qeet-id-android

**Level:** L2 · **Last verified:** 2026-11-06

## Set up a fresh clone

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # or ANDROID_HOME
scripts/fetch-qeetrix.sh
./gradlew build
```

`fetch-qeetrix.sh` clones `qeetgroup/qeetrix-android` at `qeetrix.tag` into `.qeetrix/qeetrix-android`, refuses it
unless it is `qeetrix.commit`, and shares `sdk.dir` with it.

## Add or change a screen

1. Find the mockup screen and its "Uses …" components; use the Qeetrix component of that name
   (`.qeetrix/qeetrix-android/docs/components.md`, signatures in its sources).
2. Put the strings in `res/values/strings.xml` first.
3. State in a `ViewModel` with a `StateFlow`, built with `viewModel { … }` from `LocalAppContainer`; the screen only
   draws it. Secrets never pass through it.
4. Add the route to `ui/Routes.kt` and `ui/QeetIdApp.kt`.
5. Add the flow to `ui/AppFlowsTest.kt`.
6. `./gradlew :app:ktlintFormat` then `./gradlew build`.

When Qeetrix lacks something, compose it from Qeetrix primitives in `ui/Common.kt` (tokens, `QeetrixText`,
`QeetrixIcon`, `QeetrixShapes`) and record the gap for the design system.

## Change a route the app calls

1. Read the server first: `api/openapi/auth.yaml` and `internal/access/mfa/{pairing,push}.go` on `qeet-id-server`'s
   `develop`.
2. Change `data/api/ApiModels.kt` and `data/api/QeetIdApi.kt`; every result is a sealed type, never an exception.
3. Tolerate an older server: a missing route (404/405 without a Qeet ID error code) must not break the app.
4. Cover every status in `data/api/QeetIdApiTest.kt`.

## Bump Qeetrix for Android

1. Set `qeetrix.tag` and `qeetrix.commit` (the tag's commit: `git ls-remote https://github.com/qeetgroup/qeetrix-android refs/tags/<tag>`).
2. `scripts/fetch-qeetrix.sh`.
3. Match its toolchain in `gradle/libs.versions.toml` (AGP, Kotlin, Compose BOM).
4. Replace composed stand-ins with new components; `./gradlew build`.

## Run the whole flow against a local server

1. Start `qeet-id-server` with no push service configured (no Expo, FCM or APNs credentials), listening on your
   machine.
2. `local.properties`: `qeetid.apiUrl=http://10.0.2.2:4001` (the emulator's address for your computer; use the
   machine's LAN address for a phone).
3. Give the emulator a screen lock (approving needs one): `adb shell locksettings set-pin 1234`.
4. `./gradlew :app:installDebug`, open the app, go through the welcome pages.
5. In Qeet ID (or with the API), sign in and ask for a pairing code; in the app, Link this phone › Type the code
   instead, and link. The debug build sends a stand-in push token.
6. Start a sign-in that asks for push approval. Bring the app to the foreground (or pull to refresh on Accounts): the
   pending check opens the request. Type the number, approve, confirm with the PIN.
7. Unlink in Settings.

To deliver a push message by hand instead: `adb shell am broadcast -n com.qeetgroup.qeetid/.debug.DebugPushReceiver
--es type sign_in_request --es challenge_id <id> --es action sign_in`.

## Release (not automated yet)

1. Raise `qeetid.versionCode` and `qeetid.versionName` in `gradle.properties`; note it in `CHANGELOG.md`.
2. Put `app/google-services.json` and `keystore.properties` (with the upload keystore) in place.
3. `./gradlew build :app:bundleRelease`.
4. Upload the AAB in the Play Console.
