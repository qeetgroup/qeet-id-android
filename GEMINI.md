# GEMINI.md — qeet-id-android

**Read [AGENTS.md](AGENTS.md) first.** It is the model-neutral instruction file and the source of truth for this
repository. This file is a pointer and adds no architecture.

```text
GEMINI.md  →  AGENTS.md  →  docs/llm/*
```

## Canonical context

| File | For |
| --- | --- |
| [qeet-repo.yml](qeet-repo.yml) | Machine-readable repository identity |
| [AGENTS.md](AGENTS.md) | **Rules, commands, what CI enforces** |
| [docs/llm/context.md](docs/llm/context.md) | How this repository actually works |
| [docs/llm/boundaries.md](docs/llm/boundaries.md) | What it owns, and what it must not touch |
| [docs/llm/workflows.md](docs/llm/workflows.md) | Step-by-step for common changes |
| [docs/llm/architecture-map.md](docs/llm/architecture-map.md) | "Where is X?" — fastest path to a file |

Parent context: **L0** `qeetgroup/qeet-context` · **L1** `qeetgroup/qeet-id-context`.

## What this repository is

The **Qeet ID authenticator for Android** — Kotlin, Jetpack Compose, `com.qeetgroup.qeetid`, built from Qeetrix for
Android. Offline sign-in codes and push sign-in approval for a linked phone.

## Non-negotiables

1. **Secrets sealed by the Keystore, never in UI state, logs or backups.**
2. **The device credential is a password**; no bearer on the phone's routes.
3. **Linking only with a pairing code the person confirms.**
4. **Approving needs the number and `BiometricPrompt`.**
5. **Qeetrix only**, never edited from here.
6. **HTTPS only in release builds.**

## Commands

`scripts/fetch-qeetrix.sh` · `./gradlew build` · `./gradlew :app:testDebugUnitTest` · `./gradlew :app:ktlintFormat` ·
`./gradlew :app:installDebug`

That list is complete — **do not invent commands.**

## Before finishing

```bash
./gradlew build
git diff
```
