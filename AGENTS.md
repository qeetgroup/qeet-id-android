# AGENTS.md — qeet-id-android

**The model-neutral instruction file for coding agents.** [CLAUDE.md](CLAUDE.md), [GEMINI.md](GEMINI.md) and
[.github/copilot-instructions.md](.github/copilot-instructions.md) point here and add nothing architectural.

## What this repository is

The **Qeet ID authenticator for Android**: Kotlin, Jetpack Compose, one app module, `com.qeetgroup.qeetid`. Two jobs:
**offline sign-in codes** (TOTP) and **approving Qeet ID sign-ins** on a linked phone, behind an app lock. It replaces
the Expo app in `qeet-id-auth` and keeps its rules.

It is a **client of a contract it does not own**: `qeet-id-server`'s `/v1/mfa/push/*` routes
(`api/openapi/auth.yaml`, with `internal/access/mfa/{pairing,push}.go` as the tiebreaker). The UI is built only from
**Qeetrix for Android**, taken from source at a pinned release.

## Context hierarchy

```text
qeet-context (L0)  →  qeet-id-files (L1)  →  qeet-id-server (contract + enforcement)
                                            →  qeet-id-android (L2 — this repository)
```

## Read before changing code

| File | For |
| --- | --- |
| [qeet-repo.yml](qeet-repo.yml) | Machine-readable identity |
| [docs/llm/architecture-map.md](docs/llm/architecture-map.md) | "Where is X?" |
| [docs/llm/context.md](docs/llm/context.md) | How the app works, and its known gaps |
| [docs/llm/boundaries.md](docs/llm/boundaries.md) | What it owns, and what it must not do |
| [docs/llm/workflows.md](docs/llm/workflows.md) | Common changes, step by step |
| [docs/firebase.md](docs/firebase.md) | Push, with and without a Firebase project |

The screens follow the Qeet ID mockups in `qeetgroup/qeet-mockups` (`products/qeet-id/screens/`, modules 01 to 05;
not in this repository) and the Qeetrix design rules; product flow in `qeet-id-files/TAD.md` §6.

## Rules

1. **Secrets live sealed, never in UI state.** TOTP secrets and the device credential are sealed with the Keystore key
   (`data/secure/`), one file per account secret, in `noBackupFilesDir`. They never enter a `StateFlow`, a log, a
   `toString`, a crash report or a notification. Codes are computed in `AccountStore`, which keeps secrets to itself.
2. **The device credential is a password.** `PhoneLink.deviceToken` approves sign-ins with no session until the
   server stops accepting it 180 days after the last link. It travels only in request bodies to the API, over HTTPS.
3. **No localhost default.** Release builds talk to `https://api.id.qeet.in` only (HTTPS enforced in code and by the
   network security config). Debug builds may use `qeetid.apiUrl` from `local.properties`, cleartext allowed.
4. **A phone links only with a pairing code, and only when the person confirms.** A scanned code or a
   `qeetidauth://pair` link only fills the form; Link phone sends it. Linking never accepts or asks for an access
   token.
5. **Approving needs the number and the person.** Approve sends the 2-digit number from the sign-in screen, and only
   after `BiometricPrompt` (strong biometric or the screen lock) succeeds. A phone without a screen lock cannot
   approve; deny never needs verification.
6. **No bearer on the phone's routes.** The device credential in the body is the design (`push.go`). Don't add a
   bearer and don't weaken it.
7. **Qeetrix only.** Every screen uses Qeetrix for Android components, tokens, type and icons: no hand-made colours,
   fonts, shapes or icon sets. When a component is missing, compose it from Qeetrix primitives (`ui/Common.kt`) and
   note the gap. Never edit `.qeetrix/`.
8. **Every string in `res/values/strings.xml`.** Copy follows the mockup. No uppercase except group labels.
9. **Firebase is optional at build time.** Without `app/google-services.json` the app builds, tests and runs;
   release builds then say they cannot be linked, debug builds link with a stand-in token.
10. **Tests:** a unit test for every parser, store and API mapping; a Robolectric Compose test for every flow
    (`ui/AppFlowsTest.kt`). No screenshot tests.
11. **Administration has its own session, apart from the link.** An administrator signs in with a passkey (Android's
    Credential Manager, `admin/`) and gets their own Qeet ID session: the refresh token sealed in `admin-session`,
    the access token in memory only, neither in UI state, a log or a notification. It is used only for the
    administration routes (`AdminApi`), never for the link or approvals. Anything that gives or takes access asks
    first: a passkey step-up (approve an access request, reactivate) or `BiometricPrompt` (suspend, stop all agents).
    The app's signing certificate must be in `id.qeet.in/.well-known/assetlinks.json` (qeet-id-website) and its
    `android:apk-key-hash:` origin in the server's `WEBAUTHN_RP_ORIGINS`, or passkey sign-in is refused.

## Commands

```bash
scripts/fetch-qeetrix.sh          # once, and whenever qeetrix.tag/qeetrix.commit change
./gradlew build                   # assemble debug and release, unit + Compose tests, Android Lint, ktlint — the gate
./gradlew :app:testDebugUnitTest  # tests only
./gradlew :app:ktlintFormat       # format
./gradlew :app:installDebug       # on a device or emulator
```

## What CI enforces

`ci.yml` · **Verify**, the only workflow (pull requests into `develop` and `main`, and by hand; a release branch is
checked once, on its pull request into `main`): Gradle wrapper validation, `./gradlew :app:ktlintCheck :app:lint`
(Android Lint compiles the app), and gitleaks (free CLI, checksum-pinned) over the pull request's commits. The full
gate, `./gradlew build` (debug and release builds, unit and Compose UI tests), runs locally before every push, not in
CI. Dependency advisories come from Dependabot alerts, and Dependabot proposes Gradle and Actions updates monthly into
`develop`. There is no Play Store
workflow yet: it needs the upload key and the Play Console app.

## Branches and releases

The app ships with the Qeet ID monthly release and carries its number: `develop` is where changes land, `main` is what
ships. For a month the app changes, `release/YEAR.MONTH.0` is cut from `develop` (e.g. `release/2027.1.0`); its first
commit sets `qeetid.versionName` to that version (no leading zeros) and raises `qeetid.versionCode`. After the cut it
takes fixes only, each made on `develop` first and then copied over; testers install it from Play internal testing.
After sign-off it merges into `main`, is tagged `v2027.1.0` and goes to the store. An urgent fix afterwards is
`hotfix/YEAR.MONTH.N` from `main` (e.g. `hotfix/2027.1.1`). A month without app changes ships no app release.

## Before you finish

```bash
./gradlew build
git diff
```

Then, for anything touching linking or approvals, run the flow on an emulator against a local server
([docs/llm/workflows.md](docs/llm/workflows.md#run-the-whole-flow-against-a-local-server)).

## Escalate rather than proceed

Putting a secret in state, a log or a backup · accepting an access token · linking without the person's tap ·
approving without the number or without `BiometricPrompt` · adding a bearer to the phone's routes · changing the
device-credential model · editing Qeetrix for Android · anything requiring another repository.
