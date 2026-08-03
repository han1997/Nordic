# Video Interface Logic Update

## Goal

Align the video tab's browsing, detail, and player chrome behavior with mainstream video apps (Emby/Plex/Infuse/Netflix): content-first hierarchy, predictable resume/play affordances, and consistent player chrome. Keep existing Emby catalog loading and Media3 playback infrastructure intact.

## What I Already Know

* User asked to modify the video interface logic and display so it matches mainstream expectations.
* The app is an Android Compose media hub for music, audiobooks, and video.
* Video browsing lives in `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`.
* Video cards, shelves, search, filters, loading, and empty states live in `VideoBrowseComponents.kt`.
* Video details live in `VideoDetailScreen.kt`.
* Full-screen playback UI lives in `VideoPlayerScreen.kt`, with gestures in `VideoPlayerGestures.kt`.
* Video domain display logic lives in `VideoScreenLogic.kt`, with focused tests in `VideoScreenTest.kt` and `VideoPlayerScreenTest.kt`.
* Recent commits already added continue-watching 16:9 cards, backdrop detail hero, player chrome auto-hide, gestures, and vector icons.
* Source files are valid UTF-8. The "garbled strings" suspicion in earlier notes was a console-codec display artifact, NOT a file encoding problem — no string repair work is needed.

## Requirements

### Browsing layer
* Library selector chips render ABOVE spotlight shelves (currently below), so users switch library context first — matches Emby/Plex/Infuse top-bar placement.
* Search collapses to an icon button by default; tapping it expands the search field inline. The always-visible `OutlinedTextField` is removed so the first view reads content-first.
* A visible "全部 N 项" divider/affordance sits between spotlight shelves and the raw catalog grid, so the jump from editorial rails to full grid is explicit.

### Detail layer
* Detail hero primary action adapts to resume state:
  * When `playbackPositionSeconds > 0 && !isPlayed`, primary button reads "继续从 HH:MM:SS 播放" and seeks to resume position on tap; a secondary "从头播放" action starts at 0.
  * Otherwise the primary button reads "播放" and starts at 0 (current behavior).
* Series detail episode list gains "未看 / 全部" filter chips above the episode rows. Default is "全部". Filter narrows rows by `isPlayed`.

### Player chrome layer
* Player controls start visible on first open of a video and auto-hide via the existing 4s `LaunchedEffect` — so first-time users see that chrome exists before it fades. Currently `controlsVisible` starts `false`, hiding the chrome until a tap the user doesn't know to make.
* Player status pill text is localized to Chinese, consistent with the rest of the app:
  * `Buffering` → "缓冲中"
  * `Idle` → "暂无视频"
  * `Issue` → "播放异常"
  * The center messages ("No video loaded" / "Select a video…", "Playback issue", "Buffering" / "Preparing the stream.") are localized to Chinese as well.
* Existing gesture behavior (tap toggle, double-tap skip ±10/30s, horizontal scrub, pinch aspect-ratio cycle) is preserved.

## Acceptance Criteria

* [ ] Library selector chips render above spotlight shelves on the video tab when libraries are loaded.
* [ ] Search field is hidden by default behind a search icon; expanding it reveals the field; collapsing restores the content-first view.
* [ ] A "全部 N 项" divider is visible between spotlight sections and the raw catalog grid.
* [ ] Detail hero shows "继续从 HH:MM:SS 播放" primary + "从头播放" secondary when the item has a resume position; shows just "播放" otherwise.
* [ ] Series detail shows "未看 / 全部" filter chips; selecting "未看" hides played episodes; "全部" restores them.
* [ ] Player controls are visible on first open of a video and auto-hide after ~4s during playback (existing auto-hide preserved).
* [ ] Player status pill and center messages render in Chinese.
* [ ] Existing gesture behavior (tap/double-tap/scrub/pinch) unchanged.
* [ ] Existing video logic tests pass; new/updated tests cover: resume-vs-restart button resolution, episode unwatched filter, player initial controls visibility, player status text localization.
* [ ] No regression in catalog refresh / config-change / filter / search logic.

## Definition of Done

* Tests added or updated where logic changes (VideoScreenLogic, VideoDetailScreen, VideoPlayerScreen helpers).
* Gradle unit tests for affected modules pass: `.\gradlew.bat :app:testDebugUnitTest --no-daemon`.
* Lint passes: `.\gradlew.bat :app:lintDebug --no-daemon`.
* UI changes comply with `DESIGN.md`: content-first, restrained chrome, no nested decorative card layouts, flat-at-rest cards, pill geometry, alpha-as-depth.
* No backend/API refactors; no Media3 core changes.
* Resume position formatting reuses existing `formatLongDuration` / `formatDuration` helpers — no new duration formatting code.

## Technical Approach

### Browsing layer reorder
In `VideoScreen.kt` `LazyVerticalGrid`, move the `VideoLibrarySelector` item block ABOVE the `VideoSpotlightSections` block (currently selector is below spotlight). Both already use `GridItemSpan(maxLineSpan)`. No data changes.

### Search collapse to icon
Add a `searchExpanded` state to `VideoScreen`. Replace the always-visible `OutlinedTextField` inside `VideoBrowserControls` with a search icon button (reuse `HeaderAction`-style icon, e.g. magnifier) that toggles `searchExpanded`. When expanded, render the `OutlinedTextField` + a close affordance; when collapsed, clear `searchQuery`. Keep `onSearchChange` plumbing.

### "全部 N 项" divider
Add a full-span item between `VideoSpotlightSections` and `VideoBrowserControls` (or between controls and grid) showing "全部 N 项" as a section header, where N = `visibleVideos.size` (or `videos.size` when no active filter). Use `MaterialTheme.typography.headlineMedium` to match the spotlight row titles. Only render when `videos.isNotEmpty()`.

### Detail resume/restart actions
In `VideoScreenLogic.kt`, add a helper that resolves the primary action label and secondary-action presence:
```kotlin
internal data class VideoDetailPlayAction(
    val primaryLabel: String,
    val primaryResumeSeconds: Int,
    val secondaryLabel: String?
)

internal fun resolveVideoDetailPlayAction(video: VideoItem): VideoDetailPlayAction
```
Logic:
* If `playbackPositionSeconds > 0 && !isPlayed && (durationSeconds == 0 || playbackPositionSeconds < durationSeconds)` → primary = "继续从 ${formatLongDuration(playbackPositionSeconds)} 播放", primaryResumeSeconds = playbackPositionSeconds, secondary = "从头播放".
* Else → primary = "播放", primaryResumeSeconds = 0, secondary = null.

In `VideoDetailScreen.kt`, the hero currently calls `onPlay` (which routes through `MainActivity` → `VideoPlaybackEngine.play(video)` → `resolveVideoInitialStartPositionMs` which already seeks to resume). To support "从头播放", pass a new `onPlayFromStart: () -> Unit` callback from `VideoScreen` that seeks to 0 instead. The engine already handles resume via `resolveVideoInitialStartPositionMs`; for "从头播放" we either (a) pass a flag through `onPlayVideo` to skip resume, or (b) add a `playFromStart` entry point. Prefer (a): change `onPlayVideo: (VideoItem) -> Unit` to `onPlayVideo: (VideoItem, resumeFromStart: Boolean) -> Unit` — wait, that widens the contract. Cleaner: keep `onPlayVideo: (VideoItem) -> Unit` for resume, and add a separate `onPlayVideoFromStart: (VideoItem) -> Unit` path. Actually simplest: the engine's `resolveVideoInitialStartPositionMs` is the resume resolver; adding a `playFromStart(video)` that calls `play(video)` after zeroing position is engine-level. Lowest-risk approach: in `VideoScreen`, both actions call `onPlayVideo(video)`, but "从头播放" first needs the engine to skip resume.

Let me re-examine: `VideoPlaybackEngine.play(video)` calls `resolveVideoInitialStartPositionMs(video)` which returns `resumeSeconds * 1000L` when resumable. To force start-at-0, the engine needs a flag. Cleanest minimal change: add `VideoPlaybackEngine.playFromStart(video)` OR `play(video, resume: Boolean)`. The `MainActivity` `onPlayVideo` lambda currently calls `videoVM.play(video)`. I'll add a `videoVM.playFromStart(video)` path and thread a second callback.

Decision: extend `VideoScreen`'s `onPlayVideo` signature is invasive (touches `MainActivity` call site + lambda). Instead, pass two callbacks from `MainActivity` into `VideoScreen`: `onPlayVideo: (VideoItem) -> Unit` (resume, existing) and `onPlayVideoFromStart: (VideoItem) -> Unit` (new). `VideoDetailScreen` receives both and wires them to primary/secondary. `VideoPlaybackEngine` gets a `playFromStart(video)` method that mirrors `play(video)` but with `startPositionMs = 0`.

### Episode unwatched filter
In `VideoDetailScreen.kt`, add `episodeFilter` state (`All` / `Unwatched`). Render two pill chips above the episode list. Filter `relatedEpisodes` by `episodeFilter == All || !episode.isPlayed`. No data-layer change.

### Player initial controls visible
In `VideoPlayerScreen.kt`, change `var controlsVisible by remember { mutableStateOf(false) }` to `mutableStateOf(true)`. The existing `LaunchedEffect(controlsVisible, state.isPlaying, scrubPosition, statusTone)` auto-hide already fades it after 4s when playing and not scrubbing and no status. Verify the auto-hide condition still triggers (it does: when video loads, `state.isPlaying` becomes true → after 4s → `controlsVisible = false`).

### Player status text localization
In `VideoPlayerScreen.kt`:
* `videoPlayerStatusText(...)` returns "缓冲中" / "暂无视频" / "播放异常" / null.
* `VideoPlayerCenterMessage` titles/subtitles in the three branches become Chinese: "暂无视频" / "从媒体库选择一个视频开始播放" ; "播放异常" / errorMessage ; "缓冲中" / "正在准备视频流".
* Update `VideoPlayerScreenTest.kt` assertions to the new Chinese strings.

## Decision (ADR-lite)

**Context**: Current video tab is functionally complete but several display/affordance details diverge from mainstream video apps: library selector below shelves, always-visible search field, single "播放" button ignoring resume context, no episode unwatched filter, player chrome hidden on first open, English status text in a Chinese UI.

**Decision**: Implement the 7 MVP items above as targeted UI/logic changes. Keep Emby data contracts, Media3 engine, and gesture handling intact. Resume-vs-restart is resolved by a new `playFromStart` engine entry point + second callback, rather than widening the existing `onPlayVideo` signature.

**Consequences**: Two `VideoScreen` play callbacks instead of one; `VideoPlaybackEngine` gains a `playFromStart` sibling. Test surface grows by ~4-5 cases. No backend contract change. Future "next episode" / "skip intro" remain out of scope.

## Out of Scope

* Adding Plex or WebDAV backend support.
* Replacing Media3 playback core.
* Lock-screen / orientation-lock control in player.
* Skip intro / next episode affordances.
* Subtitles, audio-track selection, casting.
* Downloads, multi-user profiles, server-side metadata editing.
* PiP (picture-in-picture).
* Full design-system rewrite.
* Repairing "garbled" Chinese strings — source files are valid UTF-8; no repair needed.

## Technical Notes

* Project design context: `DESIGN.md` — content-first, alpha-as-depth, pill geometry, flat-at-rest, FastOutSlowInEasing only.
* Relevant files: `VideoScreen.kt`, `VideoBrowseComponents.kt`, `VideoDetailScreen.kt`, `VideoPlayerScreen.kt`, `VideoScreenLogic.kt`, `VideoPlayerGestures.kt`, `MainActivity.kt`, `VideoPlaybackEngine.kt`, `VideoScreenTest.kt`, `VideoPlayerScreenTest.kt`.
* Spec: `.trellis/spec/backend/emby-integration.md` — "Video browsing UI", "Series detail UI", "Direct playback controls", "Direct playback start position" sections directly govern the resume/restart and shelf/selector behavior.
* Duration formatting helpers: `formatLongDuration` / `formatDuration` already exist in the UI module — reuse for resume label.
