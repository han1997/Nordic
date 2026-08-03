# Video UI Overhaul: Player Chrome, Browse Layouts, Detail Backdrop

## Goal

Bring the video experience in Nordic Media Hub up to the bar set by Plex / Infuse / Apple TV: a player that gets out of the way when idle, browse surfaces that distinguish "continue watching" from movie-grid browsing, and a detail page that leads with cinematic backdrop imagery instead of a centered poster.

The drive is product parity + the performance-first spec we just landed (persistent chrome must justify its always-visible cost).

## What I already know (from repo inspection)

### Current video stack
- **Data model** (`data/VideoItem`, `api/EmbyApi.kt`, `data/EmbyRepository.kt`):
  - `VideoItem` has `imageUrl` (Primary only), no backdrop/logo/thumb URL.
  - `EmbyItemDto` fetches `ImageTags` (a `Map<String,String>`) but `Fields` query omits `BackdropImageTags`. No `BackdropImageTags` DTO field.
  - Already has: `playbackPositionSeconds`, `isPlayed`, `lastPlayedDate`, `communityRating`, `seriesId/Name`, `seasonNumber`, `episodeNumber`, `durationSeconds`, `overview`, `year`.
  - Auth image URLs via `X-Emby-Token` header (or `api_key` fallback).
- **Browse** (`ui/VideoScreen.kt` + `ui/VideoBrowseComponents.kt`):
  - Grid: `GridCells.Adaptive(156.dp)`, `VideoCard` renders 2:3 portrait poster + title + meta line.
  - Spotlight rows ("继续观看" / "最受好评" / "未播放的") reuse the same `VideoCard` in 132dp-wide `LazyRow` items. Continue-watching has no visual progress indicator — only the meta text "看到 …".
  - Library selector: horizontal chip row.
- **Detail** (`ui/VideoDetailScreen.kt`):
  - 72%-width centered portrait poster, no backdrop / hero.
  - Title + chips + ▶播放 + 简介 + 分集 (episode rows with 16:9 thumb + label + title + meta, no watched state or progress bar).
- **Player** (`ui/VideoPlayerScreen.kt`):
  - Column with `SpaceBetween` always renders top bar + controls. Scrim always on. No tap-to-toggle, no auto-hide, no gestures.
  - Buttons are text glyphs: "X", "-10", "+30", "||"/">", "Full"/"Exit", aspect-ratio label. No icon assets.
  - `VideoPlayerControls` stays subscribed to `state.positionSeconds` via `visiblePosition` even while paused.
  - Surface = `SurfaceView` inside `AspectRatioFrameLayout`; fullscreen is a state flag, no orientation lock.
- **Spec / conventions**:
  - `.trellis/spec/backend/quality-guidelines.md` now has "Performance-first persistent media chrome" — controls should hide/collapse when static, detach from high-frequency ticks. This task is the first concrete application of that spec.
  - Design tokens `NordicShapes` / `NordicSpacing` / `NordicAlpha` / `NordicTypography` must be used; `Color.White.copy(alpha=…)` in player overlay is an explicit out-of-token-scope exception.
  - Shared media state components (`MediaStateCard`, `MediaLoadingCard`) should be reused.
  - `BackHandler` required for every sub-page; last-registered-wins priority.

## Assumptions (to validate)

- Emby server exposes Backdrop image tags when requested via `Fields=BackdropImageTags`; we can build `/Items/{id}/Images/Backdrop` URLs analogously to Primary.
- We will keep `SurfaceView` (not switch to `TextureView`) — parity with current rendering, lower overhead.
- Iconography will use Material Symbols / Compose vector assets already available, not custom icon font files.
- No new media3 APIs required for tap-to-toggle / auto-hide — this is Compose-side overlay state.

## Open Questions

(All resolved — see Decisions below.)

## Decisions

- **MVP scope**: all three surfaces (player + browse continue-watching + detail backdrop) in one task.
- **Player gestures**: full suite — tap-to-toggle + auto-hide (4s) + double-tap-to-seek (±10 left / ±30 right) + horizontal-swipe-to-scrub + pinch-to-zoom (Fill/Crop aspect).
- **Backdrop image source**: item's own `BackdropImageTags[0]`; episodes fall back to `ParentBackdropItemId` + `ParentBackdropImageTags` (Series), then `SeriesId`, then gradient placeholder.
- **Fullscreen orientation lock**: already exists in `MainActivity.kt:400-412` (`SENSOR_LANDSCAPE` + hide system bars). Keep as-is.
- **Icons**: add `androidx.compose.material:material-icons-core` dependency; use vector icons across the entire video flow (player + detail + continue-watching + episode rows). Replaces current text glyphs ("||", ">", "-10", "Full", "X").

## Requirements

1. **Player overlay state** — controls and scrim start hidden; a tap on the video area toggles visibility; while playing, controls auto-hide after 4 seconds of inactivity; auto-hide is suppressed while paused, scrubbing, or showing an error/buffering state.
2. **Player performance isolation** — when controls are hidden, the overlay must not subscribe to per-second `positionSeconds` ticks; only the visible-controls branch reads `visiblePosition` (complies with `.trellis/spec/backend/quality-guidelines.md` § "Performance-first persistent media chrome").
3. **Player gestures** — double-tap left/right seeks ∓10/±30 seconds; horizontal swipe scrubs (disabled when `durationSeconds <= 0`); pinch in fullscreen cycles aspect ratio (FIT/FILL/CROP). Taps that trigger gestures do not toggle control visibility.
4. **Player icons** — replace text glyphs with Material Symbols from `material-icons-core` (PlayArrow, Pause, FastRewind, FastForward, AspectRatio, Fullscreen, FullscreenExit, Close).
5. **Continue-watching row** — distinct from the movie grid: 16:9 landscape thumbnail (Primary image cropped to 16:9 for MVP), title overlay at bottom, and a progress bar reflecting `playbackPositionSeconds / durationSeconds`. Used only for the "继续观看" shelf.
6. **Movie / series library grid** — keeps the current 2:3 portrait `VideoCard` (unchanged).
7. **Detail hero** — full-bleed 16:9 backdrop image at top with gradient scrim (DESIGN.md pattern), title + meta chips + primary play action overlaid on the lower half. Falls back through `BackdropImageTags[0]` → `ParentBackdropItemId`/`ParentBackdropImageTags` → `SeriesId`-derived backdrop → gradient placeholder.
8. **Detail episode rows** — surface watched state (check icon when `isPlayed`) and a progress bar when `playbackPositionSeconds > 0 && !isPlayed`.
9. **Backdrop plumbing** — `EmbyItemDto` gains `BackdropImageTags: List<String>?`, `ParentBackdropItemId: String?`, `ParentBackdropImageTags: List<String>?`; `Fields` query adds `BackdropImageTags,ParentBackdropImageTags`; `VideoItem` gains `backdropImageUrl: String?`; `EmbyRepository` resolves the fallback chain.
10. **Icons across video flow** — detail play button, episode-row watched state, and continue-watching overlay use vector icons from `material-icons-core`.
11. **BackHandler parity** — detail / player / fullscreen back paths continue to match their manual back buttons.
12. **Design tokens** — all new composables use `NordicShapes` / `NordicSpacing` / `NordicAlpha` / `NordicTypography`; `Color.White.copy(alpha=…)` remains allowed in the player overlay scope per the existing spec exception.

## Technical Approach

### Data layer
- `EmbyApi.kt`: add `@SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null` and `@SerializedName("ParentBackdropItemId")/("ParentBackdropImageTags")` to `EmbyItemDto`; extend `Fields` query to include them.
- `EmbyRepository.toVideoItem()`: resolve `backdropImageUrl` via the fallback chain; add `backdropImageUrl: String? = null` to `VideoItem`.
- New `EmbyRepository` helper: `backdropImageUrl(itemId, tags, maxWidth=1280)` mirroring `primaryImageUrl(...)`.

### Player (`VideoPlayerScreen.kt` + new `VideoPlayerGestures.kt`)
- `var controlsVisible by remember { mutableStateOf(false) }` + `var lastInteraction by remember { mutableStateOf(0L) }`.
- `LaunchedEffect(controlsVisible, isPlaying, scrubPosition)` auto-hide timer: if `isPlaying && controlsVisible && scrubPosition == null && statusTone == null`, wait 4s then `controlsVisible = false`.
- Wrap the video area in a `pointerInput` detecting tap (toggle), double-tap (seek ±10/±30), horizontal drag (scrub), and `detectTransformGestures` (pinch → cycle aspect in fullscreen only).
- `VideoPlayerControls` and `VideoPlayerScrim` only composed when `controlsVisible`; `VideoPlayerCenterMessage` (error/buffering) composed independently of `controlsVisible`.
- Replace `VideoPlayerChromeButton` text glyphs with `Icon(Icons.Filled.X, ...)`.

### Detail (`VideoDetailScreen.kt`)
- Replace the 72%-width centered poster with a full-bleed `Box` containing: backdrop `CoverArt` (16:9, fillMaxWidth) + gradient scrim `Brush.verticalGradient` + overlaid title/meta/play button column at the bottom.
- `EpisodeRow`: add a progress bar `LinearProgressIndicator(progress = playbackPositionSeconds / durationSeconds)` over the thumbnail; show `Icons.Filled.CheckCircle` when `isPlayed`.

### Browse (`VideoBrowseComponents.kt` + `VideoScreen.kt`)
- New `ContinueWatchingCard` composable: 16:9 thumbnail (Primary cropped), title overlay, progress bar. Used only by `VideoSpotlightRow` when `keyPrefix == "continue"`.
- `VideoCard` (2:3 portrait) stays for the grid and the other spotlight rows.

### Dependencies
- Add `implementation("androidx.compose.material:material-icons-core")` (version provided by Compose BOM) to `app/build.gradle.kts`.

## Decision (ADR-lite)

**Context**: Video UI lagged Plex/Infuse/Apple TV on three fronts — always-visible player chrome, portrait posters for continue-watching, and a poster-centric detail page. The performance-first spec we just landed demanded chrome that hides when static.

**Decision**: Single task covering all three surfaces. Full player gesture suite (tap/double-tap/swipe/pinch) + vector icons via `material-icons-core`. Backdrop hero from Emby `BackdropImageTags` with episode→series fallback. Continue-watching gets a dedicated 16:9 card with progress bar; movie grid keeps 2:3 posters.

**Consequences**: Larger PR (touches data + 4 UI files + build.gradle). `material-icons-core` adds a small bundle cost, justified by icon legibility on black overlay. Pinch/swipe gestures add gesture-detection complexity but are the expected parity bar. Thumb image fetching deferred to a follow-up (Primary cropped to 16:9 covers the MVP).

## Acceptance Criteria

- [ ] Tapping the player area toggles control visibility; controls auto-hide after 4s of inactivity while playing.
- [ ] Player scrim fades in/out with controls; hidden controls do not block content taps.
- [ ] Hidden player controls are not subscribed to per-second `positionSeconds` ticks (complies with performance-first spec).
- [ ] Double-tap left/right seeks ∓10/±30s; horizontal swipe scrubs (disabled when `durationSeconds <= 0`); pinch in fullscreen cycles aspect (FIT/FILL/CROP).
- [ ] Player buttons use Material Symbols (PlayArrow/Pause/FastRewind/FastForward/AspectRatio/Fullscreen/Close), not text glyphs.
- [ ] "继续观看" row renders 16:9 landscape thumbnails with a progress bar reflecting `playbackPositionSeconds / durationSeconds`.
- [ ] Movie / series library grid keeps 2:3 portrait posters (unchanged).
- [ ] Video detail page renders a full-bleed 16:9 backdrop with gradient scrim + overlaid title/meta/play; falls back through `BackdropImageTags[0]` → `ParentBackdrop…` → `SeriesId` → gradient placeholder.
- [ ] Episode rows show watched state (`CheckCircle` when `isPlayed`) and a progress bar when partially played.
- [ ] `EmbyItemDto` + `VideoItem` carry `backdropImageUrl`; `Fields` query includes `BackdropImageTags`; episode fallback uses `ParentBackdropItemId`/`ParentBackdropImageTags`.
- [ ] All new / changed composables use `NordicShapes` / `NordicSpacing` / `NordicAlpha` / `NordicTypography` tokens; `Color.White.copy(alpha=…)` stays in player overlay scope.
- [ ] `BackHandler` parity maintained for detail / player / fullscreen.
- [ ] Existing video tests (`VideoScreenTest`, `VideoPlayerScreenTest`, `EmbyRepositoryTest`, `MainActivityTest`) still pass; new logic (auto-hide, progress-bar fraction, backdrop fallback, gesture resolution) has unit tests.
- [ ] `app/build.gradle.kts` adds `androidx.compose.material:material-icons-core` (BOM versioned).

## Definition of Done

- Lint / typecheck / unit tests green (`compileDebugKotlin`, `testDebugUnitTest`, `lintDebug`).
- No regression in existing `VideoScreenTest`, `VideoPlayerScreenTest`, `EmbyRepositoryTest`, `MainActivityTest`.
- Spec: update `.trellis/spec/backend/quality-guidelines.md` if a reusable pattern emerges (e.g., player chrome auto-hide contract).

## Out of Scope (explicit)

- Audio / audiobook player UI changes.
- Music player / dock UI changes.
- New video codecs / transcoding settings.
- Cast / remote playback.
- Subtitle track selection UI.
- Audio track selection UI.
- Playback speed (0.5x–2x).
- Skip intro / skip credits.
- Next episode auto-play.
- Download / offline video.
- Multiple backdrop carousel (single best backdrop only).
- Picture-in-picture.
- Adding Thumb image fetching for continue-watching cards (use Primary cropped to 16:9 for MVP; Thumb is a documented follow-up per research).

## Edge Cases (handled in MVP)

- Backdrop image fails to load / not available → gradient placeholder (DESIGN.md pattern: primary(0.18f) → secondary(0.1f) → surfaceVariant(0.82f)).
- Episode with no parent backdrop → `SeriesId` fallback, then gradient placeholder.
- Continue-watching card with no image → Primary image cropped to 16:9, then gradient placeholder.
- Auto-hide while user is scrubbing → do not hide while `scrubPosition != null`.
- Double-tap during buffering → seek still queues (Media3 handles it).
- Swipe-to-scrub with unknown duration → disable scrub when `durationSeconds <= 0`.
- Pinch in non-fullscreen → no-op (only active in fullscreen).
- Controls hidden + error state → keep error center message visible even when chrome is hidden.
- Controls hidden + buffering → keep buffering indicator visible until playback resumes.

## Technical Notes

- Files likely impacted:
  - `api/EmbyApi.kt` — add `BackdropImageTags` DTO field, extend `Fields` query.
  - `data/EmbyRepository.kt` — map backdrop URL, expose `backdropImageUrl` on `VideoItem`.
  - `ui/VideoPlayerScreen.kt` — overlay state, auto-hide, scrim fade, control layout.
  - `ui/VideoDetailScreen.kt` — backdrop hero, gradient scrim, overlaid title/meta, episode row progress.
  - `ui/VideoBrowseComponents.kt` — `ContinueWatchingCard` (16:9 + progress), keep `VideoCard` for grid.
  - `ui/VideoScreen.kt` — wire continue-watching row to new card.
  - Possibly new `ui/VideoPlayerGesture.kt` for tap / double-tap / swipe detection.
- Reference: `.trellis/spec/backend/quality-guidelines.md` § "Performance-first persistent media chrome".
- DESIGN.md: backdrop gradient pattern (primary/secondary at low alpha to surfaceVariant) is the identity system for hero surfaces.

## Research References

- [`research/emby-image-api.md`](research/emby-image-api.md) — Add `Fields=BackdropImageTags`; model `BackdropImageTags` as `List<String>?` (separate from `ImageTags` Map, NOT a "Backdrop" key). URL: `/Items/{id}/Images/Backdrop?maxWidth=1280&quality=90&tag={tag}` (index 0). Episodes use `ParentBackdropItemId` + `ParentBackdropImageTags` (Series), then `SeriesId`, then null. Same `X-Emby-Token` auth. Thumb (16:9) is the correct surface for continue-watching cards (flagged for follow-up).
