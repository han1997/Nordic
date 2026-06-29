# Sync Emby Video Progress Periodically

## Goal

Keep Emby resume metadata current during long-running Nordic video playback, not only when the user closes or switches away from the video player.

## What I already know

* `EmbyRepository.syncPlaybackProgress(video, positionSeconds, isPaused)` now reports `Sessions/Playing/Progress`.
* `MainActivity` already runs a 30-second periodic progress sync loop for AudiobookShelf playback.
* `VideoPlaybackState` exposes the current video, position, and playing state.
* The previous task intentionally left periodic video progress sync out of scope.

## Requirements

* Add a periodic video progress sync loop in `MainActivity` while a video is active and Emby config is ready.
* Use `EmbyRepository.syncPlaybackProgress(...)` with `isPaused = !videoPlaybackState.isPlaying`.
* Initialize the sync baseline from the greater of current player position, Emby resume position, and zero so the loop does not regress resume metadata.
* Only advance the local baseline after a successful progress sync.
* Stop the loop when the current video changes, video playback is cleared, or the Emby repository becomes unavailable.
* Ignore background periodic sync failures in the UI; do not close or reopen the video player.

## Acceptance Criteria

* [ ] `MainActivity` starts a video progress sync loop keyed by current video id and Emby repository.
* [ ] The loop reports progress every 30 seconds while the same video remains active.
* [ ] Baseline helper uses current player position over stale Emby resume position when already ahead.
* [ ] Failed periodic sync does not update the baseline or disrupt playback UI.
* [ ] Focused MainActivity helper tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Periodic video progress sync is implemented and covered by focused tests for baseline behavior.
* Emby integration spec records the close-sync plus periodic-sync contract.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Mirror the audiobook periodic sync pattern in `MainActivity`, but use video-specific state and the Emby repository. Add a small pure helper for the initial video progress baseline so regression-sensitive behavior is testable without Compose.

## Decision (ADR-lite)

Context: Close-time sync is useful but can still lose progress if the app remains open for a long session, is backgrounded, or is killed before close.

Decision: Add a 30-second periodic progress loop using the existing Emby progress endpoint and do not surface transient sync failures in the UI.

Consequences: Resume metadata becomes fresher with minimal UI complexity. If Emby rejects a progress update, playback continues and a later loop/close sync can retry.

## Out of Scope

* Changing the 30-second sync interval.
* Adding visible periodic sync error surfaces.
* Marking videos watched/unwatched separately from progress reporting.
* Adding a local offline retry queue.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/MainActivityTest.kt`.
* Relevant specs: `.trellis/spec/backend/emby-integration.md`, `.trellis/spec/backend/quality-guidelines.md`.
