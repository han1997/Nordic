# Optimize Video Player Recomposition Performance

## Goal

Reduce unnecessary Compose recomposition work on the video playback page by isolating the Media3 `AndroidView` surface from high-frequency playback position updates, while preserving the existing player UI and behavior.

## What I Already Know

* The user asked to execute the performance optimization discussed after the video player polish/fullscreen work.
* `VideoPlayerScreen` currently receives the whole `VideoPlaybackState`, derives timeline values, and renders the `AndroidView` in the same composable as the frequently changing controls.
* `positionSeconds` changes roughly once per second. That causes the parent composable to recompose and can call `AndroidView.update` even when only the scrubber/time labels need to change.
* The `SurfaceView` + `AspectRatioFrameLayout` approach should stay; this task is not replacing the media rendering component.
* The scrubber already follows the required pattern: local drag state and `onSeek` only from `onValueChangeFinished`.

## Assumptions

* The optimization should be behavior-preserving and visual-preserving.
* The main target is reducing unnecessary `AndroidView` update work and broad recomposition, not adding profiling tooling.
* Existing callbacks and public `VideoPlayerScreen` signature should remain compatible unless a very small internal refactor requires private helper changes.

## Requirements

* Extract the video rendering surface into a focused composable that only depends on stable rendering inputs:
  * `aspectRatioMode`
  * sanitized `videoAspectRatio`
  * `onSurfaceReady`
  * `onSurfaceDisposed`
* Keep `SurfaceView` attachment/disposal semantics unchanged.
* Move aspect-ratio-to-resize-mode mapping into a pure helper so the `AndroidView.update` block is small and testable.
* Keep the high-frequency timeline and scrubber state inside controls-related code.
* Keep existing playback controls, fullscreen controls, buffering/error/idle states, callbacks, and labels behaviorally unchanged.
* Do not introduce new dependencies or switch to `PlayerView`.

## Acceptance Criteria

* [x] `AndroidView` surface update no longer directly depends on `positionSeconds`, `durationSeconds`, `isPlaying`, `isBuffering`, or `errorMessage`.
* [x] Existing video playback callbacks still attach/detach the same `SurfaceView` lifecycle path.
* [x] Aspect ratio resize mode mapping is covered by unit tests.
* [x] Existing timeline/status helper tests still pass.
* [x] `:app:compileDebugKotlin` passes.
* [x] `:app:testDebugUnitTest` passes.
* [x] `:app:lintDebug` passes.

## Definition of Done

* Code implemented in the existing Compose/Media3 architecture.
* Focused tests added or updated for pure helper logic.
* Gradle verification gates pass sequentially on Windows.
* Work committed before task archive/journal.

## Technical Approach

* Add a private `VideoPlayerSurface` composable inside `VideoPlayerScreen.kt`.
* Move `AndroidView`, `attachedSurface`, and `DisposableEffect(attachedSurface)` into `VideoPlayerSurface`.
* Pass only `AspectRatioMode`, sanitized aspect ratio, and surface callbacks into `VideoPlayerSurface`.
* Add `internal fun resolveVideoPlayerResizeMode(aspectRatioMode: AspectRatioMode): Int` with Media3 `@OptIn(UnstableApi::class)`.
* Use the helper from `AndroidView.update`.
* Add unit tests in `VideoPlayerScreenTest`.

## Decision (ADR-lite)

**Context**: The video surface is expensive relative to text/time labels and should not be coupled to playback-position recomposition.

**Decision**: Keep the current Media3 `AspectRatioFrameLayout` + `SurfaceView`, but isolate it in a narrow-parameter composable and test the resize-mode helper.

**Consequences**: This is a low-risk performance cleanup. It reduces avoidable update work without changing rendering technology, controls, or playback engine behavior.

## Out of Scope

* Replacing `SurfaceView` or `AspectRatioFrameLayout`.
* Adding profiler instrumentation or benchmark tests.
* Changing playback controls, fullscreen behavior, gestures, subtitles, tracks, PiP, or next-episode behavior.
* Changing `VideoPlaybackEngine` state publishing cadence.

## Technical Notes

* Primary file: `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`.
* Tests: `app/src/test/java/com/nordic/mediahub/ui/VideoPlayerScreenTest.kt`.
* Relevant specs:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/spec/backend/emby-integration.md`
