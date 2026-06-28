# Clamp Zero-Duration Audiobook Sync Current Time

## Goal

Keep AudiobookShelf progress/session sync payloads from reporting a positive `currentTime` when the playback session duration is zero or unknown. The sync payload may still need a non-zero duration value for progress math, but the current time should clamp against the actual session duration contract.

## What I already know

* `resolveAudiobookSyncCurrentTimeSeconds` currently clamps against `durationSeconds.coerceAtLeast(1)`.
* `syncProgress` and `closeSession` pass a floored duration into that helper, so `durationSeconds = 0` can allow `currentTime = 1`.
* The AudiobookShelf integration spec says progress/session/close `currentTime` must clamp to `0..durationSeconds`.
* Existing repository tests cover over-duration, negative current time, and non-2xx progress failures.

## Requirements

* Clamp sync/close `currentTime` against the actual session duration, not the safe payload duration denominator.
* Preserve a safe positive payload duration for progress fraction and ABS request fields where the repository already uses it.
* For zero or negative session duration, send `currentTime = 0.0`.
* Preserve existing clamping for negative current times and over-duration positive current times.
* Add focused unit coverage for zero-duration sync and close payloads.

## Acceptance Criteria

* [ ] `resolveAudiobookSyncCurrentTimeSeconds(currentTimeSeconds = 10, durationSeconds = 0)` returns `0`.
* [ ] `syncAndCloseSession` with `durationSeconds = 0` sends `currentTime: 0.0` for progress, session sync, and close requests.
* [ ] Existing over-duration and negative-current sync behavior still passes.
* [ ] Focused AudiobookShelf repository tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Repository helper and sync/close call sites are updated.
* Regression tests cover zero-duration behavior.
* AudiobookShelf spec is reviewed and updated if the zero-duration payload convention needs explicit capture.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Keep the repository's safe `duration = session.durationSeconds.coerceAtLeast(1)` for request `duration` fields and progress division, but compute `safeCurrentTime` with the raw `session.durationSeconds`. This preserves stable payload math while honoring the current-time contract.

## Decision (ADR-lite)

Context: AudiobookShelf can expose incomplete or unknown duration data. Dividing progress by zero is unsafe, but reporting a positive current time for a zero-duration session is also inaccurate.

Decision: Separate the current-time clamp bound from the request duration denominator.

Consequences: Payloads remain well-formed, while current time no longer advances beyond an unknown/zero session duration.

## Out of Scope

* Changing playback session mapping from AudiobookShelf DTOs.
* Changing periodic sync cadence.
* Changing typed exception behavior.
* Adding instrumentation tests.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Relevant specs: `.trellis/spec/backend/audiobookshelf-integration.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/directory-structure.md`.
