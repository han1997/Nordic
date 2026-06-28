# Clamp Audiobook Absolute Playback Position

## Goal

Prevent transient or stale Media3 positions from pushing AudiobookShelf playback state beyond the active audio track bounds. This keeps the audiobook player, chapter navigation, and progress sync input stable when multi-track ABS sessions report a current position outside the expected local track duration.

## What I already know

* `AudiobookPlaybackEngine` maps Media3 `currentMediaItemIndex` plus `currentPosition` into an absolute audiobook position with `resolveAudiobookAbsolutePositionSeconds`.
* Seeking already clamps absolute targets through `resolveAudiobookTrackSeekPosition`.
* Repository progress sync was already hardened to clamp values before sending them to AudiobookShelf, but the playback state can still expose an out-of-range absolute position before that layer.
* Existing playback helper tests live in `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.

## Requirements

* Clamp the local Media3 position used by `resolveAudiobookAbsolutePositionSeconds` to the current audio track duration when the current track is known.
* Continue to clamp negative local positions to zero.
* Preserve the existing fallback behavior for unknown track indexes, where the raw non-negative player position is returned.
* Add focused unit coverage for the new clamping behavior.

## Acceptance Criteria

* [ ] Known-track absolute position uses `track.startOffsetSeconds + localPositionSeconds.coerceIn(0, track.durationSeconds)`.
* [ ] Unknown-track fallback remains unchanged.
* [ ] Focused audiobook playback engine tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Tests added or updated for the affected helper.
* No behavior change outside audiobook playback position resolution.
* Relevant Trellis spec is reviewed and updated if this establishes a reusable contract.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Update the pure playback resolver rather than adding clamping in the UI or repository. This keeps the state contract correct at the source while preserving the repository-side sync clamp as defense in depth.

## Decision (ADR-lite)

Context: ABS audiobooks can have multiple audio tracks, so the app derives a single absolute position from a track index and local player offset.

Decision: Clamp only when the current track is known, because track duration is the reliable bound in that path. Leave unknown-track fallback untouched to avoid inventing bounds when the mapping failed.

Consequences: UI and sync callers receive saner state for normal multi-track playback. If Media3 reports an unknown track index, existing behavior remains compatible.

## Out of Scope

* Reordering or normalizing AudiobookShelf track lists.
* Changing chapter seek behavior.
* Changing repository sync payload clamping.
* Adding instrumentation tests.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.
* Relevant specs: `.trellis/spec/backend/audiobookshelf-integration.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/directory-structure.md`.
