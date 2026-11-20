# CLAUDE.md — qeet-id-android

**Read [AGENTS.md](AGENTS.md) first.** It is the model-neutral instruction file and the source of truth for this
repository. This file adds only Claude-specific guidance.

```text
CLAUDE.md  →  AGENTS.md  →  docs/llm/*
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

Parent context: **L0** `qeetgroup/qeet-context` · **L1** `qeetgroup/qeet-id-files`.

## The things most likely to trip you up here

1. **Secrets never enter UI state.** Codes come out of `AccountStore.code()`; secrets stay inside it.
2. **Qeetrix only.** Look for the Qeetrix component first (`.qeetrix/qeetrix-android/docs/components.md`); compose
   from its primitives when one is missing. Never edit `.qeetrix/`.
3. **Approving = number + `BiometricPrompt`.** Never shortcut either.
4. **Firebase is optional.** Code that touches Firebase must work when `FirebaseApp.getApps()` is empty.
5. **Warnings are errors.** Android Lint and ktlint fail the build; fix the cause, suppress only with a reason.
6. **Run `scripts/fetch-qeetrix.sh` first** on a fresh clone, or the build stops at settings.

## Working style

- **Read before editing.** Match the neighbouring screen and view model rather than introducing an abstraction.
- Verify with `./gradlew build` and the Robolectric flow tests — **no screenshots or visual diffs**.
- **Do not read `.env*`, `keystore.properties`, `*.jks` or `google-services.json`** into anything you write.

## Finishing a change

```bash
./gradlew build
git diff
```

## Escalate rather than proceed

Stop and report if a task would weaken a security control, change the device-credential model, need a server change,
or modify another repository (including Qeetrix for Android).
