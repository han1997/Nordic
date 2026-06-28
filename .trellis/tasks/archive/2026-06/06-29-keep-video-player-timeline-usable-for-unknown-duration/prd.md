# Keep Video Player Timeline Usable for Unknown Duration

## Goal

Keep the video player timeline usable when Emby or Media3 does not yet know the stream duration. The player should show the real current position and allow the slider range to grow with playback instead of clamping everything to one second.

## What I Already Know

* `VideoPlaybackEngine` already treats non-positive duration as unknown for relative seek, allowing skip-forward from the current position.
* `VideoPlayerScreen` currently computes `safeDuration = durationSeconds.coerceAtLeast(1)` and clamps `positionSeconds` into `0..safeDuration`.
* For unknown-duration streams, a real position like 40 seconds is therefore displayed and scrubbed as 1 second.

## Requirements

* For known positive duration, keep the slider range bounded by the greater of duration and current position.
* For unknown or non-positive duration, make the slider maximum grow to at least the current playback position and at least 1 second.
* Do not clamp the displayed position below the real non-negative playback position for unknown-duration streams.
* Show an unknown-duration label instead of `0:00` when duration is not known.
* Add focused unit tests for timeline range and duration labels.

## Acceptance Criteria

* [x] Unknown duration with position 40 resolves slider max to at least 40 and visible position to 40.
* [x] Unknown duration with position 0 still has a non-empty slider range.
* [x] Known duration keeps normal bounded behavior.
* [x] Unknown duration label is `--:--`.

## Definition of Done

* Tests added or updated where appropriate.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* Emby spec updated if this clarifies video unknown-duration playback behavior.

## Technical Approach

Add small pure helpers in `VideoPlayerScreen.kt` for timeline value calculation and duration label formatting, use them in the Compose screen, and test them from a UI unit test.

## Decision (ADR-lite)

**Context**: Unknown-duration streams still have meaningful current positions and seek targets. A one-second slider makes skip/scrub behavior incoherent after playback progresses.

**Decision**: Treat the slider maximum as dynamic for unknown duration, growing with the current position, while keeping known-duration videos bounded.

**Consequences**: The UI remains usable before duration metadata is available without changing Media3 playback behavior.

## Out of Scope

* Changing video engine seek math.
* Changing Emby progress payloads.
* Adding chapter markers or thumbnails to the video scrubber.

## Technical Notes

* Relevant implementation: `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`
* Relevant tests: `app/src/test/java/com/nordic/mediahub/ui/VideoPlayerScreenTest.kt`
* Relevant spec: `.trellis/spec/backend/emby-integration.md`
