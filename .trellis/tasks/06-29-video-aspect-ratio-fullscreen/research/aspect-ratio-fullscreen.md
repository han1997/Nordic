# Research: Aspect Ratio Switching and Fullscreen Mode

- **Query**: How to implement video aspect ratio switching and fullscreen mode in an Android app using Media3/ExoPlayer + Jetpack Compose
- **Scope**: Mixed (internal code analysis + external API knowledge)
- **Date**: 2026-06-29

## Findings

### 1. Media3/ExoPlayer Aspect Ratio Control

#### How ExoPlayer controls video scaling

ExoPlayer/Media3 provides two approaches to video scaling:

**Approach A: `PlayerView` / `StyledPlayerView` (media3-ui)**
- `PlayerView` wraps a `SurfaceView` or `TextureView` internally and uses `AspectRatioFrameLayout` as its container.
- The scaling mode is set via `PlayerView.setResizeMode(@ResizeMode int)`.
- `AspectRatioFrameLayout.ResizeMode` constants:
  - `RESIZE_MODE_FIT` = 0 -- fit within bounds, maintain aspect ratio, letterbox/pillarbox (default)
  - `RESIZE_MODE_FIXED_WIDTH` = 1 -- fixed width, height adjusts to aspect ratio
  - `RESIZE_MODE_FIXED_HEIGHT` = 2 -- fixed height, width adjusts to aspect ratio
  - `RESIZE_MODE_FILL` = 3 -- fill entire container, may distort (stretch)
  - `RESIZE_MODE_ZOOM` = 4 -- fill container, crop to maintain aspect ratio (crop/overscan)
- Classes live in: `androidx.media3.ui.AspectRatioFrameLayout`, `androidx.media3.ui.PlayerView`
- The dependency `androidx.media3:media3-ui:1.3.1` is already included in the project (see `app/build.gradle.kts:63`).

**Approach B: Raw SurfaceView (current app approach)**
- The app uses `AndroidView(factory = { SurfaceView(context) })` (line 94 of VideoPlayerScreen.kt) without `PlayerView`.
- ExoPlayer renders to this surface via `player.setVideoSurfaceView(surfaceView)` (line 86 of VideoPlaybackEngine.kt).
- Since there is no `AspectRatioFrameLayout` wrapping the surface, ExoPlayer renders at the surface's native size (filling the full `fillMaxSize()` Box).
- The raw ExoPlayer `Player` interface does NOT expose a `setResizeMode()` method. Resize modes are a UI-layer concern handled by `PlayerView` / `AspectRatioFrameLayout`.

#### Standard aspect ratio modes in media players

The common modes across players like VLC, mpv, and Emby/Jellyfin:

| Mode | Description | Result |
|------|-------------|--------|
| **Fit / Original** | Maintain video's native aspect ratio, fit within bounds | Letterbox/pillarbox bars |
| **Fill / Stretch** | Stretch to fill entire screen | Distortion possible |
| **Crop / Zoom / Overscan** | Scale to fill screen, crop edges to maintain aspect ratio | No bars, content clipped |
| **16:9** | Force 16:9 aspect ratio | Letterbox/crop for other sources |
| **4:3** | Force 4:3 aspect ratio | Pillarbox for widescreen |

The minimal practical set for a mobile player: **Fit, Fill (stretch), Crop (zoom)**. Fixed-ratio modes (16:9, 4:3) are secondary.

#### How to cycle through modes programmatically

Pattern (pseudocode):
```kotlin
enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    CROP("Crop")
}

fun cycleAspectRatio(current: AspectRatioMode): AspectRatioMode {
    return AspectRatioMode.entries[(current.ordinal + 1) % AspectRatioMode.entries.size]
}
```

The existing app uses this exact pattern for audiobook speed cycling: `cyclePlaybackSpeed()` in `AudiobookPlaybackEngine` (line 203-208) cycles through `AUDIOBOOK_PLAYBACK_SPEEDS` list.

### 2. Compose SurfaceView Aspect Ratio

#### Current rendering setup

The current `VideoPlayerScreen` composable (line 88-100):
```kotlin
Box(
    modifier = modifier.fillMaxSize().background(Color.Black)
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).also { view ->
                attachedSurface = view
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    // ... scrim, controls overlay
}
```

The `SurfaceView` uses `fillMaxSize()` within a `fillMaxSize()` Box. This means the video surface always fills the entire available space, and the ExoPlayer scaler will render the video content into that full-size surface. By default, ExoPlayer scales the video to fit within the surface while maintaining the original aspect ratio (equivalent to `FIT` mode).

#### Option A: Apply aspect ratio via `Modifier.aspectRatio()` on the AndroidView wrapper

This is the simplest approach and works well for "Fit" and fixed-ratio modes. The `SurfaceView` in the `AndroidView` factory is created once; you can change its layout `Modifier` reactively:

- For **Fit mode**: Apply `Modifier.aspectRatio(videoAspectRatio)` to the `AndroidView`, centering it in the parent Box. The SurfaceView will be sized to the video's aspect ratio, and ExoPlayer will render natively into it.
- For **Fill mode (stretch)**: Use `Modifier.fillMaxSize()` (current behavior). The video stretches to fill.
- For **Crop mode (zoom)**: Use `Modifier.fillMaxSize()` plus tell ExoPlayer to apply a scale transform on the SurfaceView. This requires calculating the crop scale based on video vs. container aspect ratio.

**Caveat**: `Modifier.aspectRatio()` requires knowing the video's aspect ratio at compose time. ExoPlayer exposes `Player.getVideoSize()` which returns `VideoSize(width, height, pixelWidthHeightRatio)`. You can listen via `Player.Listener.onVideoSizeChanged(VideoSize)`.

#### Option B: Configure SurfaceView layout params directly

You can update the `SurfaceView`'s layout params in the `AndroidView`'s `update` block:

```kotlin
AndroidView(
    factory = { context -> SurfaceView(context).also { attachedSurface = it } },
    update = { view ->
        // Modify view.layoutParams here based on mode
        val params = view.layoutParams
        // ... set width/height/position based on aspect ratio mode
        view.layoutParams = params
    },
    modifier = Modifier.fillMaxSize()
)
```

This gives full control over the surface dimensions within the box but is lower-level and requires manual measurement.

#### Option C: Use `AspectRatioFrameLayout` as the AndroidView factory root

You can create an `AspectRatioFrameLayout` wrapping a `SurfaceView` inside the `AndroidView` factory:

```kotlin
AndroidView(
    factory = { context ->
        AspectRatioFrameLayout(context).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            addView(SurfaceView(context).also { attachedSurface = it })
        }
    },
    update = { frameLayout ->
        frameLayout.resizeMode = when (mode) {
            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    },
    modifier = Modifier.fillMaxSize()
)
```

This is the most idiomatic approach for Media3 and leverages the well-tested `AspectRatioFrameLayout`. The class is available from `androidx.media3:media3-ui:1.3.1` (already a dependency). It handles all the math for Fit/Fill/Crop automatically.

**Key detail**: `AspectRatioFrameLayout` also needs `setAspectRatio(float)` to be called when the video size becomes known. By default it assumes 16:9. You update it in `onVideoSizeChanged`:

```kotlin
frameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height.toFloat() * videoSize.pixelWidthHeightRatio)
```

### 3. Fullscreen Mode in Android Compose Apps

#### Current edge-to-edge setup

The app already calls `enableEdgeToEdge()` in `MainActivity.onCreate()` (line 210). This means:
- The app draws edge-to-edge by default
- System bars (status bar, navigation bar) are transparent
- Content draws behind the system bars

`VideoPlayerScreen` currently uses `statusBarsPadding()` and `navigationBarsPadding()` (line 125) to inset the control column. To enter fullscreen, these paddings would be removed and the system bars would be hidden entirely.

#### Hiding system bars (immersive/leanback mode)

**API: `WindowInsetsControllerCompat`** (from `androidx.core:core-ktx`, already a dependency at version 1.12.0)

```kotlin
val window = (context as ComponentActivity).window
val controller = WindowInsetsControllerCompat(window, window.decorView)

// Hide both status bar and navigation bar
controller.hide(WindowInsetsCompat.Type.systemBars())

// Show them again
controller.show(WindowInsetsCompat.Type.systemBars())

// Set behavior: bars reappear temporarily on swipe (sticky immersive)
controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

// Alternative: bars do not reappear until explicitly shown
// controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
```

`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` = sticky immersive mode (bars appear temporarily then auto-hide). This is the recommended behavior for video players.

#### Toggling fullscreen

The typical toggle pattern in a Compose app:

```kotlin
var isFullscreen by remember { mutableStateOf(false) }
val context = LocalContext.current

// Update system bars when fullscreen state changes
LaunchedEffect(isFullscreen) {
    val activity = context as ComponentActivity
    val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    if (isFullscreen) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
```

**Important**: When fullscreen is active, remove `statusBarsPadding()` and `navigationBarsPadding()` from the controls, so they can use the full screen area. When not fullscreen, re-add these paddings.

#### Interaction with `enableEdgeToEdge()`

Since the app already uses `enableEdgeToEdge()`, hiding the system bars via `WindowInsetsControllerCompat` works cleanly. The `enableEdgeToEdge()` call sets up transparent bars; `WindowInsetsControllerCompat.hide()` then hides them entirely. There is no conflict.

One consideration: when the video player is closed (`onClose`), the fullscreen state must be reset (show bars again). This should happen in `closeVideoPlayback()` in `MainActivity.kt` (line 439-457).

#### Screen orientation for fullscreen

For a fullscreen video player, it is typical to force landscape orientation:

```kotlin
val activity = context as ComponentActivity
activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
// On exit:
activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
```

This is independent of the system bar hiding. The `Activity` class and `ActivityInfo` are standard Android APIs -- no extra dependencies needed. However, this adds complexity (orientation change causes Activity recreation unless `configChanges` is handled). The manifest currently has `android:windowSoftInputMode="adjustResize"` but no `configChanges` for orientation.

### 4. Common UX Patterns

#### Aspect ratio button in media players

**Yamby / Emby client apps**:
- Aspect ratio button is typically in the playback controls row
- Icon: a rectangle with corner arrows or a "fit/zoom" icon
- Tapping cycles: Fit -> Fill -> Crop -> Fit
- Some Emby clients use a text label like "Fit" / "Fill" / "Zoom"

**VLC for Android**:
- Aspect ratio button in the overlay controls
- Icon: a rectangle with bidirectional arrows (resize icon)
- Cycles through: Original -> Fit Screen -> 16:9 -> 4:3 -> 2.35:1 -> Original
- Label shown briefly when switching

**mpv / mpv-android**:
- No dedicated button; uses gesture (pinch to zoom)
- Mode options: "Contain" (fit), "Cover" (crop/zoom), "Stretch"

**Common design decisions**:
- Icon alternatives: `Icons.AutoMirrored.Fullscreen` (for fullscreen), a custom aspect ratio icon (rectangle with arrows), or text label like "Fit/Fill/Crop"
- Placement: In the controls row alongside play/pause/skip, or in the top bar area
- Cycle order: Fit -> Crop -> Fill -> (repeat) -- "Fit" is almost always the default since it preserves the original frame

**Standard cycle order** (most common across players):
1. **Fit** (default) -- preserve aspect ratio, fit within container
2. **Crop / Zoom** -- fill container, crop edges
3. **Fill / Stretch** -- fill container, stretch

Some players omit "Fill/Stretch" since it distorts. A two-mode cycle (Fit <-> Crop) is also common.

#### Fullscreen toggle in media players

**Icon**: `Icons.Default.Fullscreen` (expand to fullscreen) / `Icons.Default.FullscreenExit` (exit fullscreen)

**Placement options**:
- Top-right of the player controls (most common)
- In the controls row (alongside play/pause)

**Double-tap to toggle**: Many players (VLC, YouTube) use double-tap on the video surface to toggle fullscreen. This requires a `pointerInput` modifier on the surface with `detectTapGestures(onDoubleTap = { isFullscreen = !isFullscreen })`.

**Back button**: When fullscreen, pressing back should exit fullscreen first (then back again closes the player). The app already has a `BackHandler` for `showVideoPlayer` (line 712-714 of MainActivity.kt).

### 5. Implementation Approach for This App

#### VideoPlaybackEngine aspect ratio control

The `VideoPlaybackEngine` (app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt) currently does NOT expose any aspect ratio or video size info:

- It uses `player.setVideoSurfaceView(surfaceView)` (line 86) -- raw surface, no `PlayerView`.
- No `onVideoSizeChanged` listener is registered on the `Player.Listener` (line 56-79).
- The `VideoPlaybackState` data class (line 38-45) has no field for aspect ratio mode or video dimensions.

**What needs to be added to the engine**:

1. **Video size tracking**: Add `Player.Listener.onVideoSizeChanged` callback to capture `VideoSize(width, height, pixelWidthHeightRatio)`.
2. **Aspect ratio mode field** in `VideoPlaybackState`: Add an `aspectRatioMode` enum field (default = FIT).
3. **`cycleAspectRatio()` method** on `VideoPlaybackEngine** -- follow the same pattern as `AudiobookPlaybackEngine.cyclePlaybackSpeed()` (line 203-208).
4. **Video dimensions field** in `VideoPlaybackState`: Add `videoWidth: Int = 0` and `videoHeight: Int = 0` (or a `videoAspectRatio: Float = 16f/9f`) so the UI can size the `AspectRatioFrameLayout`.

The aspect ratio mode is a **UI concern**, not a player concern. However, since the app's architecture puts all playback state in `VideoPlaybackState` (following the pattern of `AudiobookPlaybackState.playbackSpeed`), adding the mode to the state/engine is consistent.

#### Where to place aspect ratio and fullscreen buttons in VideoPlayerScreen

The current `VideoPlayerControls` composable (line 280-384) has this layout:
- Slider (seek bar)
- Time row (current / duration)
- Button row: `[-10] [||/>] [+30]`
- Optional scrub hint text

The controls are inside a `Surface` with `RoundedCornerShape(28.dp)` at the bottom of the screen.

**Placement options for new buttons**:

**Option A: Add to the existing button row**. The current row is `[seekBack] [playPause] [seekForward]`. Adding aspect ratio and fullscreen buttons would make it: `[AR] [seekBack] [playPause] [seekForward] [FS]`. This uses the existing `VideoPlayerChromeButton` component and pattern.

**Option B: Add a secondary row above/below the existing controls**. This avoids making the main row too wide, but adds visual complexity.

**Option C: Place fullscreen in the top bar** (alongside the "X" close button). This is common in many players. The top bar `VideoPlayerTopBar` (line 178-223) currently has `[X] [title/subtitle] [statusPill]`. Fullscreen could go to the right of the pill or as a fourth element.

The most aligned with the existing code patterns is **Option A** (add to existing button row) for aspect ratio and **Option C** (top bar) for fullscreen. However, the screen is already relatively crowded.

The app uses `VideoPlayerChromeButton` (line 386-444) for all player buttons. These are text-based circular buttons. The aspect ratio button could show "Fit"/"Fill"/"Crop" as text (like the audiobook speed button shows "1x"/"1.5x"), and fullscreen could show the "[]" icon text or similar.

#### State additions

**`VideoPlaybackState`** (line 38-45 of VideoPlaybackEngine.kt):
```kotlin
data class VideoPlaybackState(
    val video: VideoItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
    // NEW:
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val videoAspectRatio: Float = 16f / 9f  // width/height * pixelWidthHeightRatio
)
```

**`VideoPlayerScreen`** (line 48-58):
- Add `onCycleAspectRatio: () -> Unit` callback (same pattern as `onPlayPause`)
- Add `onToggleFullscreen: () -> Unit` callback
- Add `isFullscreen: Boolean` parameter to control system bar visibility and padding

**`MainActivity.kt`** (line 740-753):
- Add `isFullscreen` state variable
- Add `onCycleAspectRatio` handler calling `videoPlaybackEngine.cycleAspectRatio()`
- Add `onToggleFullscreen` handler toggling `isFullscreen` state and system bars
- Reset fullscreen state in `closeVideoPlayback()` (line 439-457)
- Add `BackHandler` for fullscreen exit (exit fullscreen before closing player)

#### SurfaceView wrapper approach (recommended)

The most maintainable approach uses `AspectRatioFrameLayout` as the `AndroidView` factory root:

```kotlin
AndroidView(
    factory = { context ->
        AspectRatioFrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            val surface = SurfaceView(context)
            addView(surface)
            attachedSurface = surface
        }
    },
    update = { frameLayout ->
        frameLayout.resizeMode = when (state.aspectRatioMode) {
            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        frameLayout.setAspectRatio(state.videoAspectRatio)
    },
    modifier = Modifier.fillMaxSize()
)
```

The `update` block recomposes when `state.aspectRatioMode` or `state.videoAspectRatio` changes. The `AspectRatioFrameLayout` class is from `androidx.media3.ui` (already on the classpath).

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt` | Video player UI composable -- uses raw `AndroidView(factory = { SurfaceView })` at line 93-100, no aspect ratio/fullscreen support |
| `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt` | Video playback engine -- `VideoPlaybackState` at line 38-45 (no AR mode), `attachSurface` at line 85-87, `Player.Listener` at line 56-79 (no `onVideoSizeChanged`) |
| `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt` | `VideoItem` data class at line 27-45 (has id, title, streamUrl, etc., no aspect ratio info from server) |
| `app/src/main/java/com/nordic/mediahub/MainActivity.kt` | Main activity -- `enableEdgeToEdge()` at line 210, `VideoPlayerScreen` call at line 740-753, `closeVideoPlayback` at line 439-457 |
| `app/src/main/AndroidManifest.xml` | Activity has no `configChanges` for orientation |
| `app/build.gradle.kts` | Media3 dependencies at line 59-63, `media3-ui:1.3.1` already included |
| `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt` | Reference pattern: `cyclePlaybackSpeed()` at line 203-208, `AudiobookPlaybackState.playbackSpeed` field |

### Code Patterns

1. **State cycling pattern**: See `AudiobookPlaybackEngine.cyclePlaybackSpeed()` (line 203-208): cycles through a list, publishes state. Same pattern should be used for aspect ratio cycling.

2. **Button text pattern**: `AudiobookPlayerScreen` uses `formatPlaybackSpeed(state.playbackSpeed)` to generate "1x", "1.5x" text for the button. A similar `formatAspectRatioMode(state.aspectRatioMode)` can generate "Fit", "Fill", "Crop" text.

3. **Chrome button pattern**: `VideoPlayerChromeButton` (VideoPlayerScreen.kt line 386-444) accepts `text`, `colorScheme`, `primary`, `enabled`, `size`, `onClick`. Neither aspect ratio nor fullscreen buttons need `primary = true`.

4. **Surface lifecycle pattern**: `DisposableEffect(attachedSurface)` at VideoPlayerScreen.kt line 76-86 handles surface attach/detach. Switching to `AspectRatioFrameLayout` does not change this pattern -- the `SurfaceView` is a child of the frame layout.

### External References

- [Media3 AspectRatioFrameLayout docs](https://developer.android.com/reference/androidx/media3/ui/AspectRatioFrameLayout) -- ResizeMode constants, setAspectRatio() API
- [Media3 PlayerView docs](https://developer.android.com/reference/androidx/media3/ui/PlayerView) -- PlayerView.setResizeMode() delegates to AspectRatioFrameLayout
- [WindowInsetsControllerCompat docs](https://developer.android.com/reference/androidx/core/view/WindowInsetsControllerCompat) -- hide/show system bars, BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
- [enableEdgeToEdge docs](https://developer.android.com/develop/ui/compose/layout/edge-to-edge) -- already used in the app, compatible with WindowInsetsController
- [Player.Listener.onVideoSizeChanged](https://developer.android.com/reference/androidx/media3/common/Player.Listener#onVideoSizeChanged(androidx.media3.common.VideoSize)) -- provides VideoSize(width, height, pixelWidthHeightRatio)
- Media3 version: 1.3.1 (current in build.gradle.kts). Latest stable is 1.7.x but 1.3.1 has all needed APIs.

### Related Specs

- `.trellis/spec/backend/emby-integration.md` -- Emby integration spec (relevant for VideoItem data flow)

## Caveats / Not Found

1. **No existing aspect ratio or fullscreen code**: The app has zero implementation for either feature currently. The `SurfaceView` fills the entire screen and ExoPlayer renders at default FIT behavior.

2. **`VideoItem` does not carry aspect ratio info from Emby**: The Emby API response (`EmbyItemDto`) likely includes `AspectRatio` or stream info, but the current `toVideoItem()` mapper (EmbyRepository.kt line 215-237) does not extract it. The aspect ratio must be detected at playback time via `onVideoSizeChanged`.

3. **No orientation change handling in manifest**: `AndroidManifest.xml` does not declare `configChanges` for the activity. If fullscreen forces landscape orientation via `requestedOrientation`, the Activity will be recreated. This can be avoided by either: (a) adding `android:configChanges="orientation|screenSize|smallestScreenSize"` to the activity, or (b) not forcing orientation and only hiding system bars.

4. **The `AspectRatioFrameLayout` requires `UnstableApi` opt-in**: It is annotated with `@UnstableApi`. The audiobook engine already uses `@OptIn(UnstableApi::class)` (AudiobookPlaybackEngine.kt line 50), so this pattern exists in the codebase.

5. **`Modifier.aspectRatio()` vs `AspectRatioFrameLayout`**: `Modifier.aspectRatio()` on the Compose wrapper only controls the layout size of the `AndroidView`. It does NOT control how ExoPlayer scales the video content within the `SurfaceView`. Using `AspectRatioFrameLayout` inside the factory is more correct because it handles both the layout and the video scaling via `resizeMode`.

6. **Double-tap for fullscreen**: If implemented, the double-tap gesture must be added to the outer `Box` container, not the `AndroidView`, because the `SurfaceView` consumes touch events. A transparent overlay `Box` with `pointerInput` is needed.

7. **Fullscreen state persistence**: The `isFullscreen` boolean should be managed at the `MainActivity` level (where `showVideoPlayer` is managed), not inside `VideoPlayerScreen`, because it needs to interact with the Activity's window and system bars. When the video player is closed, fullscreen must be cleared.
