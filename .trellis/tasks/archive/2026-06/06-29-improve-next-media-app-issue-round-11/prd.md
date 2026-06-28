# Improve Next Media App Issue Round 11

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds improved repository response correctness and Emby library filtering.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `MusicPlaybackEngine.play(song)` currently replaces the Media3 item only when the current media id differs from the requested song id.
* Navidrome stream URLs include generated auth parameters and can change for the same song id after refresh/config changes, so id-only comparison can keep a stale URL.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Replace the current Media3 item when the requested music song has the same id but a different stream URL from the current Media3 item.
* Keep same-id/same-stream requests on the existing item to preserve the current lightweight play/resume behavior.
* Keep different-id requests replacing the item as before.
* Add focused unit tests for the replacement decision.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Same-id/different-stream music requests replace the Media3 item.
* [x] Same-id/same-stream music requests do not replace the Media3 item.
* [x] Different-id music requests still replace the Media3 item.
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

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`.
* Expected implementation is a small pure helper in `MusicPlaybackEngine.kt` used by `play(song)`.
