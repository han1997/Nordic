# Video Aspect Ratio Switching and Fullscreen Playback

## Goal

Add video aspect ratio mode switching (Fit/Crop/Fill) and fullscreen playback mode to the video player, so users can control how the video fills the screen and enjoy an immersive landscape fullscreen experience.

## What I Already Know

* User wants aspect ratio switching and fullscreen playback on the video player page.
* Current `VideoPlayerScreen` uses a raw `SurfaceView` inside `AndroidView` with `fillMaxSize()`. No aspect ratio control exists.
* `VideoPlaybackState` has no aspect ratio mode or video dimensions fields.
* `VideoPlaybackEngine` has no `onVideoSizeChanged` listener and no aspect ratio cycling method.
* `media3-ui:1.3.1` is already a dependency. `AspectRatioFrameLayout` is available.
* The app already calls `enableEdgeToEdge()`. `WindowInsetsControllerCompat` can hide system bars.
* The app uses a state-driven cycling pattern for speed (`AudiobookPlaybackEngine.cyclePlaybackSpeed()`).
* The audiobook engine already uses `@OptIn(UnstableApi::class)`.

## Assumptions

* Aspect ratio can be detected at playback time via `Player.Listener.onVideoSizeChanged` — no server-side data needed.
* `AspectRatioFrameLayout` wrapping the `SurfaceView` inside the `AndroidView` factory is the correct approach for both layout and ExoPlayer scaling.
* Fullscreen state is managed at `MainActivity` level because it controls the Activity window and system bars.
* Forcing landscape in fullscreen requires adding `configChanges` to the Manifest to avoid Activity recreation.

## Open Questions

* None.

## Requirements (Evolving)

* Add an `AspectRatioMode` enum: `FIT`, `CROP`, `FILL`, with cycling order FIT → CROP → FILL → FIT.
* Add `aspectRatioMode` and `videoAspectRatio` fields to `VideoPlaybackState` (default 16:9).
* Add `onVideoSizeChanged` to `VideoPlaybackEngine`'s `Player.Listener` to detect video dimensions and compute `videoAspectRatio`.
* Add `cycleAspectRatio()` method to `VideoPlaybackEngine` following the same pattern as `AudiobookPlaybackEngine.cyclePlaybackSpeed()`.
* Replace the raw `SurfaceView` in `VideoPlayerScreen` with an `AspectRatioFrameLayout` wrapping a `SurfaceView`, using the `update` block to apply `resizeMode` and `setAspectRatio()`.
* Add aspect ratio button in the controls row: text label shows current mode ("Fit"/"Crop"/"Fill"), uses `VideoPlayerChromeButton`.
* Add fullscreen toggle: button in the controls row, toggles `isFullscreen` state.
* Fullscreen mode: hide status bar and navigation bar via `WindowInsetsControllerCompat` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, force landscape orientation via `requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`.
* Normal mode: show system bars, unset forced orientation (`SCREEN_ORIENTATION_UNSPECIFIED`), apply `statusBarsPadding()`/`navigationBarsPadding()`.
* When fullscreen, add a `BackHandler` that exits fullscreen first (before the player-close BackHandler).
* Reset fullscreen state when closing the video player in `closeVideoPlayback()`.
* Add `android:configChanges="orientation|screenSize|smallestScreenSize"` to the Activity in `AndroidManifest.xml`.

## Acceptance Criteria (Evolving)

- [x] Aspect ratio cycles through Fit → Crop → Fill on button tap
- [x] `AspectRatioFrameLayout` correctly applies `resizeMode` for each mode
- [x] Video size is detected at playback time and updates `videoAspectRatio`
- [x] Fullscreen hides system bars with sticky immersive behavior
- [x] Fullscreen forces landscape orientation
- [x] Exiting fullscreen restores system bars and unsets forced orientation
- [x] Back gesture exits fullscreen before closing the player
- [x] Closing the player resets fullscreen state
- [x] `:app:compileDebugKotlin` passes
- [x] `:app:testDebugUnitTest` passes
- [x] `:app:lintDebug` passes
- [x] `:app:assembleDebug` passes

## Definition of Done

* Code implemented in existing Compose/Media3 architecture.
* Helper tests added or updated for aspect ratio cycling logic.
* Gradle verification gates pass sequentially on Windows.
* Work committed and task archived.

## Research References

* `.trellis/tasks/06-29-video-aspect-ratio-fullscreen/research/aspect-ratio-fullscreen.md` — Media3 AspectRatioFrameLayout, WindowInsetsControllerCompat, UX patterns, SurfaceView wrapper approach, engine state additions.

## Feasible Approaches

**Approach A: AspectRatioFrameLayout + Fullscreen with Landscape (Recommended)**

* Use `AspectRatioFrameLayout` as the `AndroidView` factory root wrapping `SurfaceView`. Apply `resizeMode` and `setAspectRatio()` in the `update` block. For fullscreen, hide system bars + force landscape. Add `configChanges` to Manifest.
* Pros: Most idiomatic Media3 approach. Fullscreen with landscape is the expected mobile video player behavior.
* Cons: Requires Manifest change. Orientation forcing adds some complexity in state management.

**Approach B: Compose Modifier + Fullscreen without Landscape**

* Use `Modifier.aspectRatio()` on the `AndroidView` wrapper for Fit mode and `fillMaxSize()` for Crop/Fill. For fullscreen, only hide system bars without forcing landscape.
* Pros: No Manifest change, simpler.
* Cons: `Modifier.aspectRatio()` only controls the layout slot, not ExoPlayer's internal scaling — video fitting may not work correctly. No landscape is a lesser fullscreen experience.

## Decision (ADR-lite)

**Context**: Users need aspect ratio control and fullscreen playback. The question is whether to use `AspectRatioFrameLayout` (Media3-native) or Compose modifiers, and whether to force landscape in fullscreen.

**Decision**: Use Approach A — `AspectRatioFrameLayout` wrapping `SurfaceView` for correct video scaling, plus fullscreen with forced landscape. This matches user preference and provides the best media player UX.

**Consequences**: Requires `@OptIn(UnstableApi::class)` on the `AndroidView` factory (pattern exists). Requires Manifest `configChanges` addition. Fullscreen state must be carefully reset on player close and back navigation.

## Technical Notes

* Primary targets: `VideoPlayerScreen.kt`, `VideoPlaybackEngine.kt`, `VideoPlaybackState` (in VideoPlaybackEngine.kt), `MainActivity.kt`, `AndroidManifest.xml`.
* `AspectRatioFrameLayout` is from `media3-ui:1.3.1` (already a dependency).
* `AspectRatioFrameLayout.setAspectRatio()` requires `@UnstableApi` opt-in.
* `cycleAspectRatio()` follows `AudiobookPlaybackEngine.cyclePlaybackSpeed()` pattern.
* `onVideoSizeChanged` callback provides `VideoSize(width, height, pixelWidthHeightRatio)`.
* `formatAspectRatioMode()` helper follows `formatPlaybackSpeed()` pattern.
* `WindowInsetsControllerCompat` from `androidx.core:core-ktx` (already a dependency).
* `requestedOrientation` is available on `ComponentActivity` (no extra dependency).

## Out of Scope

* Double-tap to toggle fullscreen (gesture overlay complexity — keeps for future).
* Fixed aspect ratio modes (16:9, 4:3) — three modes (Fit/Crop/Fill) are sufficient for MVP.
* Server-side aspect ratio from Emby metadata — detect at playback time instead.
* Picture-in-Picture (PiP) mode.
