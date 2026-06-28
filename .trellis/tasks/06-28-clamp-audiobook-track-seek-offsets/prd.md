# Clamp Audiobook Track Seek Offsets

## Goal

Make AudiobookShelf playback seeking more robust by clamping track-local seek offsets when converting absolute audiobook progress to Media3 track positions.

## Requirements

* Resolve an absolute audiobook position to a Media3 track index plus track-local offset.
* Clamp negative absolute positions to the first track at offset `0`.
* Clamp positions beyond the selected track duration to that track's duration.
* Keep normal in-range positions mapped to the correct track and local offset.
* Keep existing chapter navigation, skip, speed, and progress sync behavior unchanged.

## Acceptance Criteria

* [x] Seeking to an in-range absolute position chooses the expected track and local offset.
* [x] Seeking before the audiobook start chooses track index `0`, offset `0`.
* [x] Seeking beyond the final track duration chooses the final track and clamps to its duration.
* [x] Empty track lists are handled without crashing.
* [x] Focused playback unit tests cover the resolver.

## Definition of Done

* `:app:compileDebugKotlin` passes.
* `:app:testDebugUnitTest` passes.
* `:app:lintDebug` passes.
* AudiobookShelf spec updated if this becomes a reusable playback contract.
* Work commit is created before task archive and journal commits.

## Technical Approach

Extract the track seek calculation into an internal pure helper, for example `resolveAudiobookTrackSeekPosition(...)`, and have `seekToTrackPosition(...)` use that helper before calling `controller.seekTo(index, offsetMillis)`.

Add unit tests in `AudiobookPlaybackEngineTest` for in-range, negative, beyond-last-track, and empty-track behavior.

## Decision (ADR-lite)

**Context**: AudiobookShelf sessions expose progress as absolute audiobook time, while Media3 seeks into a list of track media items using a track index and local offset. The current engine chooses the last track whose start offset is before the absolute time, but it does not clamp the local offset to the track duration.

**Decision**: Normalize the absolute-to-track mapping in a pure helper and clamp local offsets into `0..track.durationSeconds`.

**Consequences**: Resume and scrub behavior becomes more defensive when server progress or local state is slightly beyond the final media track, without changing normal playback flow.

## Out of Scope

* Changing AudiobookShelf repository API calls or DTO mapping.
* Changing progress sync payloads.
* Changing UI controls or chapter navigation copy.
* Adding instrumentation playback tests.

## Technical Notes

* Target code: `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`.
* Target tests: `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md` requires absolute audiobook positions for scrubber, skip, chapter navigation, and progress sync.
