# Navigation Flow Audit

## Scope
- Audited modules: Music / Audiobook / Video
- Audited dimensions: back path / selection-after-refresh / first-connect / config-change reset

## Findings

### Music

#### Finding M1 - Detail back path loses source context
- **Severity**: must-fix
- **Dimension**: back path
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt:630`
- **Observation**: `navigateBackFromMusicPage()` returns only `PlaylistDetail` to `Playlists`; every other sub-page, including `AlbumDetail` and `ArtistDetail`, sets `selectedTab = 0` and `libraryPage = Home`. Album detail can be opened from Home (`MusicScreenV2.kt:832`), Albums (`MusicScreenV2.kt:961`), ArtistDetail (`MusicScreenV2.kt:1138`), Search (`MusicScreenV2.kt:1262`, `MusicScreenV2.kt:1334`), and artist detail can be opened from Home/Search (`MusicScreenV2.kt:918`, `MusicScreenV2.kt:1312`).
- **Expected**: Back from a detail page should return to the user's immediate source context when it is still valid, so system back and visible back preserve a predictable drill-in/drill-out flow.
- **Suggested fix**: Track a small `previousMusicPage` or `musicDetailOrigin` when opening album/artist/playlist detail, then use that in `navigateBackFromMusicPage()`. Keep the existing fallback to Home when the recorded origin is unavailable after config reset.

#### Finding M2 - BackHandler and visible back are currently consistent
- **Severity**: acceptable
- **Dimension**: back path
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt:640`
- **Observation**: The system `BackHandler` and `MediaPageHeader(onBack = ::navigateBackFromMusicPage)` both call the same function (`MusicScreenV2.kt:736`). The handler is declared at composable scope, outside `AnimatedContent`, matching the project back-handler priority contract in `.trellis/spec/backend/quality-guidelines.md:607`.
- **Expected**: System back and visible back should invoke identical page-level behavior.
- **Suggested fix**: No source change required for handler parity; preserve this structure if M1 adds origin-aware back routing.

#### Finding M3 - Refresh can leave selected music detail objects dangling
- **Severity**: should-fix
- **Dimension**: selection
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt:217`
- **Observation**: `refreshMusicData()` replaces `albums`, `songs`, `recentlyAddedSongs`, and `artists` (`MusicScreenV2.kt:240`) but does not reconcile `selectedAlbum`, `selectedArtist`, `libraryPage`, `albumDetailSongs`, or `artistAlbums`. `loadAlbumList()` replaces `sortedAlbums` (`MusicScreenV2.kt:277`) without checking whether `selectedAlbum` still exists; `loadPlaylists()` replaces `playlists` (`MusicScreenV2.kt:315`) without checking `selectedPlaylist` unless a delete action is the source (`MusicScreenV2.kt:428`).
- **Expected**: After refresh, selected detail objects should either be updated to the refreshed object with the same id or cleared and the page returned to a sensible list/home state.
- **Suggested fix**: Add small resolver helpers parallel to video/audiobook, e.g. resolve selected album/artist/playlist after refreshed lists arrive. If a selected detail no longer exists, clear its detail payload and route from `AlbumDetail`/`ArtistDetail`/`PlaylistDetail` back to the corresponding source page or Home.

#### Finding M4 - First-connect guidance is present but not readiness-specific
- **Severity**: acceptable
- **Dimension**: first-connect
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt:784`
- **Observation**: When there is no content and no loading/error state, Music shows `先接入你的音乐库` with hint `前往配置 tab 开始连接` (`MusicScreenV2.kt:785`). The condition is content-based, not directly gated on `!savedConfig.isReadyForMusicSync()`, so a ready but empty library receives similar guidance.
- **Expected**: Not-ready first-connect states should direct users to the Config tab.
- **Suggested fix**: No urgent source change required for navigation predictability. If copy precision matters later, split the not-ready state from the ready-empty-library state.

#### Finding M5 - Config reset covers most account state and guards async writes, but reset feedback is silent
- **Severity**: should-fix
- **Dimension**: config-change
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt:148`
- **Observation**: `resetMusicStateAfterConfigChange()` clears selected items, detail payloads, search, filters/sorts, loading flags, playlist dialogs/action state, errors, and cache age (`MusicScreenV2.kt:148`). Async loaders use `musicConfigStateVersion` checks in refresh/list/detail/search paths (`MusicScreenV2.kt:144`, `MusicScreenV2.kt:228`, `MusicScreenV2.kt:278`, `MusicScreenV2.kt:509`, `MusicScreenV2.kt:1214`). The reset is triggered silently by `LaunchedEffect(savedConfig)` (`MusicScreenV2.kt:601`) and routes all pages to Home via `resolveMusicLibraryPageAfterConfigChange()` (`MusicScreenLogic.kt:25`).
- **Expected**: Account-scoped state should reset and stale async writes should not repopulate old content; when a visible detail/search page is forced home, the user should receive some feedback.
- **Suggested fix**: Keep the version guards. Add a lightweight transient message only when the previous page was not Home or when content was visible before reset, so config-driven page collapse is explained without changing the navigation model.

### Audiobook

#### Finding A1 - Back from Detail is consistent
- **Severity**: acceptable
- **Dimension**: back path
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt:298`
- **Observation**: `navigateBackFromAudiobookPage()` sets `libraryPage = Home` and clears `errorMessage`. System back uses this same function (`AudiobookScreen.kt:303`), and the visible header back uses the same function (`AudiobookScreen.kt:347`). No overlay/config `BackHandler` exists in this screen.
- **Expected**: Detail back should consistently return to the audiobook list/home surface.
- **Suggested fix**: No source change required.

#### Finding A2 - Refresh preserves or clears selected detail coherently
- **Severity**: acceptable
- **Dimension**: selection
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt:201`
- **Observation**: `refreshAudiobooks()` captures `previousSelectedItem`, resolves it against refreshed `items`, assigns `selectedItem`, clears `loadingItemDetailId`, and calls `resolveAudiobookLibraryPageAfterRefresh()` to return Detail to Home only when the previous selected item disappeared (`AudiobookScreen.kt:201`, `AudiobookScreen.kt:211`). Library selection also clears `selectedItem`, returns Home, and clears detail loading state after loading the chosen library (`AudiobookScreen.kt:376`).
- **Expected**: A detail selection should survive refresh only if the item still exists, otherwise the page should fall back to Home.
- **Suggested fix**: No source change required.

#### Finding A3 - First-connect guidance is explicit and config reset reaches Home
- **Severity**: acceptable
- **Dimension**: first-connect
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt:404`
- **Observation**: When `!savedConfig.isReadyForAudiobookSync()`, Home shows `先接入你的有声书书库` with hint `前往配置 tab 开始连接`. Config changes reset `libraryPage` to Home and clear libraries/items/selection in `resetAudiobookStateAfterConfigChange()` (`AudiobookScreen.kt:139`), so a ready-to-not-ready transition cannot leave the user on a stale detail page.
- **Expected**: Not-ready states should direct users to Config, and readiness loss should clear content/detail navigation.
- **Suggested fix**: No source change required.

#### Finding A4 - Config reset is guarded, but page-collapse feedback is silent
- **Severity**: should-fix
- **Dimension**: config-change
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt:279`
- **Observation**: `LaunchedEffect(savedConfig)` calls `resetAudiobookStateAfterConfigChange()` and then optionally applies cache/refreshes for ready configs (`AudiobookScreen.kt:279`). Refresh and detail loads guard writes with `audiobookConfigStateVersion` and item/library checks (`AudiobookScreen.kt:157`, `AudiobookScreen.kt:197`, `AudiobookScreen.kt:253`, `AudiobookScreen.kt:258`). The user-visible reset to Home after config changes is silent.
- **Expected**: State reset and stale-write prevention should remain; forced navigation from Detail to Home should be understandable to the user.
- **Suggested fix**: Add a transient informational state only when config changes while `libraryPage == Detail` or when a selected item existed before reset. Keep the existing version guards.

### Video

#### Finding V1 - Video detail back target is the underlying filtered browser, not an explicit source context
- **Severity**: should-fix
- **Dimension**: back path
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt:212`
- **Observation**: Opening any video sets only `selectedVideo = video`. Back clears `selectedVideo` (`VideoScreen.kt:216`), revealing the existing browser state. This returns to a useful context for search/type-filtered entries because `searchQuery` and `selectedTypeFilter` remain intact, but spotlight entries such as continue-watching/top-rated/unplayed are only visible when no browser filter is active (`VideoScreen.kt:339`). If the user opens a spotlight item, detail back returns to the top of the grid state rather than a dedicated source section position/context.
- **Expected**: Back from detail should reveal the same source context where the item was chosen when that context is still available.
- **Suggested fix**: Track a lightweight detail origin such as `Spotlight`, `Browser`, or current filter/search state. For the current state-driven model, the smallest useful fix is to preserve/restore list scroll or section anchor for spotlight-origin opens rather than changing the whole navigation structure.

#### Finding V2 - Nested BackHandlers are prioritized correctly for detail vs browser filters
- **Severity**: acceptable
- **Dimension**: back path
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt:216`
- **Observation**: The detail handler is declared first and clears `selectedVideo`; the browser-filter handler is declared after it, but is enabled only when `selectedVideo == null` and browser back should be handled (`VideoScreen.kt:220`). Since the later handler is disabled while detail is visible, it cannot steal system back from the detail page. The visible detail back also clears `selectedVideo` (`VideoScreen.kt:240`).
- **Expected**: Last-registered-wins should not let filter/search handlers intercept detail back.
- **Suggested fix**: No source change required; preserve the `selectedVideo == null` predicate if new overlay/config handlers are added.

#### Finding V3 - Catalog refresh resolves selected video safely
- **Severity**: acceptable
- **Dimension**: selection
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/VideoScreenLogic.kt:102`
- **Observation**: `resolveVideoSelectionAfterCatalogRefresh()` keeps the selected video only when the current selection's `libraryId` matches the refreshed `selectedLibraryId` and the refreshed item list contains the same id/library pair (`VideoScreenLogic.kt:107`). `refreshVideo()` assigns this resolver result after replacing `videos` (`VideoScreen.kt:157`, `VideoScreen.kt:164`).
- **Expected**: Selected video should survive refresh only if the refreshed catalog still contains the same item in the selected library.
- **Suggested fix**: No source change required.

#### Finding V4 - Library switch can leave stale type filter after replacing videos
- **Severity**: must-fix
- **Dimension**: selection
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt:307`
- **Observation**: The library selector resets `selectedTypeFilter = VideoTypeFilter.All` before launching a library load (`VideoScreen.kt:312`). Inside the async success path it replaces `videos = loadedVideos` but does not call `resolveVideoTypeFilterAfterCatalogRefresh()` or otherwise validate the filter after the new list arrives (`VideoScreen.kt:319`). This is safe for the current user tap path because it always set All first, but stale async writes from overlapping library requests can still replace `videos` if the selected id matches again later, and the filter correction is not applied in this non-`refreshVideo()` path.
- **Expected**: Every path that replaces catalog items should keep filter/search state coherent with the resulting catalog.
- **Suggested fix**: In the library-load success block, update `selectedTypeFilter = resolveVideoTypeFilterAfterCatalogRefresh(selectedTypeFilter, loadedVideos)` immediately before or after assigning `videos`. This mirrors the main `refreshVideo()` path without rearchitecting navigation.

#### Finding V5 - First-connect guidance is explicit and readiness loss clears browse/detail state
- **Severity**: acceptable
- **Dimension**: first-connect
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt:390`
- **Observation**: When `!savedConfig.isReadyForVideoSync()`, Video shows `先接入你的 Emby 服务器` with hint `前往配置 tab 开始连接`. `resetVideoStateAfterConfigChange()` clears libraries, selected library, videos, selected video, search state, filter, loading/error state, and cache age (`VideoScreen.kt:96`), and `LaunchedEffect(savedConfig)` invokes it on readiness changes (`VideoScreen.kt:193`).
- **Expected**: Not-ready states should direct users to Config, and readiness loss should clear stale detail/browser content.
- **Suggested fix**: No source change required for first-connect behavior.

#### Finding V6 - Config reset is guarded, but page-collapse feedback is silent
- **Severity**: should-fix
- **Dimension**: config-change
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt:96`
- **Observation**: Config changes increment `videoConfigStateVersion` and clear account-scoped state, while cache/refresh writes check `isCurrentVideoConfigRequest()` (`VideoScreen.kt:92`, `VideoScreen.kt:153`, `VideoScreen.kt:187`). The reset silently closes detail/search/filter state when `savedConfig` changes (`VideoScreen.kt:193`).
- **Expected**: Forced navigation caused by server/account changes should be understandable, especially when a user was in detail or a filtered search state.
- **Suggested fix**: Add a one-shot informational message when config reset clears `selectedVideo`, `searchQuery`, or a non-All filter. Keep it local to Video screen state.

### Cross-module

#### Finding C1 - Config-change reset feedback is consistently absent
- **Severity**: should-fix
- **Dimension**: config-change
- **Location**: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt:601`
- **Observation**: Music, Audiobook, and Video all reset page/detail/search state from `LaunchedEffect(savedConfig)` (`MusicScreenV2.kt:601`, `AudiobookScreen.kt:279`, `VideoScreen.kt:193`). All three protect stale async writes with version checks, but none presents user-facing feedback when a config change forces Home or clears filters/detail.
- **Expected**: Silent reset is technically safe but can feel like unexpected navigation if it happens while the user is on a detail/search page.
- **Suggested fix**: Use a shared pattern, not necessarily a shared component: record whether reset will visibly collapse navigation, then show one local compact `MediaStateCard`/snackbar-style message after reset. Avoid major routing changes.

#### Finding C2 - First-connect guidance is aligned with the Config tab contract
- **Severity**: acceptable
- **Dimension**: first-connect
- **Location**: `.trellis/spec/backend/quality-guidelines.md:223`
- **Observation**: The spec requires first-run/setup empty states to direct users to the bottom-nav `配置` tab and forbids per-media inline config panels (`quality-guidelines.md:223`, `quality-guidelines.md:231`). Music, Audiobook, and Video each show `前往配置 tab 开始连接` in their setup states (`MusicScreenV2.kt:789`, `AudiobookScreen.kt:409`, `VideoScreen.kt:395`), and `MainActivity` maps tab 3 to `ServerConfigScreen` (`MainActivity.kt:510`).
- **Expected**: Setup guidance should point to centralized configuration, not a stale per-screen config panel.
- **Suggested fix**: No source change required.

## Summary
- Total findings: 15
- Must-fix: 2
- Should-fix: 6
- Acceptable: 7
- Highest-priority cluster: Music detail navigation and refresh selection reconciliation are the main user-visible risks because the current state model collapses most music drill-in paths to Home and can keep detail selections after refreshed lists no longer contain those objects.
