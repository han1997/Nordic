# Clamp audiobook progress sync time

## Goal

Keep AudiobookShelf progress/session payloads internally consistent by clamping reported `currentTime` to the audiobook duration. The app should not send `currentTime` values greater than duration even when local playback state temporarily overshoots the known book length.

## What I Already Know

* The long-running goal asks to improve AudiobookShelf behavior against the official service contract.
* `AudiobookShelfRepository.syncProgress(...)` currently clamps progress fraction to `0.0..1.0`, but sends `currentTimeSeconds.coerceAtLeast(0)` as `currentTime`.
* Existing tests show `currentTime=150` being sent for a 100-second sample session while `progress=1.0`.
* `closeSession(...)` has the same lower-bound-only behavior for current time.

## Requirements

* Clamp progress update `currentTime` to `0..durationSeconds` where duration is coerced to at least `1`.
* Clamp session sync `currentTime` to the same safe range.
* Clamp close-session `currentTime` to `0..durationSeconds`.
* Preserve existing progress fraction behavior.
* Preserve non-negative `timeListened` behavior.
* Cover the time clamp behavior with focused unit tests.

## Acceptance Criteria

* [x] Progress update payload sends duration-clamped `currentTime`.
* [x] Session sync payload sends duration-clamped `currentTime`.
* [x] Close-session payload sends duration-clamped `currentTime`.
* [x] Negative current time still clamps to `0`.
* [x] Focused repository unit tests pass.

## Definition of Done

* Tests added or updated where behavior changes.
* Kotlin compile, unit tests, and lint pass.
* Specs or notes updated if behavior changes.
* Trellis finish workflow is run at the end of the round.

## Technical Approach

Add a small pure helper in `AudiobookShelfRepository.kt` to resolve a safe sync current time from raw current seconds and session duration. Use it in `syncProgress(...)` and `closeSession(...)`, then update `AudiobookShelfRepositoryTest` expectations and add negative-current coverage.

## Decision (ADR-lite)

**Context**: AudiobookShelf progress requests include both duration/progress and currentTime. Sending `currentTime > duration` is inconsistent even if progress is clamped to `1.0`.

**Decision**: Clamp `currentTime` at the repository boundary before sending progress, sync, or close payloads.

**Consequences**: Server resume state receives bounded values. Local playback can still clamp/resolve separately in the playback engine.

## Out of Scope

* Playback engine seek behavior.
* UI copy or layout changes.
* New AudiobookShelf endpoints.
* Changing `timeListened` semantics beyond preserving its non-negative clamp.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`.
