# Herald (Kotlin Multiplatform Android client for the Hermes gateway)

Rules in `/home/dayltan/AGENTS.md` still apply. Conventions below mirror `CONTRIBUTING.md`; read it for detail.

## Environment
- The Pi has NO JDK and no Android SDK. You cannot compile or run Gradle here. Never claim a build or test passed; say "unverified, needs CI".
- Real verification is GitHub Actions (`.github/workflows/ci.yml`). Leave changes uncommitted unless told otherwise; the reviewer commits.
- The CI command to mirror: `./gradlew --continue :shared:core:testAndroidHostTest :shared:designsystem:testAndroidHostTest :shared:ui:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug`

## Layout
- `shared/core`: chat and protocol logic (JSON-RPC over `/api/ws`). Tests: `ChatReducerTest` (events), `ChatSessionTest` (gateway calls, fake gateway).
- `shared/ui`, `shared/designsystem`: Compose UI and view models.
- `androidApp`, `wearApp`, `shared/wearlink`: app shells.
- `docs/ROADMAP.md`: tick the item your change completes, in the same change.

## Rules
- One feature or fix per change. No refactors mixed into a feature.
- Add a test with every behaviour change; a bug fix needs a test that fails without it.
- Commit subjects: `Area: what changed` in plain words. No `feat:` / `fix:` prefixes.
- Lint fails on any new issue. Do not add to `lint-baseline.xml`.
- Match surrounding naming and comment density. KDoc says what a thing is for.
- Check the protocol docs (`docs/hermes-protocol-research.md`) before adding a gateway call.
- Never put real chats, prompts, jobs, bots or gateway addresses in code, tests or screenshots.
- Never push, never open a PR, never touch the `DIAGNOSTIC` commit.
