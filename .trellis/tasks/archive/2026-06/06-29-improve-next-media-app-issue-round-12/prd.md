# Improve Next Media App Issue Round 12

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds improved repository response correctness, Emby library filtering, and same-song music stream refresh behavior.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `VideoPlaybackEngine.play(video)` currently replaces the ExoPlayer item only when the current state video id differs from the requested video id.
* Emby stream URLs include server/token information and can change for the same video id after account, token, or server changes, so id-only comparison can keep a stale video URL.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Replace the current ExoPlayer media item when the requested video has the same id but a different stream URL from the current video state.
* Keep same-id/same-stream video requests on the existing item to preserve the current lightweight play/resume behavior.
* Keep different-id video requests replacing the item as before.
* Add focused unit tests for the replacement decision.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Same-id/different-stream video requests replace the ExoPlayer item.
* [x] Same-id/same-stream video requests do not replace the ExoPlayer item.
* [x] Different-id video requests still replace the ExoPlayer item.
* [x] Focused tests cover the replacement decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Dependency upgrades unless required by the selected issue.
* Changes to Emby repository URL generation or progress sync behavior.

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/emby-integration.md`.
* Expected implementation is a small pure helper in `VideoPlaybackEngine.kt` used by `play(video)`.
