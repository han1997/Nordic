# Defer Music Scrubber Seeks

## Goal

Improve music playback smoothness by preventing the music player scrubber from sending repeated seek commands while the user drags the slider.

## Requirements

* While dragging the music progress slider, update the displayed position locally.
* Call `onSeek(...)` only from `onValueChangeFinished`.
* Reset local scrub state after the seek is submitted so live playback position resumes driving the UI.
* Keep current enabled/disabled behavior and duration bounds.
* Match the established Video and Audiobook player scrubber pattern.

## Acceptance Criteria

* [x] Music slider `onValueChange` no longer invokes `onSeek`.
* [x] Music slider `onValueChangeFinished` submits the latest scrub position.
* [x] Displayed elapsed time follows the scrub position while dragging.
* [x] Existing playback controls, repeat, shuffle, queue, and lyric behavior remain unchanged.

## Definition of Done

* Kotlin compile passes.
* Unit tests pass.
* Android lint passes.
* No `.trellis/spec/` update unless a new reusable convention is discovered.
* Work commit is created before task archive and journal commits.

## Technical Approach

Reuse the `scrubPosition` state already present in `MusicPlayerScreen`. Pass a local `onPositionChange` callback to the control panel that sets `scrubPosition`, and pass an `onPositionChangeFinished` callback that seeks to the last scrubbed value and then clears local scrub state.

## Decision (ADR-lite)

**Context**: The current music slider still forwards `onValueChange` directly to the seek callback. Video and Audiobook already defer seek until slider release to avoid flooding Media3.

**Decision**: Align Music with the existing deferred seek pattern.

**Consequences**: Dragging the slider remains responsive visually while issuing a single playback seek per completed scrub gesture.

## Out of Scope

* Changing playback engine seek implementation.
* Changing skip intervals or queue behavior.
* Changing visual styling of the player controls.
* Adding instrumentation UI tests for slider gestures.

## Technical Notes

* Target code: `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`.
* Reference patterns: `VideoPlayerScreen.kt` and `AudiobookPlayerScreen.kt`.
* Relevant specs: `.trellis/spec/backend/quality-guidelines.md` already requires playback scrubbers to defer engine seeks until `onValueChangeFinished`.
* Discovery: `MusicPlayerScreen` already keeps `scrubPosition` locally, passes `onPositionChange = { scrubPosition = it }`, and calls `onSeek(...)` only from `onPositionChangeFinished`; no source change was required.
