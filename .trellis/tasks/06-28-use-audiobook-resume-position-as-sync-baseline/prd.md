# Use Audiobook Resume Position As Sync Baseline

## Goal

Prevent periodic AudiobookShelf session sync from over-reporting `timeListened` after starting a resumed audiobook. The first sync loop currently seeds `lastSyncedPosition` from playback UI state, which can briefly still be `0` even when the ABS playback session starts at a non-zero resume point.

## What I already know

* `MainActivity` owns the periodic AudiobookShelf sync loop.
* The sync loop computes `deltaSeconds = (currentPosition - lastSyncedPosition).coerceAtLeast(0)` and sends that as `timeListened`.
* `AudiobookPlaybackSession` already carries `startTimeSeconds` and `currentTimeSeconds` from ABS playback session startup.
* `AudiobookPlaybackEngine.play(...)` can publish state before the UI-observed position catches up to the session resume offset.

## Requirements

* Initialize the periodic sync baseline from the greater of current playback state and ABS session resume/current time.
* Keep negative or missing session values from producing a negative baseline.
* Keep subsequent sync behavior unchanged: on each successful sync, update the baseline to the current absolute position.
* Add focused unit coverage for the baseline resolver.

## Acceptance Criteria

* [ ] A resumed session with state position `0` and `startTimeSeconds = 120` uses baseline `120`.
* [ ] If playback state is already ahead of the session resume point, use the playback state position.
* [ ] Negative values clamp to baseline `0`.
* [ ] Focused unit tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Baseline resolver is implemented and used by the periodic sync loop.
* Unit tests cover resume, already-advanced, and negative-value cases.
* AudiobookShelf spec documents that `timeListened` deltas are measured from the session resume baseline.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Add a small pure `internal` helper in the app shell near the sync loop, then replace the initial `lastSyncedPosition` assignment. This avoids changing repository payload handling or playback engine state semantics.

## Decision (ADR-lite)

Context: ABS session sync needs both absolute `currentTime` and incremental `timeListened`. On resumed books, those are different concepts; the first delta must not count previously listened time.

Decision: Seed the delta baseline with the best known session resume/current time when playback state has not caught up yet.

Consequences: Periodic sync continues to use absolute positions for `currentTime`, while `timeListened` reflects only newly listened time in the current app session.

## Out of Scope

* Changing periodic sync cadence.
* Changing repository sync payload validation.
* Changing playback engine seek behavior.
* Adding instrumentation tests.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Relevant model: `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfModels.kt`.
* Relevant specs: `.trellis/spec/backend/audiobookshelf-integration.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/directory-structure.md`.
