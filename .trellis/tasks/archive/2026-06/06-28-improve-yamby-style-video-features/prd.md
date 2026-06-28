# Improve Yamby-style Video Features

## Goal

Continue evolving the Video tab toward a Yamby-like media experience: fast poster-first browsing, practical video controls, robust Emby behavior, and polished playback ergonomics.

## What I already know

* User wants the video experience to reference Yamby, improve features, optimize performance, and fix bugs.
* The current app is a Kotlin / Jetpack Compose Android app.
* The video provider currently implemented is Emby.
* Recent work already added:
  * Emby library browsing with poster grid.
  * Search and type filters.
  * Paginated Emby item loading.
  * Null-safe missing `ImageTags` handling.
  * Stream URL mapping.
  * In-app Media3 video playback via `VideoPlaybackEngine` and `VideoPlayerScreen`.
* Current working tree only contains this new Trellis task folder; previous video code is committed in `35df606`.

## Assumptions

* "Reference Yamby" means a media-first Android client experience: dense poster browsing, quick filtering, detail/play flows, smooth controls, and minimal chrome.
* Emby remains the priority provider for this task; Plex/WebDAV parity is not required unless explicitly selected.
* Improvements should build on current architecture rather than replacing it with a new player or navigation framework.

## Open Questions

* None for the MVP.

## Requirements

* Preserve existing Emby browsing and playback behavior.
* Continue using repository-owned Emby API/auth URL mapping; UI must not reconstruct authenticated Emby URLs.
* Keep Compose lists stable with keys/content types for image-heavy grids.
* Keep playback state owned by playback layer rather than duplicated in UI.
* Prioritize a video detail page as the MVP slice.
* Detail page should show at least poster/thumbnail, title, overview, year, duration, type, and a primary play action.
* Detail page should be a full-screen route inside the Video tab with an obvious back action.

## Acceptance Criteria

* [ ] Video grid item opens a video detail surface instead of immediately starting playback.
* [ ] Detail surface displays poster/thumbnail, title, overview, year, duration, type, and primary play action.
* [ ] Play action starts the existing in-app video player using `VideoItem.streamUrl`.
* [ ] Existing Emby browsing/search/type filters and playback continue to work.
* [ ] Kotlin compile passes.
* [ ] Relevant unit tests are added or updated for changed repository/playback behavior.
* [ ] Android lint passes for UI changes.
* [ ] Debug assemble passes if playback, manifest, resources, or dependency wiring changes.

## Definition of Done

* Tests added/updated where behavior changes.
* `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug` pass.
* `:app:assembleDebug` passes if playback or packaging-sensitive code changes.
* Specs/notes updated when service contracts or reusable conventions change.

## Out of Scope

* Replacing Emby with another provider.
* Adding Plex/WebDAV video support unless chosen as the task priority.
* Large redesign of Music or Audiobook tabs.

## Technical Notes

* Key files:
  * `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
  * `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`
  * `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`
  * `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`
* Relevant specs:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/emby-integration.md`
  * `.trellis/spec/backend/quality-guidelines.md`
* Observed quality issue to consider during implementation: `VideoScreen.kt` has a minor indentation anomaly in the `LaunchedEffect(savedConfig)` else branch, although it compiles.

## Decision Log

* 2026-06-28: User selected **video detail page** as the next priority over playback polish, stability-only work, or additional feature categories.
* 2026-06-28: User selected **full-screen detail page** over bottom sheet or wider responsive split layout.

## Technical Approach

Implement the detail page as a Compose screen owned by the Video UI layer. `VideoScreen` keeps the selected `VideoItem` as view state, shows the grid normally, and swaps to the full-screen detail route when a grid poster is clicked. The detail route consumes the existing `VideoItem` fields and calls the existing `onPlayVideo(video)` callback for playback, preserving repository-owned URL mapping and playback-layer ownership.

## Implementation Plan

1. Add a reusable full-screen video detail composable that follows the app's existing Nordic/Yamby-like media-first design language.
2. Wire grid poster clicks to select a detail item instead of immediately playing.
3. Add back/play actions in the detail route and preserve current playback behavior through `onPlayVideo`.
4. Run compile, unit tests, lint, and assemble if required.
