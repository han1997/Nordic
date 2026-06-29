# Start Complete-Resume Videos From Beginning

## Goal

Prevent Emby/Yamby-style video playback from opening at the end of a video when Emby reports a resume position at or beyond the known runtime but the item is not marked played. Such resume points are already excluded from the continue-watching shelf as effectively complete, so direct playback should start from the beginning instead of seeking to the terminal position.

## What I already know

* `continueWatchingShelf` excludes videos whose `playbackPositionSeconds >= durationSeconds` when duration is known.
* `resolveVideoInitialStartPositionMs` currently clamps an over-duration resume point to `durationSeconds`, which can start the player at the end.
* Existing focused tests for video startup and relative seek helpers live in `VideoPlaybackEngineTest`.
* The Emby contract currently documents the old clamp-to-duration behavior and needs to be updated if this task changes the contract.

## Requirements

* Keep normal unfinished resume behavior: positive resume positions less than known duration should seek to that position.
* Keep played videos and videos with no positive resume position starting at `0`.
* For known-duration videos, treat resume positions at or beyond duration as complete and start at `0`.
* Keep unknown-duration resume behavior unchanged: positive resume positions may still be used.
* Update focused unit tests and the Emby integration spec.

## Acceptance Criteria

* [ ] `resolveVideoInitialStartPositionMs` returns `0` when `durationSeconds > 0` and `playbackPositionSeconds >= durationSeconds`.
* [ ] Positive resume points below known duration still return `resumeSeconds * 1000L`.
* [ ] Unknown-duration positive resume points still return `resumeSeconds * 1000L`.
* [ ] Focused video playback tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Tests updated for complete-resume and unknown-duration behavior.
* Emby integration spec reflects the new initial playback start-position contract.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Update the pure `resolveVideoInitialStartPositionMs` helper in `VideoPlaybackEngine.kt` so playback startup follows the same completion boundary as the video continue-watching shelf. No repository mapping or UI state changes are required.

## Decision (ADR-lite)

Context: Emby can expose a resume tick at or beyond runtime while `Played` is false, often due to stale or incomplete user data.

Decision: Treat known-duration resume points at or beyond runtime as effectively complete for initial playback and start from zero.

Consequences: Direct playback no longer opens at the end of a video. Unknown-duration items preserve their existing resume behavior because there is no reliable completion boundary.

## Out of Scope

* Changing Emby repository metadata mapping.
* Changing continue-watching shelf sorting/filtering.
* Marking videos watched/unwatched in Emby.
* Adding instrumentation tests.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/playback/VideoPlaybackEngineTest.kt`.
* Relevant specs: `.trellis/spec/backend/emby-integration.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/directory-structure.md`.
