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
qeet-context (L0)  →  qeet-id-context (L1)  →  qeet-id-server (contract + enforcement)
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

The approved screens are the mockup in `qeet-id/mockups/android/` (not in this repository) and the Qeetrix design
rules; product flow in `qeet-id-context/FLOWS/mfa.md`.

## Rules

1. **Secrets live sealed, never in UI state.** TOTP secrets and the device credential are sealed with the Keystore key
   (`data/secure/`), one file per account secret, in `noBackupFilesDir`. They never enter a `StateFlow`, a log, a
   `toString`, a crash report or a notification. Codes are computed in `AccountStore`, which keeps secrets to itself.
2. **The device credential is a password.** `PhoneLink.deviceToken` approves sign-ins with no session until the
   server stops accepting it 180 days after the last link. It travels only in request bodies to the API, over HTTPS.
3. **No localhost default.** Release builds talk to `https://api.id.qeet.in` only (HTTPS enforced in code and by the
   network security config). Debug builds may use `qeetid.apiUrl` from `local.properties`, cleartext allowed.
4. **A phone links only with a pairing code, and only when the person confirms.** A scanned code or a
   `qeetidauth://pair` link only fills the form; Link phone sends it. Never accept or ask for an access token.
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

## Commands

```bash
scripts/fetch-qeetrix.sh          # once, and whenever qeetrix.tag/qeetrix.commit change
./gradlew build                   # assemble debug and release, unit + Compose tests, Android Lint, ktlint — the gate
./gradlew :app:testDebugUnitTest  # tests only
./gradlew :app:ktlintFormat       # format
./gradlew :app:installDebug       # on a device or emulator
```

## What CI enforces

`ci.yml` (pull requests into `main`, pushes to `main`, manual): Gradle wrapper validation, then `./gradlew build`.
`security.yml`: gitleaks (free CLI, checksum-pinned) over the history. `codeql.yml`: CodeQL, skipped while the
repository is private. There is no release or Play Store workflow yet.

## Branches

Work on `develop`; `main` is what ships. A pull request from `develop` into `main` runs CI.

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
