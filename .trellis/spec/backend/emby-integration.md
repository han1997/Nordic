# Emby Integration Contract

## Scenario: Emby Video Browsing and Direct Playback

### 1. Scope / Trigger
- Trigger: The Android app now exposes Emby as the first real video provider instead of placeholder cards.
- Scope: Authentication, user media-library discovery, video item listing, thumbnail URL generation, direct stream URL generation, watched/resume/rating metadata, playback progress reporting, typed errors, and repository tests.
- Out of scope: Plex, WebDAV browsing, persistent Emby token storage, and provider-wide account management.

### 2. Signatures
- Config readiness:
```kotlin
fun VideoServerConfig.isReadyForVideoSync(): Boolean
```
- Base URL normalization:
```kotlin
internal fun normalizeVideoServerBaseUrl(serverUrl: String): String
internal fun VideoServerConfig.normalizedBaseUrl(): String
```
- Repository:
```kotlin
class EmbyRepository(private val config: VideoServerConfig) {
    suspend fun getCatalog(selectedLibraryId: String? = null): VideoCatalog
    suspend fun getLibraryItems(libraryId: String): List<VideoItem>
    suspend fun syncPlaybackProgress(video: VideoItem, positionSeconds: Int, isPaused: Boolean)
    suspend fun stopPlaybackProgress(video: VideoItem, positionSeconds: Int)
}
internal fun resolveEmbyPlaybackPositionTicks(positionSeconds: Int, durationSeconds: Int): Long
internal fun resolveVideoProgressSyncBaselineSeconds(statePositionSeconds: Int, video: VideoItem): Int
internal fun browseCatalogVideos(videos: List<VideoItem>): List<VideoItem>
internal fun visibleBrowseVideos(videos: List<VideoItem>, searchQuery: String, selectedTypeFilter: VideoTypeFilter): List<VideoItem>
internal fun visibleVideoTypeFilters(videos: List<VideoItem>): List<VideoTypeFilter>
internal fun topRatedVideoShelf(videos: List<VideoItem>, limit: Int = 12): List<VideoItem>
internal fun unplayedVideoShelf(videos: List<VideoItem>, limit: Int = 12): List<VideoItem>
internal fun videoMatchesSearch(video: VideoItem, query: String): Boolean
internal fun resolveVideoTypeFilterAfterCatalogRefresh(selectedTypeFilter: VideoTypeFilter, videos: List<VideoItem>): VideoTypeFilter
internal fun resolveVideoSelectionAfterConfigChange(selectedVideo: VideoItem?): VideoItem?
internal fun resolveVideoTypeFilterAfterConfigChange(selectedTypeFilter: VideoTypeFilter): VideoTypeFilter
internal fun shouldReplaceCurrentVideoItem(currentVideo: VideoItem?, requestedVideo: VideoItem): Boolean
```
- Retrofit API:
```kotlin
POST Users/AuthenticateByName
GET Users
GET Users/{userId}/Views
GET Users/{userId}/Items
POST Sessions/Playing/Progress
POST Sessions/Playing/Stopped
```
- DTOs:
```kotlin
data class EmbyItemsResponse(
    val items: List<EmbyItemDto>? = null
)

data class EmbyItemDto(
    val id: String? = null,
    val name: String? = null
)
```

### 3. Contracts
- `VideoServerConfig.isReadyForVideoSync()` returns `true` only when:
  - `type == VideoServerType.EMBY`
  - `serverUrl` is not blank
  - either `apiKey` is not blank, or both `username` and `password` are not blank
- API key flow:
  - `GET Users` with `X-Emby-Token: <apiKey>`
  - ignore returned users whose `Id` is missing, null, empty, or blank
  - choose the remaining user whose `Name` matches `config.username` ignoring case, otherwise choose the first remaining user
  - use the API key as the session token for later requests
- Username/password flow:
  - `POST Users/AuthenticateByName`
  - request body: `{"Username": "...", "Pw": "..."}`
  - header: `X-Emby-Authorization` with Nordic Android client metadata
  - response must include `User.Id` and a non-null, non-blank `AccessToken`
- Library filtering:
  - Include libraries whose `CollectionType` matches one of `movies`, `tvshows`, `homevideos`, or `mixed`, case-insensitively
  - Include `Type == "CollectionFolder"` case-insensitively only as a fallback when `CollectionType` is blank
  - Do not include known non-video collections such as `music`
- Item listing:
  - `GET Users/{userId}/Items`
  - query includes `ParentId`, `Recursive=true`, `IncludeItemTypes=Movie,Series,Episode,Video`
  - query `Fields` includes `Overview`, `ProductionYear`, `SeriesId`, `SeriesName`, `ParentIndexNumber`, `IndexNumber`, `RunTimeTicks`, `ChildCount`, `ImageTags`, `CommunityRating`, and `UserData`
  - `GET Users/{userId}/Views` and `GET Users/{userId}/Items` responses may omit `Items` or send it as null; DTOs must allow nullable lists and repositories must map them to empty app lists
  - Emby item row `Id` and `Name` are optional wire fields. DTOs must model both as nullable strings.
  - Library discovery must skip view rows whose trimmed `Id` or `Name` is blank, while keeping other valid video libraries from the same response.
  - Video item listing must skip item rows whose trimmed `Id` or `Name` is blank, while keeping other valid videos from the same page.
  - Returned `VideoLibrary.id`, `VideoLibrary.name`, `VideoItem.id`, and `VideoItem.title` values should be trimmed so UI state, image URLs, stream URLs, and follow-up item requests do not carry accidental surrounding whitespace.
  - Item pagination must advance `StartIndex` by fetched row count, not mapped video count, so skipped unusable rows do not force extra requests after `TotalRecordCount` has already been fetched.
  - map `RunTimeTicks` to seconds using `10_000_000` ticks per second
- Video metadata:
  - `VideoItem.playbackPositionSeconds` maps from `UserData.PlaybackPositionTicks` using `10_000_000` ticks per second
  - `VideoItem.lastPlayedDate` maps from `UserData.LastPlayedDate` and stays nullable for older/incomplete Emby responses
  - `VideoItem.isPlayed` maps from `UserData.Played == true`
  - `VideoItem.communityRating` maps from `CommunityRating`
  - `VideoItem.seriesId`, `seriesName`, `seasonNumber`, and `episodeNumber` map from `SeriesId`, `SeriesName`, `ParentIndexNumber`, and `IndexNumber`
  - Missing `UserData` or `CommunityRating` must fall back to `0`/`false`/`null` rather than excluding the item
- Video browsing UI:
  - The browse catalog is a UI projection of the loaded Emby item list that excludes `Episode` items. Movies, `Series`, and standalone `Video` items may appear in the browse grid and search results.
  - Keep the full loaded item list available for exceptions that need episode identity: continue-watching can show resumable episodes, and `Series` detail pages derive their episode rows from the full list.
  - Yamby-style spotlight shelves may be derived from the already-loaded Emby item list:
    - **Continue watching (server-authoritative)**: prefer the server `GET /Users/{UserId}/Items/Resume` list (`EmbyRepository.getResumeItems()`), fetched after each successful catalog refresh. `mergeResumeItemsWithCatalog(resume, catalog)` aligns server rows with the loaded catalog — server rows win for progress/order; catalog rows fill omitted playback-critical fields (streamUrl, artwork, chapters, introRange, owning libraryId). Server rows absent from the catalog pass through; the server list is the source of truth for shelf membership. Fallback to local derivation (`continueWatchingShelf`: `playbackPositionSeconds > 0 && !isPlayed`, position < duration, `lastPlayedDate` descending) only when the Resume request fails or the state is null. Resume failures are silent; the Resume list is UI state, never persisted to the browse cache.
    - Top rated: browse-catalog items only, non-null positive `communityRating`, sorted descending
    - Unplayed: browse-catalog items only, `!isPlayed && playbackPositionSeconds <= 0`
  - These shelves are view state only. Do not persist local video history unless the PRD explicitly adds that scope.
  - After a catalog refresh, selected video detail state must resolve against the refreshed item list. Keep the selection only when the same item id still exists in the selected library, and replace it with the refreshed `VideoItem`; otherwise clear the detail state.
  - After a catalog refresh, selected type filter state must resolve against the refreshed browse catalog. Keep `All`, keep a specific browse-visible type only when at least one refreshed browse item still matches it, and reset unavailable or episode-specific filters to `All`.
  - Saved video config changes are catalog boundaries. Clear libraries, selected library id, catalog items, selected detail video, local search text, stale loading state, and stale errors before loading the new account.
  - Saved video config changes must reset the type filter to `All` and load the ready new config without passing a previous config's selected library id.
  - In-flight catalog refresh or library-selection responses from a previous saved config must not write catalog/detail/filter/error/loading state after the config boundary. Guard these writes with a config-state version or equivalent request identity.
  - Same-config manual refresh keeps existing catalog reconciliation behavior: selected detail and type filters may be preserved only when they still exist in the refreshed same-catalog response.
  - Search is local to the browse catalog and composes with the selected type filter; do not add server-side search unless the PRD explicitly includes it.
  - Blank or whitespace-only queries must match all currently visible videos.
  - Search must match title, overview, type, year, and non-blank `seriesName`.
  - When `seasonNumber` or `episodeNumber` is positive, search must match common episode tokens such as `S1`, `E2`, `S1E2`, `S1 E2`, and zero-padded variants such as `S01E02`.
  - Keep token matching in a testable helper such as `videoMatchesSearch(...)`; Compose filtering should call the helper rather than duplicating token rules inline.
- Thumbnail URL:
  - Build `/Items/{itemId}/Images/Primary`
  - Include `maxWidth=640`, `quality=90`, `tag=<ImageTags.Primary>`, and `api_key=<session token>`
  - Return `null` when `ImageTags` is absent or `ImageTags.Primary` is absent
- Direct playback URL:
  - Build `/Videos/{itemId}/stream`
  - Include `Static=true` and `api_key=<session token>`
  - Generate direct stream URLs only for directly playable item types: `Movie`, `Episode`, and `Video`
  - Do not generate a `streamUrl` for `Series`; series detail pages should route playback through episode rows
  - Keep playback URL generation in `EmbyRepository`; UI must consume `VideoItem.streamUrl` instead of reconstructing authenticated URLs.
  - `VideoPlaybackEngine.play(video)` must replace the ExoPlayer item when either the current video id differs or the current `VideoItem.streamUrl` differs from the requested `VideoItem.streamUrl`. Emby stream URLs carry server/token context, so same-id/different-stream requests must refresh ExoPlayer instead of reusing a stale URL.
- Direct playback start position:
  - A newly started unplayed `VideoItem` with `playbackPositionSeconds > 0` must seek to that resume position before playback starts.
  - Items marked `isPlayed == true`, or items with no positive resume position, start at `0`.
  - If `durationSeconds` is known, use a resume position only when it is less than duration. Resume positions at or beyond duration are effectively complete and start at `0`.
  - If `durationSeconds` is unknown, keep positive resume positions because there is no reliable completion boundary.
- Video detail resume/restart play action:
  - `internal data class VideoDetailPlayAction(val primaryLabel: String, val primaryResumeSeconds: Int, val secondaryLabel: String?)` and `internal fun resolveVideoDetailPlayAction(video: VideoItem): VideoDetailPlayAction` live in `VideoScreenLogic.kt`.
  - Resume action is offered when `playbackPositionSeconds > 0 && !isPlayed && (durationSeconds == 0 || playbackPositionSeconds < durationSeconds)` — the same eligibility rule as the continue-watching shelf. `primaryLabel = "继续从 ${formatLongDuration(playbackPositionSeconds)} 播放"`, `primaryResumeSeconds = playbackPositionSeconds`, `secondaryLabel = "从头播放"`.
  - Otherwise `primaryLabel = "播放"`, `primaryResumeSeconds = 0`, `secondaryLabel = null`. Already-played items never offer a resume action even with a positive position.
  - `VideoPlaybackEngine.play(video)` is the resume path (uses `resolveVideoInitialStartPositionMs`). `VideoPlaybackEngine.playFromStart(video)` is the restart path: it mirrors `play(video)` (same `shouldReplaceCurrentVideoItem` media-item wiring) but forces `seekTo(0)` after the item is prepared, skipping the resume resolver.
  - The app shell routes the primary (resume) action through `onPlayVideo` and the secondary (restart) action through `onPlayVideoFromStart`; both must run the existing close-handoff / stop-progress-snapshot path before starting the new item.
  - The detail hero primary play action remains disabled when `streamUrl == null` (e.g. `Series` items); the secondary restart action is disabled whenever the primary is.
- Direct playback controls:
  - Video playback supports fixed relative seek controls: 10 seconds backward and 30 seconds forward.
  - Relative seek commands must resolve to an absolute player position and use the same `seekTo(positionSeconds)` path as the scrubber.
  - Clamp relative seek targets to `0..durationSeconds` when duration is known. When duration is zero or negative, treat it as unknown: clamp only to `>= 0` so skip-forward still advances from the current position.
  - Player timeline UI must not clamp unknown-duration playback to a one-second range. When duration is unknown, the slider maximum should grow to at least the current player position and the duration label should show `--:--`.

- Playback progress reporting:
  - `syncPlaybackProgress(...)` posts to `Sessions/Playing/Progress`; `stopPlaybackProgress(...)` posts to `Sessions/Playing/Stopped`.
  - Requests must include `X-Emby-Token`, `ItemId`, `PositionTicks`, and `IsPaused`.
  - Convert seconds to ticks with the same `10_000_000` ticks-per-second convention used for item duration and resume metadata.
  - Clamp reported positions to `0..durationSeconds` when duration is known, and to at least `0` when duration is unknown.
  - The app shell must snapshot the current `VideoPlaybackState.video` and `positionSeconds` before clearing playback, then use `stopPlaybackProgress(...)` on video close, music handoff, audiobook handoff, and switching to a different video item.
  - The app shell must run a 30-second periodic progress loop while the same playable video remains active and an Emby repository is available.
  - Periodic progress uses `syncPlaybackProgress(video, positionSeconds, isPaused = !state.isPlaying)`.
  - Periodic progress initializes its local baseline with `maxOf(0, state.positionSeconds, video.playbackPositionSeconds)` and reports at least that baseline so an early zero-position player state cannot regress Emby resume metadata.
  - Periodic progress advances the local baseline only after a successful sync. Failed periodic syncs leave the baseline unchanged.
  - Background progress-sync failures must not reopen the video player or block local media handoff.
- Series detail UI:
  - A selected `Series` may derive related episodes from the already-loaded library items.
  - Match episodes by `seriesId == selectedSeries.id`. Use `seriesName == selectedSeries.title` only when the episode `seriesId` is missing or blank, as a fallback for incomplete responses.
  - Sort derived episodes by `seasonNumber`, then `episodeNumber`, then title.
  - Episode rows play the episode item; the series header primary play action remains disabled when `streamUrl == null`.

### 4. Validation & Error Matrix
- Non-2xx response -> throw `EmbyApiException(kind = HTTP, message contains "HTTP <code>")`
- Empty response body -> throw `EmbyApiException(kind = API)`
- Empty 200 JSON responses that fail during Retrofit/Gson conversion must also throw `EmbyApiException(kind = API)`, not a generic wrapped exception.
- API key flow returns no users with a non-blank `Id` -> throw `EmbyApiException(kind = AUTH)`
- Password flow returns missing, null, or blank `AccessToken` -> throw `EmbyApiException(kind = AUTH)`
- Password flow returns missing, null, or blank `User.Id` -> throw `EmbyApiException(kind = AUTH)`
- View response `Items` is missing or null -> map libraries to `emptyList()`, selected library id to `null`, and catalog items to `emptyList()`
- View response item row omits `Id` or `Name`, or sends either as null, empty, or blank -> skip that library row and keep mapping other valid rows
- Library item response `Items` is missing or null -> map that page to `emptyList()` and stop pagination
- Library item response row omits `Id` or `Name`, or sends either as null, empty, or blank -> skip that video row and keep mapping other valid rows
- Library/item row sends `Id` or `Name` with surrounding whitespace -> trim the value before building domain models, image URLs, stream URLs, or follow-up requests
- Library item response fetched row count reaches `TotalRecordCount` while mapped videos are fewer because rows were skipped -> stop pagination without requesting another page
- Missing item `UserData` -> map resume position to `0` and played state to `false`
- Missing `UserData.LastPlayedDate` -> continue-watching shelf keeps the item eligible by resume position but sorts it behind dated resume items
- Resume position at or beyond known duration while `Played == false` -> exclude from continue-watching shelf as effectively complete
- Catalog refresh omits the currently selected video id -> clear selected video detail state
- Catalog refresh still contains the selected video id -> keep detail state using the refreshed `VideoItem`
- Catalog refresh still contains at least one item for the selected type filter -> keep that filter
- Catalog refresh no longer contains any item for the selected type filter -> reset the filter to `All`
- Catalog refresh has an empty item list while `All` is selected -> keep `All`
- Saved config changes while a detail page or browser filter is active -> clear selected detail, search text, and reset type filter to `All` before loading the new config
- Ready saved config replaces another ready config -> call catalog refresh without the previous config's selected library id
- Previous-config catalog or library response completes after saved config changed -> ignore the stale response and keep the new config's state
- Missing item `CommunityRating` -> map rating to `null`; top-rated shelves should ignore it
- `playbackPositionSeconds` at or beyond known duration -> initial playback starts at `0` instead of seeking to the end
- Same current video id and same stream URL -> do not replace the ExoPlayer item.
- Same current video id and different stream URL -> replace the ExoPlayer item and prepare it.
- Different video id -> replace the ExoPlayer item regardless of stream URL.
- Relative video skip requested near the start or end of a known-duration item -> clamp to `0` or `durationSeconds`
- Relative video skip requested while duration is unknown -> clamp negative targets to `0`, but allow positive forward targets
- Video player timeline rendered while duration is unknown -> show the real non-negative position, use a non-empty slider range, and show `--:--` for total duration
- Progress report position below zero -> report `0` ticks
- Progress report position beyond known duration -> report duration ticks
- Progress report position with unknown duration -> keep positive position ticks
- Progress/stopped report non-2xx response -> throw `EmbyApiException(kind = HTTP)`
- Periodic sync current player position behind local baseline -> report the baseline, not the lower player position
- Periodic sync failure -> keep playback UI unchanged and do not advance the baseline
- Missing episode relationship fields -> keep the episode playable, but only show it under a series detail when `seriesId` is missing/blank and the `seriesName` fallback matches
- `Series` item -> `VideoItem.streamUrl == null`; UI must not call playback for the series item directly
- `Episode` item in loaded catalog -> exclude from browse grid, search results, browse type filters, top-rated shelf, and unplayed shelf
- `Episode` item with resume progress -> keep eligible for continue-watching shelf
- Selected type filter is `Episodes` after refresh or app upgrade -> reset to `All` even when episodes exist in the loaded catalog
- Video search query is blank or whitespace -> return `true` so clearing search restores the full filtered catalog
- Video search query is a series title and an episode has matching `seriesName` -> include the episode even when the episode title does not contain the series title
- Video search query is a compact or zero-padded episode code and season/episode numbers are present -> include the episode
- Video search query needs missing season/episode fields -> do not synthesize misleading `S`/`E` tokens
- Unknown repository exceptions -> wrap with user-action context, e.g. `"连接 Emby 失败: ..."`
- Do not classify errors by `message.contains(...)`; callers should catch `EmbyApiException` by type/kind.

### 5. Good/Base/Bad Cases
- Good: API key + username, multiple users, matching user selected, video libraries and items load.
- Good: An Emby-compatible server omits or nulls a view or item-list `Items` array; Nordic returns empty app lists instead of crashing.
- Good: An Emby-compatible server includes partial view or item rows, and the repository skips unusable rows while preserving valid libraries and videos from the same response.
- Good: Emby sends valid `Id` or `Name` values with surrounding whitespace, and Nordic trims them before storing domain ids/titles or building URLs.
- Good: Emby returns `UserData.PlaybackPositionTicks` and `CommunityRating`; repository maps resume/rating metadata and UI can show continue-watching/top-rated/unplayed shelves.
- Good: Emby returns `UserData.LastPlayedDate`; continue watching prioritizes recently watched items over older items with larger resume positions.
- Good: User starts an unfinished continue-watching item; playback seeks to the Emby resume position before playing.
- Good: User replays a refreshed Emby item with the same id but a changed stream URL; playback replaces ExoPlayer so the fresh URL is used.
- Good: User refreshes a video library while viewing details; if the item still exists, detail metadata updates from the refreshed catalog, and if it disappeared the app returns to the catalog instead of showing stale detail.
- Good: User refreshes after an older app state had selected Episodes; the browser resets to All because episode filtering is not browse-visible.
- Good: User switches Emby server/account from a detail page with an active search/filter; the app clears the old detail/search/filter state and loads the new account from its default library selection.
- Good: User can use video skip controls to quickly jump 10 seconds back or 30 seconds forward without leaving player bounds.
- Good: User closes or switches away from video playback; Nordic sends the stopped position to Emby so the next catalog refresh has current resume metadata.
- Good: User watches a long video session; Nordic periodically sends progress so Emby resume metadata stays fresh before close.
- Good: A TV library returns both a `Series` item and its `Episode` items; the browse grid shows the `Series` once, series detail shows sorted episode rows, and tapping an episode plays the episode stream.
- Good: Searching a show name in the video browser returns the matching `Series` item without returning its episode rows.
- Good: Searching `S01E02` returns the episode whose season is `1` and episode is `2`.
- Base: Username/password login, one video library, empty item list, UI shows an empty media-library state.
- Base: Older/incomplete Emby responses omit `UserData` and `CommunityRating`; catalog still loads and spotlight shelves simply omit unavailable groups.
- Base: A `Series` item has no matching loaded episodes; detail still shows metadata/overview and disables primary playback.
- Bad: Video browser search runs against the full loaded list and returns episode rows when the user searches the show name.
- Bad: `EmbyItemsResponse.Items` is modeled as a non-null Kotlin list and repository mapping calls `.filter`/`.map` directly, allowing Gson-omitted fields to become runtime nulls.
- Bad: `EmbyItemDto.Id` or `Name` is modeled as a non-null Kotlin string and mapped directly, allowing compatible partial rows to crash catalog browsing.
- Bad: Item pagination compares `TotalRecordCount` to mapped video count after filtering, causing extra page requests when unusable rows were already included in the fetched total.
- Bad: Video playback compares only `VideoItem.id` and keeps an expired same-item stream URL.
- Bad: Emby returns HTTP 500 for views, repository throws `EmbyApiException.Kind.HTTP` and UI shows the error card.
- Bad: Server has music and movie collections, repository filters out music by `CollectionType`.
- Bad: TV library browsing renders `Episode` items, flattening a show into individual episode cards instead of Series cards.
- Bad: Keeping `VideoTypeFilter.Episodes` visible in browse controls; users should enter a `Series` detail page to choose episodes.

### 6. Tests Required
- Readiness:
  - API key alone is enough with server URL
  - username/password is enough with server URL
  - Plex/WebDAV config is not ready for Emby sync
- API key flow:
  - asserts `GET /Users` and `X-Emby-Token`
  - asserts matching/first user behavior when applicable
  - asserts returned users with missing, null, empty, or blank `Id` are not selected
  - asserts responses with no usable user ids throw `EmbyApiException.Kind.AUTH`
- Password flow:
  - asserts `POST /Users/AuthenticateByName`
  - asserts `Username` and `Pw` body fields
  - asserts later requests use `AccessToken`
  - asserts missing, null, and blank `AccessToken` responses throw `EmbyApiException.Kind.AUTH`
  - asserts missing, null, and blank `User.Id` responses throw `EmbyApiException.Kind.AUTH`
- Mapping:
  - asserts non-video libraries are filtered
  - asserts missing and null view response `Items` map to an empty library list, null selected library id, and empty catalog item list
  - asserts missing and null library item response `Items` map to an empty item list and stop pagination
  - asserts view rows with missing/null/blank `Id` or `Name` are skipped without dropping valid video library rows
  - asserts item rows with missing/null/blank `Id` or `Name` are skipped without dropping valid video item rows
  - asserts valid library and item rows with surrounding whitespace in `Id` or `Name` map to trimmed domain values
  - asserts item pagination stops when fetched row count reaches `TotalRecordCount`, even when mapped videos are fewer because rows were skipped
  - asserts video library `CollectionType` and blank-collection `CollectionFolder` fallback matching are case-insensitive
  - asserts duration ticks become seconds
  - asserts `Fields` requests `UserData` and `CommunityRating`
  - asserts `UserData.PlaybackPositionTicks`, `UserData.Played`, and `CommunityRating` map to `VideoItem`
  - asserts `UserData.LastPlayedDate` maps to `VideoItem.lastPlayedDate`
  - asserts browse catalog filtering excludes `Episode` from grid/search/type filters/top-rated/unplayed while keeping episodes in continue-watching and series detail
  - asserts continue-watching shelf sorting uses last-played recency before resume-position fallback
  - asserts continue-watching shelf excludes resume positions at or beyond known duration, while keeping unknown-duration resume items eligible
  - asserts selected video detail resolution keeps a refreshed matching item and clears selection when the library changes or the item disappears
  - asserts selected video type filter resolution keeps still-present filters, resets unavailable filters to `All`, and keeps `All` for empty catalogs
  - asserts saved config changes clear selected video detail state and reset every type filter to `All`
  - asserts video initial start-position helper uses resume seconds for unfinished items, starts played items at zero, starts at zero for resume positions at/beyond known duration, and preserves positive resume positions when duration is unknown
  - asserts video relative seek helper clamps at the beginning and end of known-duration items, and allows forward seek when duration is unknown
  - asserts video player timeline helpers keep unknown-duration positions visible, keep a non-empty slider range at zero, and format unknown duration as `--:--`
  - asserts playback progress helpers convert seconds to ticks and clamp negative, over-duration, and unknown-duration positions
  - asserts progress/stopped reporting requests use `/Sessions/Playing/Progress` and `/Sessions/Playing/Stopped`, include `X-Emby-Token`, and serialize `ItemId`, `PositionTicks`, and `IsPaused`
  - asserts video periodic progress baseline uses current player position, Emby resume position, and zero without regressing
  - asserts `Fields` requests `SeriesId`, `SeriesName`, `ParentIndexNumber`, and `IndexNumber`
  - asserts `Series` items map `streamUrl` to `null`
  - asserts `Episode` relationship fields map to `VideoItem.seriesId`, `seriesName`, `seasonNumber`, and `episodeNumber`
  - asserts series detail episode derivation uses `seriesName` fallback only when `seriesId` is missing or blank
  - asserts local video search matches an episode by `seriesName`
  - asserts local video search matches compact and zero-padded season/episode tokens
  - asserts local video search treats blank queries as match-all
  - asserts thumbnail URL contains item path, primary tag, and token query
  - asserts missing `ImageTags` maps to a `null` thumbnail instead of crashing catalog loading
  - asserts stream URL contains video stream path, `Static=true`, and token query
  - asserts current video replacement decisions: same id/same stream does not replace, same id/different stream replaces, different id replaces
- Error:
  - asserts non-2xx responses throw typed `EmbyApiException.Kind.HTTP`
  - asserts empty body responses from body-bearing Emby endpoints throw typed `EmbyApiException.Kind.API`

### 7. Wrong vs Correct

#### Wrong
```kotlin
val pageItems = response.items
items += pageItems.map { it.toVideoItem(libraryId, token) }
```

Gson can set omitted `Items` fields to `null`, so direct mapping can crash on empty-compatible Emby responses.

#### Correct
```kotlin
val pageItems = response.items.orEmpty()
items += pageItems.map { it.toVideoItem(libraryId, token) }
```

#### Wrong
```kotlin
data class EmbyItemDto(
    val id: String,
    val name: String
)

items += pageItems.map { item -> item.toVideoItem(libraryId, token) }
startIndex += items.size
```

Non-null row fields and mapped-count pagination let partial rows crash catalog loading or trigger extra page requests after filtering.

#### Correct
```kotlin
data class EmbyItemDto(
    val id: String? = null,
    val name: String? = null
)

items += pageItems.mapNotNull { item -> item.toVideoItem(libraryId, token) }
startIndex += pageItems.size
```

Validate row identity at the repository boundary and keep pagination tied to the server rows fetched.

#### Wrong
```kotlin
val libraries = response.items.filter { it.type == "CollectionFolder" }
```

This includes music and other non-video Emby collections, causing the Video tab to show unrelated libraries.

#### Correct
```kotlin
val libraries = response.items.filter { item ->
    item.collectionType in setOf("movies", "tvshows", "homevideos", "mixed") ||
        (item.collectionType.isNullOrBlank() && item.type == "CollectionFolder")
}
```

This keeps video-first behavior while retaining a compatibility fallback for older or incomplete Emby responses.

#### Wrong
```kotlin
if (state.value.video?.id != video.id) {
    player.setMediaItem(video.toMediaItem())
}
```

This can keep playing a stale Emby URL when the same item id is refreshed with a new server/token stream URL.

#### Correct
```kotlin
if (shouldReplaceCurrentVideoItem(state.value.video, video)) {
    player.setMediaItem(video.toMediaItem())
}
```

This preserves lightweight replay for the same stream while refreshing ExoPlayer when the stream URL changes.

## Scenario: Video Playback Display Modes and Fullscreen

### 1. Scope / Trigger

- 修改视频播放控制层、选集、倍速/信息面板、画面比例、全屏与方向时适用。
- 范围：Media3 画面尺寸、Compose 控制分区、同剧集上下文、本地进度快照和应用外壳的播放切换。
- 不包含：字幕/音轨、清晰度/转码、投屏、画中画、自动连播与重建 Emby URL。

### 2. Signatures

```kotlin
enum class AspectRatioMode { FIT, CROP, FILL }
internal enum class VideoPlayerPanel { Settings, Speed, Info, Episodes }
internal fun resolveVideoPlayerToolLayout(
    availableWidth: Dp, availableHeight: Dp,
    hasEpisodes: Boolean, hasNextEpisode: Boolean,
    fontScale: Float = 1f, hasPlaybackStatus: Boolean = false
): VideoPlayerToolLayout
internal fun useVideoPlayerSidePanel(isFullscreen: Boolean, width: Dp, height: Dp): Boolean
internal fun resolveVideoPlayerEpisodes(current: VideoItem?, videos: List<VideoItem>): List<VideoItem>
internal fun resolveNextVideoEpisode(current: VideoItem, videos: List<VideoItem>): VideoItem?
internal fun shouldPlaySelectedVideoEpisode(current: VideoItem?, selected: VideoItem): Boolean
internal fun updateVideoEpisodeProgress(videos: List<VideoItem>, current: VideoItem, positionSeconds: Int): List<VideoItem>
internal fun resolveVideoOrientationRequest(showVideoPlayer: Boolean, lockedLandscape: Boolean): Int
```

`VideoPlayerScreen` 接收 `episodeContext`、`onPlayEpisode`、`onPlayNextEpisode`、`onSeekRelative`、`onCycleAspectRatio`、`isFullscreen` 和 `onToggleFullscreen`。播放状态由 `VideoPlaybackState` 提供，UI 不持有 ExoPlayer。

### 3. Contracts

- **画面**：`VideoPlaybackEngine` 持有比例模式；`onVideoSizeChanged` 使用 width/height/pixelWidthHeightRatio 发布真实比例。无效尺寸回退 16:9。`SurfaceView` 必须填满 Media3 `AspectRatioFrameLayout`；FIT/CROP/FILL 分别映射 FIT/ZOOM/FILL，不重新创建媒体项或 seek。
- **方向**：应用外壳是系统栏与方向唯一控制器。播放器显示时非全屏锁竖屏、全屏锁横屏；重力不改变方向，不提供独立旋转按钮；关闭恢复 `SCREEN_ORIENTATION_UNSPECIFIED`。全屏隐藏系统栏并允许瞬时滑出，退出恢复。Activity 保留方向/尺寸 configChanges。
- **控制分层**：上方关闭、标题、更多；有足够高度时中间播放/暂停及后退 10 秒/前进 30 秒；下方时间线、倍速和工具。中央操作区不足 280dp 高或显示加载/错误时，播放按钮移到底栏，避免与状态提示重叠。
- **实际占位**：工具按钮有效目标至少 48dp，主播放按钮 72dp。`resolveVideoPlayerToolLayout` 读取扣除安全区与边距后的实际宽高和 fontScale，先预留倍速、全屏、选集及必要的播放按钮，再容纳下一集/比例。放不下的低频项进入“播放设置”，不得按“无下一集就只剩一个侧按钮”的假设缩小或裁切控件。
- **时间线**：展示已播/总时长、剩余时长及缓冲进度；未知时长显示 `--:--`，滑轨上限至少包含当前位置。复用 `PlayerThinSlider`，视频通过 modifier 扩大至 48dp 触控高度，取消拖动不 seek。
- **显示/交互**：控制层初次显示，播放时无操作 4 秒后隐藏；暂停、拖动、状态提示、面板打开与临时倍速时不自动隐藏。按钮交互重置计时。错误/缓冲提示独立于控制层。
- **面板**：`VideoPlayerPanel` 单一状态，不能同时打开多个面板；同窗口布局保持系统栏控制权不变。全屏且宽度至少 600dp、宽大于高时使用侧面板，否则使用底部面板。面板和 chrome 使用 `WindowInsets.safeDrawing`，全屏也避让刘海。
- **手势**：识别器只附着视频 surface 容器，与控制层/面板是兄弟节点；打开面板禁用底层播放手势、隐藏其可访问性节点。回调使用 `rememberUpdatedState`，不得捕获上一集的 `remember(video.id)` 状态。手势锁仍只禁用播放手势，按钮不因此失效；隐藏 chrome 后保留解锁入口。
- **返回优先级**：面板自身 BackHandler 优先关闭面板；再解手势锁、退出全屏，最后调用应用外壳关闭。不要让底层导航抢先退出播放器。
- **剧集来源**：选集与下一集共用 `resolveVideoPlayerEpisodes`。当前项优先于旧目录副本，按 id 去重、按季/集/标题排序；只允许同 libraryId、同剧集的 Episode。双方 seriesId 非空时必须相同；缺失 ID 时可用非空 seriesName 忽略大小写匹配；无剧集身份只保留当前项，不混合所有无名剧集。
- **选集呈现**：按季查看，0 季显示“特别篇”，null 显示“未分季”；初次定位当前季/集，高亮当前播放项。复用 `VideoEpisodeRow(isCurrent, compact)` 与已看/续播展示，稳定列表 key 为 id。电影不显示选集，最后一集仍能选集；上下文只有当前项时明确“暂无其他已载入剧集”，不虚构全集、不自动联网补库。
- **切换**：点击当前集仅收起面板；无 streamUrl 的项禁用。新选集及下一集都经 MainScreen 的 `onPlayVideo` / `runMediaHandoff`，先快照/关闭原项，再启动目标，不能直接 `videoVM.play(next)` 绕过进度保存。切换前记录全屏意图，启动目标后恢复 fullscreen 与方向锁状态。
- **本地进度**：ViewModel 关闭时用 `updateVideoEpisodeProgress` 更新内存剧集上下文，沿用 `resolveVideoProgressSyncBaselineSeconds` 的启动零值保护；用户在服务器目录刷新前切回同集也能续播。不改变已看标记、持久缓存或现有后台上报/重试语义。
- **片尾提示**：有可播放下一集且进入最后 30 秒时，仅在控制层/面板隐藏、未锁定、无播放状态异常且未手动取消时出现；放在顶部边缘避开时间线与常见字幕区，点击才播放，不能写“即将播放”暗示自动连播。
- **语义**：图标动作提供中文 contentDescription/Role；字幕、音轨、清晰度等没有真实能力的数据入口不得展示为可用功能。

### 4. Validation & Error Matrix

| 输入/状态 | 行为 |
| --- | --- |
| 无效视频尺寸 / 未知时长 | 比例安全回退 / 总时长 `--:--`，允许非负相对跳转 |
| 320/360/392/720dp，fontScale 1/1.5/2，有无选集/下一集 | 按实际按钮数预留 48dp 目标，溢出项收纳设置；播放与全屏不丢失 |
| 高度 < 280dp 或缓冲/错误 | 播放操作位于底栏，中央留给状态信息 |
| 横屏面板空间不足 | 回退底部面板，不用过窄侧栏 |
| 打开面板后返回/拖动列表 | 只关闭/滚动面板，不 seek、不改音量、不退出全屏 |
| 当前集不在目录或目录含重复项 | 加入真实当前项且去重；不因 stale copy 丢失 streamUrl |
| 同名但双方 seriesId 不同 / libraryId 不同 | 排除，不串剧或跨库 |
| 当前集/不可播放项被选择 | 当前集只关闭面板；不可播放项无播放请求 |
| 切换剧集并切回 | 走原进度快照路径，使用更新的本地续播点，保留全屏 |
| 显示控制栏、打开面板、锁定或取消片尾提示 | 不展示重复的下一集浮层 |

### 5. Good/Base/Bad Cases

- Good：窄屏仅收纳比例/下一集到更多，所有可见动作仍有足够触控面积。
- Good：在全屏选择第二季的某集后仍横屏；再选回上一集，进度来自最近本地快照。
- Base：电影只有基础播放工具，无选集和下一集；剧集只有当前数据时仍可查看当前项。
- Bad：为有无下一集设置对称侧宽，却忘记左边始终存在两枚按钮。
- Bad：选集直接调用 engine 或 VM 的 play，跳过旧项停止进度上报。
- Bad：按空 seriesName 把无名条目合并；使用旧 callback 向已废弃的 Compose 状态写入。

### 6. Tests Required

- `VideoPlayerScreenTest`：实际操作占位矩阵、紧凑回退、侧/底面板判定、片尾提示互斥、比例映射/时间线边界。
- `VideoPlayerEpisodesTest`：身份/库隔离、缺失 ID 回退、去重与排序、当前项定位、季标签、同集/不可播放不重启。
- `VideoEpisodeProgressTest`：切换后的内存续播点、启动零值保护、旧副本替换、电影不污染剧集上下文。
- 保留 Engine 比例/初始续播和 MainActivity 方向/跨媒体 handoff 测试。
- 运行 Kotlin 编译、受影响单元测试、lint 与 debug assemble。`VideoPlayerPreviewActivity` 仅位于 `src/debug`，使用离线合成数据检查横竖屏、面板/点击和状态，不访问真实账号；真实服务器上报仍需单独集成验证。

### 7. Wrong vs Correct

```kotlin
// Wrong: no stopped-progress snapshot and no local episode progress update.
onPlayNextEpisode = { videoVM.play(next) }

// Correct: reuse the app-shell handoff for both picker and Next.
onPlayNextEpisode = { next?.let(onPlayEpisode) } // onPlayEpisode = onPlayVideo
```

```kotlin
// Wrong: holds a callback writing to the previous episode's state.
pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }) }

// Correct: gesture lifetime is stable, callback state is current.
val currentToggle by rememberUpdatedState(onToggleControls)
pointerInput(Unit) { detectTapGestures(onTap = { currentToggle() }) }
```

## Scenario: Media URL Auth Header and Disk Cache Hygiene

### 1. Scope / Trigger
- Trigger: Any change to Emby stream/image URL construction, `EmbyRepository` session/token handling, `MusicPlaybackService`/`VideoPlaybackEngine` HTTP clients, Coil `ImageLoader`, or `AuthedAsyncImage`.
- Auth tokens must not reach disk via ExoPlayer `SimpleCache` or Coil disk cache. Emby media endpoints accept the `X-Emby-Token` header (already proven on JSON API), so the token moves out of the URL entirely.

### 2. Signatures
- `internal const val EMBY_HEADER_AUTH_ENABLED: Boolean` (`EmbyRepository.kt`)
- `internal fun stripAuthQuery(url: String): String` (`data/AuthUrl.kt`) — shared by ExoPlayer `CacheKeyFactory` and Coil `diskCacheKey`
- `internal object MediaAuthHeaderRegistry` + `internal class MediaAuthHeaderInterceptor` (`data/AuthUrl.kt`) — global registry keyed by `"$host:$port"` origin, shared by all media OkHttp clients
- `internal fun AuthedAsyncImage(url: String?, ...)` (`ui/AuthedAsyncImage.kt`) — wraps Coil `AsyncImage` with `diskCacheKey`/`memoryCacheKey` = `stripAuthQuery(url)`

### 3. Contracts
- When `EMBY_HEADER_AUTH_ENABLED=true` (default), `EmbyRepository.primaryImageUrl`/`streamUrl` build the URL path + non-auth params (`maxWidth`, `quality`, `tag`, `Static`) WITHOUT the `api_key` query. The session token is registered into `MediaAuthHeaderRegistry` under the Emby base-URL origin as header `X-Emby-Token` when a session is established.
- When `EMBY_HEADER_AUTH_ENABLED=false` (smoke-test-failure fallback), the URL adds `api_key=<token>` query as before; `stripAuthQuery` still keeps the disk cache key clean, so disk hygiene is preserved either way.
- `MusicPlaybackService`, `VideoPlaybackEngine`, and the Coil `ImageLoader` (in `MainActivity`) OkHttp clients add `MediaAuthHeaderInterceptor`, which injects the registered `X-Emby-Token` for requests whose `host:port` matches a registered Emby origin.
- The Emby stream/image URL path segments (`Items/{id}/Images/Primary`, `Videos/{id}/stream`) and non-auth query params (`maxWidth=640`, `quality=90`, `tag=<primaryTag>`, `Static=true`) are unchanged; only the auth channel moves from query to header.
- `MediaAuthHeaderInterceptor` matches by origin (`host:port`), so an Emby token is never injected into an ABS or Navidrome request (different origins).
- `stripAuthQuery` is used ONLY as the `CacheKeyFactory`/`diskCacheKey` value; the actual request URL is unchanged for Navidrome (query auth) and is auth-free for Emby (header auth).

### 4. Validation & Error Matrix
| Condition | Behavior |
|---|---|
| Emby stream/image request, header auth enabled | URL has no `api_key`; interceptor injects `X-Emby-Token`; cache key strips nothing extra (URL already clean) |
| Emby stream/image request, header auth disabled (fallback) | URL has `api_key=<token>`; cache key strips `api_key`; token stays in network URL only |
| Smoke test rejects `X-Emby-Token` on stream/image | Set `EMBY_HEADER_AUTH_ENABLED=false`; playback falls back to `api_key` query; disk cache key still clean |
| Coil image request for Emby cover | `AuthedAsyncImage` sets `diskCacheKey=stripAuthQuery(url)`; interceptor injects `X-Emby-Token` |
| `MediaAuthHeaderRegistry` cleared between repository test runs | `@Before`/`@After` call `MediaAuthHeaderRegistry.clear()` to prevent cross-test token leakage |

### 5. Good/Base/Bad Cases
- Good: `exo_player_cache` `cached_content_index` and Coil disk cache contain only de-authed Emby URLs; tokens never reach disk.
- Base: A server rejects header auth; `EMBY_HEADER_AUTH_ENABLED=false` keeps playback working with `api_key` query while the clean cache key still protects disk.
- Bad: `stripAuthQuery` is applied to the actual request URL (not just the cache key); Emby requests lose auth and 401 because there is no query fallback when header auth is enabled.
- Bad: A repository test does not clear `MediaAuthHeaderRegistry`; an Emby token from a prior test leaks into the next test's ABS request.

### 6. Tests Required
- `AuthUrlTest`: `stripAuthQuery` removes `api_key`/`token`/`u`/`t`/`s`/`v`/`c`, preserves non-auth params, handles no-query/clean/blank/non-URL; `MediaAuthHeaderRegistry` register/lookup/overwrite/clear/origin isolation; `MediaAuthHeaderInterceptor` injects registered header, skips when absent, does not overwrite an existing header.
- `EmbyRepositoryTest`: stream/image URL does NOT contain `api_key=` when header auth enabled; `MediaAuthHeaderRegistry` holds `X-Emby-Token: <token>` for the Emby origin after `getCatalog`; `@Before`/`@After` clear the registry.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Token stays in the URL and reaches the disk cache index.
addQueryParameter("api_key", token)
```

#### Correct
```kotlin
// Token moves to a header injected by the shared interceptor; URL stays clean.
if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token)
// and register X-Emby-Token into MediaAuthHeaderRegistry when session() resolves.
```

## Scenario: Emby Backdrop Image URL Mapping

### 1. Scope / Trigger
- Trigger: The video detail screen leads with a full-bleed 16:9 backdrop hero instead of a centered portrait poster.
- Scope: `EmbyItemDto` backdrop image-tag fields, `Fields` query extension, `EmbyRepository` backdrop URL builder, episode→series backdrop fallback chain, and repository tests.
- Out of scope: Thumb / Logo image fetching (documented as a follow-up), multiple-backdrop carousel, backdrop image caching policy.

### 2. Signatures
- DTO fields on `EmbyItemDto`:
```kotlin
@SerializedName("BackdropImageTags") val backdropImageTags: List<String>? = null
@SerializedName("ParentBackdropItemId") val parentBackdropItemId: String? = null
@SerializedName("ParentBackdropImageTags") val parentBackdropImageTags: List<String>? = null
```
- `VideoItem` field:
```kotlin
val backdropImageUrl: String? = null
```
- Repository helper:
```kotlin
private fun backdropImageUrl(itemId: String, token: String, backdropTag: String?): String?
```
- `Fields` query default appends `BackdropImageTags,ParentBackdropImageTags` to the existing comma-delimited list.

### 3. Contracts
- `BackdropImageTags` is a JSON **array of strings** (separate from `ImageTags`, which is a `Map<String,String>` keyed by single-image type names). `Backdrop` is **never** a key in `ImageTags` because Backdrop allows multiple images.
- The backdrop URL follows the Primary URL pattern: `/Items/{itemId}/Images/Backdrop?maxWidth=1280&quality=90&tag={tag}`. `maxWidth=1280` (vs `640` for Primary) because a full-bleed hero needs more pixels than a grid thumbnail.
- Auth follows the existing `EMBY_HEADER_AUTH_ENABLED` contract: the URL omits `api_key` when header auth is enabled (default); `MediaAuthHeaderInterceptor` injects `X-Emby-Token`. No new auth plumbing is needed.
- `tag` is included for cache hygiene (strong cache headers), matching the Primary pattern.
- The fallback chain resolves in `EmbyItemDto.toVideoItem(...)` in priority order:
  1. Own `BackdropImageTags[0]` (non-blank) → `backdropImageUrl(itemId, token, ownTag)` — Movie / Series case.
  2. `ParentBackdropImageTags[0]` (non-blank) + non-blank `ParentBackdropItemId` → `backdropImageUrl(parentBackdropItemId.trim(), token, parentTag)` — Episode inherits Series backdrop.
  3. Non-blank `SeriesId` → build `/Items/{SeriesId.trim()}/Images/Backdrop?maxWidth=1280&quality=90` with **no `tag`** (Emby still serves the image; weak caching is the last-resort tradeoff).
  4. Otherwise → `backdropImageUrl = null`; the UI renders the gradient placeholder (`primary(0.18f) → secondary(0.10f) → surfaceVariant(0.82f)`).
- Blank/whitespace-only tags are treated as absent at every branch (use `takeIf { it.isNotBlank() }`).
- `ParentBackdropItemId` and `SeriesId` are trimmed before being inserted into the URL path, matching the existing id-trim contract.

### 4. Validation & Error Matrix
- Own `BackdropImageTags` non-empty with non-blank first tag → use own tag + own id.
- Own `BackdropImageTags` missing/null/empty → fall through to parent.
- `ParentBackdropImageTags` non-empty + non-blank `ParentBackdropItemId` → use parent tag + parent id.
- `ParentBackdropImageTags` missing/null/empty but `SeriesId` non-blank → use SeriesId, no tag.
- `SeriesId` blank/null and no own/parent backdrop → `backdropImageUrl = null`.
- First tag blank (`""` or `"   "`) → skip to next branch; do not build a URL with an empty `tag`.
- `ParentBackdropItemId` blank while `ParentBackdropImageTags` is non-empty → skip to SeriesId branch; do not build a URL with an empty id segment.
- Backdrop URL must NOT contain `api_key=` when `EMBY_HEADER_AUTH_ENABLED=true`.
- Backdrop URL must contain `api_key=<token>` when `EMBY_HEADER_AUTH_ENABLED=false`.

### 5. Good/Base/Bad Cases
- Good: A Movie with `BackdropImageTags: ["b1"]` resolves `backdropImageUrl` to `/Items/{id}/Images/Backdrop?...&tag=b1`.
- Good: An Episode with no own backdrop but `ParentBackdropItemId: "s1"` + `ParentBackdropImageTags: ["sb1"]` resolves to `/Items/s1/Images/Backdrop?...&tag=sb1`.
- Good: An Episode with no own/parent backdrop but `SeriesId: "s1"` resolves to `/Items/s1/Images/Backdrop?maxWidth=1280&quality=90` (no tag).
- Good: An item with no backdrop anywhere resolves to `null` and the UI shows the gradient placeholder.
- Base: A Movie with `BackdropImageTags: [""]` (blank first tag) falls through to the SeriesId/no-backdrop path instead of building a broken URL.
- Bad: Reading `imageTags["Backdrop"]` — Backdrop is never a key in `ImageTags`; it always returns null.
- Bad: Building the backdrop URL with `api_key` in the query while header auth is enabled (token leaks into the disk cache key).
- Bad: Skipping the SeriesId no-tag fallback and returning null when parent tags are missing but SeriesId is present (misses a cheap image render).

### 6. Tests Required
- Repository unit test asserting the `Fields` query parameter contains `BackdropImageTags` and `ParentBackdropImageTags`.
- Repository unit test asserting an item with own `BackdropImageTags: ["b1"]` maps to a backdrop URL containing `/Items/{id}/Images/Backdrop` and `tag=b1`.
- Repository unit test asserting an episode with `ParentBackdropItemId` + `ParentBackdropImageTags` maps to a backdrop URL using the parent id and parent tag.
- Repository unit test asserting an item with `SeriesId` but no own/parent backdrop maps to a backdrop URL containing the series id with no `tag` query.
- Repository unit test asserting an item with no backdrop data anywhere maps to `backdropImageUrl = null`.
- Repository unit test asserting the backdrop URL does not contain `api_key=` when `EMBY_HEADER_AUTH_ENABLED=true`.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Backdrop is NOT a key in ImageTags — this always returns null.
val backdropTag = imageTags.orEmpty()["Backdrop"]
```

```kotlin
// Token leaks into the disk cache key when header auth is enabled.
addQueryParameter("api_key", token)
addQueryParameter("tag", backdropTag)
```

```kotlin
// Blank tag becomes tag= in the URL.
val tag = backdropImageTags?.firstOrNull() // "" survives
backdropImageUrl(itemId, token, tag)
```

#### Correct
```kotlin
// Backdrop tags live in the separate BackdropImageTags array; blank tags are skipped.
val ownTag = backdropImageTags.orEmpty().firstOrNull()?.takeIf { it.isNotBlank() }
val parentTag = parentBackdropImageTags.orEmpty().firstOrNull()?.takeIf { it.isNotBlank() }
val resolved = when {
    ownTag != null -> backdropImageUrl(itemId, token, ownTag)
    parentTag != null && !parentBackdropItemId.isNullOrBlank() ->
        backdropImageUrl(parentBackdropItemId!!.trim(), token, parentTag)
    !seriesId.isNullOrBlank() -> backdropUrlNoTag(seriesId!!.trim(), token)
    else -> null
}
```

```kotlin
// Token stays out of the URL when header auth is enabled.
private fun backdropImageUrl(itemId: String, token: String, tag: String?): String? {
    if (tag.isNullOrBlank()) return null
    return baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("Items").addPathSegment(itemId)
        .addPathSegment("Images").addPathSegment("Backdrop")
        .addQueryParameter("maxWidth", "1280")
        .addQueryParameter("quality", "90")
        .addQueryParameter("tag", tag)
        .apply { if (!EMBY_HEADER_AUTH_ENABLED) addQueryParameter("api_key", token) }
        .build().toString()
}
```

## Scenario: 视频画中画生命周期

### 1. 范围 / 触发条件

修改 PiP 进入、退出、Activity 生命周期、视频结束状态、播放器小窗 UI 或 PiP 设置时读取。本合同与上面的进度上报、全屏方向合同共同生效；音乐/有声书的后台播放不随视频改变。

### 2. 关键签名

- `VideoPictureInPicture.kt`：`resolveVideoPipBridgeState(showVideoPlayer, pipEnabled, playbackState, externalError): VideoPipBridgeState`、`resolveVideoPipAspectRatio(ratio: Float): Pair<Int, Int>`。
- `VideoPlaybackState.playWhenReady: Boolean`、`hasEnded: Boolean` 分别来自 Media3 的 `playWhenReady` 与 `playbackState == Player.STATE_ENDED`。
- `MainActivity.updateVideoPipState(state)`、`isInVideoPipMode`（Compose 可观察）、`dismissVideoPip()`。
- `VideoPlayerScreen(..., pipEnabled, onTogglePip, isInPipMode)`；存储合同见 [Persistence Guidelines](./database-guidelines.md) 的“播放偏好与配置订阅”。

### 3. 可执行合同

- Manifest 必须声明 `supportsPictureInPicture="true"` 并处理 `orientation|screenSize|smallestScreenSize|screenLayout`。`minSdk 26` 不等于所有设备支持 PiP；调用前检查 `FEATURE_PICTURE_IN_PICTURE`。
- 进入资格为：播放器可见、开关开启、有播放地址、播放器与外部错误均为空白、未结束，并且 `isPlaying || (isBuffering && playWhenReady)`。不能单独依赖 `isPlaying`，也不能把已暂停的缓冲当作继续播放。
- API 26–30 通过 `onUserLeaveHint()` 主动进入，已有 PiP 不重复调用。API 31+ 在 Home 手势**之前**通过 `setPictureInPictureParams` 发布 `setAutoEnterEnabled(资格)`，不再重复主动进入。暂停/出错/关闭/禁用后必须发布 false。
- 参数只随资格与比例变化更新，不随进度秒数更新。仅在系统成功应用后缓存“已应用”参数，`onResume` 重试过渡期间拒绝的更新，防止遗留自动进入资格。
- PiP 比例在 `1:2.39..2.39:1` 内；超窄/超宽钳制到 `100/239` 或 `239/100`，非有限值及非正值回退 16:9。小窗使用 FIT，不改变用户保存的全屏显示模式。
- 可见 PiP 的生命周期是 STARTED；不能在 `ON_PAUSE` 停止。`onPictureInPictureModeChanged(false)` 也会在展开回应用时触发，只更新可观察模式，**不能据此关播放器**。
- `ON_STOP` 且非配置重建才走 `closeVideoPlayback`：先快照视频/位置，再停止本地播放和隐藏 UI，后台 best-effort 上报 Stopped（失败重试一次）。不要额外同时发送 Progress 与 Stopped；配置重建保留 `syncNow`。
- PiP 自然结束使用 `hasEnded`，不能比较整数秒与 Emby 元数据时长。先关闭播放，再 `moveTaskToBack(true)` 退出 pinned 小窗；不要 `finish()` 单 Activity 并取消仍在上报的 ViewModel 协程。
- PiP 中保留同一个视频 Surface 和字幕节点；控制、scrim、面板、下一集/手势提示与手势处理退出组合或禁用。进入时取消 scrub/临时倍速，展开后恢复控制，不重建播放会话。方向与系统栏仍只由 MainScreen 单一控制器负责，PiP 期间不重新写方向。

### 4. 验证与错误矩阵

| 条件 | 行为 |
|---|---|
| 播放中 / 请求播放的缓冲中离开 | 允许 PiP，持续播放与周期上报 |
| 暂停 / 错误 / 无可播放视频 / 开关关闭 | 撤销自动进入资格 |
| 设备不支持 / 系统不允许进入 | 不崩溃，实际 ON_STOP 后停止并上报 |
| 小窗展开回应用 | 继续当前视频、原进度和全屏方向，不触发 Stopped |
| 系统关闭小窗 / 无 PiP 进入后台 | ON_STOP 立即关闭，不等待网络 |
| 配置重建 | 只同步，不当作主动关窗 |
| STATE_ENDED，时长未知或元数据偏大 | 关闭小窗，不自动连播 |
| 只更新 positionSeconds | PiP 参数不变 |

### 5. 正常 / 基础 / 错误案例

- 正常：全屏看剧 → Home 小窗 → 展开继续 → 再次 Home → 关闭小窗；最后一次操作才结束播放并上报停止位置。
- 基础：从未设置开关时默认开启；设备禁用 PiP 时维持视频后台即停。
- 错误：在模式 false 回调中 stop 导致展开即停；在 onUserLeaveHint 才设置 Android 12 自动进入；只隐藏页面却不退出 pinned 小窗；用 finish 取消停止上报。

### 6. 必需测试

- `VideoPictureInPictureTest`：资格开关/可见性、请求播放与暂停缓冲、两种错误源、无 URL、真实结束与未知时长、进度不影响参数、常规/异常/极端比例及平台区间扫描。
- `EncryptedConfigStoreTest`：开关默认 true、往返存取、新 store 恢复 false 与订阅注册竞态。
- compile、完整单测、lint、assemble；自动检查不能代替 API 26–30 / 31+ 真机的 Home、展开、关窗、结束、权限禁止、字幕与实际 Emby 上报检查。

### 7. 错误与正确示例

```kotlin
// 错误：展开小窗也会走到这里。
if (!isInPictureInPictureMode) closeVideoPlayback()

// 正确：模式回调只发布状态；实际停止生命周期才关闭。
isInVideoPipMode = isInPictureInPictureMode
// ON_STOP 且 !isChangingConfigurations -> closeVideoPlayback()
```

```kotlin
// 错误：实际媒体结束时，元数据 duration 可能更大或未知。
if (positionSeconds >= durationSeconds) closeVideoPlayback()

// 正确：由引擎发布真实 STATE_ENDED，小窗关闭不 finish Activity。
if (isInPipMode && showVideoPlayer && videoPlaybackState.hasEnded) {
    closeVideoPlayback()
    activity?.dismissVideoPip()
}
```

## Scenario: 视频章节与片头跳过

### 1. 范围 / 触发条件

修改视频章节列表、章节跳转、片头（intro）区间解析、自动跳过或「跳过片头」按钮时读取。数据源与 PiP/进度上报合同共同生效；音乐/有声书不受影响。

### 2. 关键签名

- `EmbyApi.getItems` 的 `Fields` 必须包含 `Chapters`；`EmbyChapterDto(name, startPositionTicks, markerType)`，`markerType` 可空（老服务器无此字段）。
- `data class VideoChapterInfo(name, startSeconds, endSeconds: Int?)`、`data class VideoIntroRange(startSeconds, endSeconds)`（`EmbyRepository.kt`）。
- `VideoItem.chapters: List<VideoChapterInfo>`、`VideoItem.introRange: VideoIntroRange?`；`VideoPlaybackState.introRange` 由引擎在 `play`/`playFromStart` 替换媒体项时发布。
- `internal fun shouldSkipVideoIntro(positionSeconds, introRange, alreadySkipped): Boolean`（`VideoPlaybackEngine.kt`）。
- `VideoPlaybackEngine.skipIntro()`、`applyAutoSkipIntro(enabled)`；`VideoPlaybackViewModel.autoSkipIntro: StateFlow<Boolean>`、`setAutoSkipIntro(Boolean)`、`skipIntro()`。
- `VideoPlayerPanel.Chapters`；`internal fun videoChapterIndexForPosition(chapters, positionSeconds): Int?`、`resolveVideoChaptersSummary(video, positionSeconds)`、`shouldShowVideoSkipIntroButton(introRange, positionSeconds, panelOpen, gesturesLocked, hasPlaybackStatus)`（`VideoPlayerLayout.kt` / `VideoPlayerPanels.kt`）。
- 存储合同：`EncryptedConfigKeys.VIDEO_AUTO_SKIP_INTRO`，Flow/save 先例与 PiP 相同（见 [Persistence Guidelines](./database-guidelines.md)）。

### 3. 可执行合同

- **数据源**：Emby 4.9 的 intro 检测结果直接内嵌在 item 的 `Chapters` 数组中，以 `MarkerType=IntroStart/IntroEnd`（及 `CreditsStart`）marker 形式出现；不存在 `/MediaSegments/{id}` 端点（Jellyfin 专属），`/Items/Intros` 是管理员 debug 端点不可用。intro 区间 = 第一个 `IntroStart` 与其后第一个 `IntroEnd` 的 tick→秒；不成对或 end <= start 时无 intro。
- **章节映射**：仅 `markerType == null || "Chapter"` 的条目进入章节列表；`Name` 空白或 `StartPositionTicks` 缺失/负数的行丢弃；`endSeconds` 由下一章 start 推导，最后一章为 null。丢弃行不参与 endSeconds 推导。
- **自动跳过**：引擎在 `publishPlayerState`（播放时 1s 节奏）判定 `shouldSkipVideoIntro`；命中即 seek 到 `endSeconds` 并置 `introSkippedForCurrentItem = true`。该标志在每次媒体项替换（`play`/`playFromStart` 且 `shouldReplaceCurrentVideoItem`）时重置，同一集重播可再次跳过。手动 seek 出区间后 position >= end，判定自然为 false，不回跳。
- **手动跳过**：`skipIntro()` 与自动跳过共享同一 once 标志，互不重复触发；不受 `autoSkipIntroEnabled` 限制。
- **UI**：Settings 面板仅当 `state.introRange != null` 显示「自动跳过片头」开关行（`VideoPlayerSettingToggleRow`），仅当 `video.chapters.isNotEmpty()` 显示「章节」入口行；章节面板复用 `MediaPlayerChoiceRow`（当前章节高亮，点击 `seekTo(startSeconds)` 并关闭面板）；「跳过片头」浮动按钮仅在区间内且无面板/手势锁/错误状态时显示，PiP 小窗不组合任何面板与按钮。
- **降级**：请求失败、字段缺失、老服务器无 MarkerType 时静默降级为无章节/无 intro，不影响播放主链路。

### 4. 验证与错误矩阵

| 条件 | 行为 |
|---|---|
| `MarkerType` 缺失（老服务器） | 全部按普通章节处理，无 intro |
| `IntroStart` 无配对 `IntroEnd` | `introRange = null` |
| `IntroEnd` ticks <= `IntroStart` ticks | `introRange = null` |
| 章节行 `Name` 空白 / ticks 缺失或负数 | 丢弃该行，不参与 endSeconds 推导 |
| 播放位置进入 intro 区间（自动跳过开启） | seek 到 endSeconds，每（item, 会话）一次 |
| 恢复播放起点在区间内 | 同样触发跳过（position >= start 即命中） |
| 用户手动 seek 越过区间 | 不回跳（position >= end 判定 false） |
| 自动跳过关闭 | 无自动 seek；手动按钮仍可用 |
| 无 intro 数据 | 无开关行、无按钮、无自动跳过 |
| PiP 小窗 | 不显示按钮与面板 |

### 5. 正常 / 基础 / 错误案例

- 正常：播放带「片头/片尾」marker 的剧集 → 进入区间自动跳一次 → 片中手动 seek 回片头不再自动跳 → 手动按钮仍可点。
- 基础：电影只有普通章节 → 章节面板可跳转，无 intro 开关与按钮。
- 错误：把 `MarkerType` 缺失当作 intro；把 intro marker 混进章节列表；用章节名（「片头」）而非 MarkerType 判定区间；自动跳过在每次位置发布重复 seek。

### 6. 必需测试

- `EmbyRepositoryTest`：Chapters+marker 映射（过滤 intro marker、丢弃空名/缺 ticks 行、endSeconds 推导）、intro 无 end/逆序为 null、`Fields` 含 `Chapters`。
- `VideoPlaybackEngineTest`：`shouldSkipVideoIntro` 区间边界、once 语义、null/逆序区间。
- `VideoScreenTest`：`videoChapterIndexForPosition` 覆盖判定、`resolveVideoChaptersSummary` 当前章/计数、`shouldShowVideoSkipIntroButton` 状态矩阵。
- `EncryptedConfigStoreTest`：`videoAutoSkipIntro` 默认 true、往返、新 store 恢复 false、非法值默认 true。
- compile + 完整单测 + lint + assemble；真机验收需覆盖真实 Emby 4.9 服务器的章节跳转与 intro 自动跳过。

### 7. 错误与正确示例

```kotlin
// 错误：按章节名判定 intro，服务器命名不可依赖。
chapters.filter { it.name == "片头" }

// 正确：按 MarkerType 配对判定，缺失/逆序一律降级。
val start = chapters.indexOfFirst { it.markerType == "IntroStart" }
val end = chapters.drop(start + 1).firstOrNull { it.markerType == "IntroEnd" }
```

```kotlin
// 错误：每次位置发布都 seek，播放卡死在区间末尾。
if (position in introRange) player.seekTo(intro.endSeconds)

// 正确：once 标志 + 纯函数判定；seek 后下一次发布自然落在区间外。
if (shouldSkipVideoIntro(position, introRange, alreadySkipped)) {
    introSkippedForCurrentItem = true
    player.seekTo(introRange.endSeconds * 1000L)
}
```

## Scenario: 视频 PlaybackInfo 转码与清晰度选择

### 1. 范围 / 触发条件

修改视频清晰度档位、PlaybackInfo 握手、转码 URL 构造、转码失败回退或转码会话上报时读取。与直连播放、进度上报、章节/intro 合同共同生效。

### 2. 关键签名

- `enum class VideoQualityMode(val label: String, val bitrateBps: Long?) { AUTO, ORIGINAL, BITRATE_2M, BITRATE_4M, BITRATE_8M, BITRATE_20M }` + `fromName(raw): VideoQualityMode`（非法值回退 AUTO）。
- `data class VideoPlaybackSession(playSessionId, mediaSourceId)`；`internal fun resolveVideoPlaybackStreamUrl(video, mode, baseUrl, playbackSession): String?`（`EmbyRepository.kt`）。
- `EmbyRepository.getPlaybackInfo(video, maxBitrateBps): VideoPlaybackSession?`（POST `/Items/{Id}/PlaybackInfo`，带最小 DeviceProfile + UserId；失败抛 `EmbyApiException`，响应缺 PlaySessionId/MediaSourceId 返回 null）。
- `EmbyRepository.baseUrlForStreamUrls(): String`；`syncPlaybackProgress`/`stopPlaybackProgress` 增加可空 `playSessionId` 参数。
- `VideoPlaybackEngine.playTranscoded(video, transcodeUrl, directUrl)`；`VideoPlaybackViewModel.qualityMode: StateFlow<VideoQualityMode>`、`setQualityMode(mode)`。
- 存储合同：`EncryptedConfigKeys.VIDEO_QUALITY_MODE`，默认 AUTO（见 [Persistence Guidelines](./database-guidelines.md)）。

### 3. 可执行合同

- **转码决策在客户端**：Emby 4.9 对 DirectPlay 判定宽松（mkv/H.265 也返回 true）且不返回 `TranscodingUrl`；不得依赖 `SupportsDirectPlay` 做档位判定。档位 bitrateBps 非空时走转码，null（AUTO/ORIGINAL）走直连。
- **转码 URL**：`/Videos/{itemId}/master.m3u8?MediaSourceId=<id>&VideoCodec=h264&AudioCodec=aac&VideoBitrate=<bps>`，认证走 `MediaAuthHeaderRegistry`（X-Emby-Token header）。ExoPlayer 直接消费 master playlist。
- **握手降级**：PlaybackInfo 失败（异常/响应缺字段）→ `activePlaySessionId = null` 并回退直连播放，不阻塞、不报错。
- **播放失败回退**：转码流触发 `onPlayerError` 时，若 `directPlayFallbackUrl` 非空，清空标记并以直连流重建媒体项、seek 到当前进度重试一次；再次失败才走正常错误链路。`play`/`playFromStart`（直连路径）与 `stop()` 必须清空该标记，防止跨项残留。
- **上报**：转码会话的 Progress/Stopped 附服务器返回的 `PlaySessionId`；直连会话为 null（DTO 字段可空，向后兼容）。关闭路径在 `engine.stop()` 后快照 sessionId 再清空。
- **UI**：Settings 面板「清晰度」行仅当 `streamUrl` 非空显示；Quality 面板 6 档 `MediaPlayerChoiceRow`；切换即时持久化，重新播放生效（档位在 `startPlaybackWithQuality` 读取）。

### 4. 验证与错误矩阵

| 条件 | 行为 |
|---|---|
| AUTO / ORIGINAL | 直连 Static 流，行为与历史版本一致 |
| 限码率档 + 握手成功 | master.m3u8 转码流 + PlaySessionId 上报 |
| 限码率档 + 握手失败/响应缺字段 | 回退直连播放，sessionId 为 null |
| 转码流播放错误 | 自动回退直连重试一次（保留进度） |
| 回退后再次错误 | 正常错误链路（「视频播放失败」） |
| `fromName` 非法/缺失 | AUTO |
| 切换档位 | 持久化；下次 play 生效；当前播放不中断 |

### 5. 正常 / 基础 / 错误案例

- 正常：选 4M 档播放 4K H.265 → 握手取得 sessionId → 转码播放 → 关闭时 Stopped 带 PlaySessionId。
- 基础：AUTO 档全程直连，无握手请求，上报无 sessionId。
- 错误：依赖 `SupportsDirectPlay` 判定转码（服务器恒 true）；转码错误直接报错不回退；回退标记跨媒体项残留导致直连视频错误时错误地「重试」。

### 6. 必需测试

- `EmbyRepositoryTest`：握手请求体（MaxStreamingBitrate/UserId/hls profile）、响应映射、缺 PlaySessionId/MediaSourceId 返回 null、`resolveVideoPlaybackStreamUrl` URL 构造与降级、`fromName` 回退。
- `EncryptedConfigStoreTest`：`videoQualityMode` 默认 AUTO、往返、新 store 恢复、非法值回退。
- compile + 完整单测 + lint + assemble；真机验收需覆盖真实转码播放、回退与上报。

### 7. 错误与正确示例

```kotlin
// 错误：依赖服务器 SupportsDirectPlay 判定（4.9 恒 true，永远直连）。
if (mediaSource.supportsDirectPlay == true) playDirect()

// 正确：档位驱动；bitrate 非空即构造转码 URL，握手失败降级直连。
val url = resolveVideoPlaybackStreamUrl(video, mode, baseUrl, session)
engine.playTranscoded(video, transcodeUrl = url ?: video.streamUrl, directUrl = video.streamUrl)
```
