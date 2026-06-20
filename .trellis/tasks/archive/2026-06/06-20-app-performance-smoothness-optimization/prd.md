# App Performance and Smoothness Optimization

## Goal

Improve perceived smoothness and runtime performance across Nordic Media Hub, with emphasis on scrolling, page switching, playback surfaces, image-heavy media lists, and avoiding unnecessary recomposition or work on the main thread.

## What I Already Know

* User requested broad performance and smoothness optimization.
* The app is a Kotlin/Jetpack Compose Android app.
* Product/design context says the UI should feel light, content-focused, and restrained.
* The most likely performance-sensitive areas are:
  * `MusicScreenV2.kt`: main music home, library pages, search results, many `LazyColumn`/`LazyRow` sections, sorting, and multiple image cards.
  * `MusicHomeSections.kt`: album/song/artist cards and artwork fallback handling.
  * `MusicPlayerScreen.kt`: artwork/lyrics switching and recently added cover-art background layer.
  * `PlaybackDock.kt`: persistent bottom dock that observes playback state and renders artwork.
  * `MainActivity.kt`: tab/page switching, bottom dock visibility, playback state collection, lyrics loading, and audiobook progress sync.
  * `VideoScreen.kt` / `AudiobookScreen.kt`: image-heavy lists and config-driven loading.
* Existing dependency stack includes Compose Material3, Coil Compose, Retrofit/OkHttp, DataStore, and Media3.
* Existing specs require:
  * sequential Gradle verification on Windows;
  * no duplicate utility functions;
  * shared media state surfaces for loading/error/empty states;
  * `NavidromeRepository` remembered by config;
  * music Songs page must use `getAllSongs()`, not the recent preview list.
* Impeccable performance guidance says to identify actual bottlenecks and measure before/after where possible.

## Assumptions

* The highest-value MVP should improve user-visible smoothness rather than attempt risky deep architectural rewrites.
* Initial target should be low-risk optimizations: stable list keys, avoiding unnecessary image work, limiting recomposition triggers, and smoothing page/player transitions.
* Network/API pagination and cache-schema work are out of scope unless inspection proves they are the primary bottleneck.

## Open Questions

* None for the MVP.

## Requirements

* Identify concrete performance/smoothness hotspots from code inspection.
* Prioritize list and image scrolling smoothness for the MVP.
* Optimize music home, song/album/artist lists, and search result image surfaces before deeper sync/playback work.
* Implement low-risk optimizations that preserve existing user-facing behavior.
* Avoid changing repository/API contracts unless explicitly needed and documented.
* Preserve current media loading behavior, playback behavior, and cache semantics.
* Keep design-system consistency while reducing UI work.

## Acceptance Criteria

* [x] Music home and library lists avoid obvious unstable keys or avoidable recomposition work.
* [x] Image-heavy surfaces avoid unnecessary duplicate image loading where feasible.
* [x] Recently added songs, recent albums, artists, search results, and detail lists use stable keys where data identity is available.
* [x] Player and bottom dock remain visually correct and responsive.
* [x] Existing compile, unit test, and lint gates pass.
* [x] Any measurable or inspectable before/after improvement is recorded.

## Definition of Done

* Relevant specs read before coding.
* Focused implementation committed.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* Spec update reviewed and applied only if a reusable convention or contract is learned.

## Out of Scope

* Playback/player animation optimization unless directly caused by image/list work.
* Broad page-transition redesign.
* Replacing Compose, Coil, Retrofit, Media3, or the app navigation model.
* Adding a new performance monitoring SDK.
* Large cache schema redesign unless explicitly approved.
* Changing server API behavior or playback queue semantics.

## Technical Notes

* Task created at `.trellis/tasks/06-20-app-performance-smoothness-optimization`.
* Design context loaded from `PRODUCT.md` and `DESIGN.md`.
* Impeccable product and optimize references loaded.
* Initial search covered Compose lazy lists, image loading, animations, repository creation, lifecycle collection, and playback state collection.
* Implemented stable `key` and `contentType` values for music home, library, detail, playlist, and search lazy lists/cards where data identity exists.
* Cached preview slices with `remember(...) { take(n) }` for home and search suggestion shelves, and used `itemsIndexed` for home song playback clicks to avoid per-click `indexOf`.
* Removed per-artwork success/error debug logging from `MusicArtwork`; image failures still switch to the existing fallback state without log spam.
* Verification passed:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`

## Decision

**Context**: The user chose list and image scrolling smoothness as the first optimization target.

**Decision**: This MVP will focus on music list/image surfaces and avoid deeper sync or playback architecture changes.

**Consequences**: The change should be low-risk and directly improve perceived scrolling stability, but it will not address network sync duration or full app startup time in this pass.
