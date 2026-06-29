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
  - Yamby-style spotlight shelves may be derived from the already-loaded Emby item list:
    - Continue watching: `playbackPositionSeconds > 0 && !isPlayed`, and when `durationSeconds > 0` the resume position must be less than duration; sort by `lastPlayedDate` descending when present, then `playbackPositionSeconds` descending as the compatibility fallback
    - Top rated: non-null positive `communityRating`, sorted descending
    - Unplayed: `!isPlayed && playbackPositionSeconds <= 0`
  - These shelves are view state only. Do not persist local video history unless the PRD explicitly adds that scope.
  - After a catalog refresh, selected video detail state must resolve against the refreshed item list. Keep the selection only when the same item id still exists in the selected library, and replace it with the refreshed `VideoItem`; otherwise clear the detail state.
  - After a catalog refresh, selected type filter state must resolve against the refreshed item list. Keep `All`, keep a specific type only when at least one refreshed item still matches it, and reset unavailable specific filters to `All`.
  - Saved video config changes are catalog boundaries. Clear libraries, selected library id, catalog items, selected detail video, local search text, stale loading state, and stale errors before loading the new account.
  - Saved video config changes must reset the type filter to `All` and load the ready new config without passing a previous config's selected library id.
  - In-flight catalog refresh or library-selection responses from a previous saved config must not write catalog/detail/filter/error/loading state after the config boundary. Guard these writes with a config-state version or equivalent request identity.
  - Same-config manual refresh keeps existing catalog reconciliation behavior: selected detail and type filters may be preserved only when they still exist in the refreshed same-catalog response.
  - Search is local to the already-loaded catalog and composes with the selected type filter; do not add server-side search unless the PRD explicitly includes it.
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
- Good: User refreshes while filtered to Episodes and the refreshed catalog still has episodes; the Episodes filter remains active.
- Good: User refreshes while filtered to Episodes and the refreshed catalog no longer has episodes; the browser resets to All instead of leaving a hidden active filter.
- Good: User switches Emby server/account from a detail page with an active search/filter; the app clears the old detail/search/filter state and loads the new account from its default library selection.
- Good: User can use video skip controls to quickly jump 10 seconds back or 30 seconds forward without leaving player bounds.
- Good: User closes or switches away from video playback; Nordic sends the stopped position to Emby so the next catalog refresh has current resume metadata.
- Good: User watches a long video session; Nordic periodically sends progress so Emby resume metadata stays fresh before close.
- Good: A TV library returns both a `Series` item and its `Episode` items; series detail shows sorted episode rows, and tapping an episode plays the episode stream.
- Good: Searching a show name in the video browser returns matching episode rows via `seriesName`.
- Good: Searching `S01E02` returns the episode whose season is `1` and episode is `2`.
- Base: Username/password login, one video library, empty item list, UI shows an empty media-library state.
- Base: Older/incomplete Emby responses omit `UserData` and `CommunityRating`; catalog still loads and spotlight shelves simply omit unavailable groups.
- Base: A `Series` item has no matching loaded episodes; detail still shows metadata/overview and disables primary playback.
- Bad: Video browser search checks only episode title/overview and hides loaded episodes when the user searches the show name.
- Bad: `EmbyItemsResponse.Items` is modeled as a non-null Kotlin list and repository mapping calls `.filter`/`.map` directly, allowing Gson-omitted fields to become runtime nulls.
- Bad: `EmbyItemDto.Id` or `Name` is modeled as a non-null Kotlin string and mapped directly, allowing compatible partial rows to crash catalog browsing.
- Bad: Item pagination compares `TotalRecordCount` to mapped video count after filtering, causing extra page requests when unusable rows were already included in the fetched total.
- Bad: Video playback compares only `VideoItem.id` and keeps an expired same-item stream URL.
- Bad: Emby returns HTTP 500 for views, repository throws `EmbyApiException.Kind.HTTP` and UI shows the error card.
- Bad: Server has music and movie collections, repository filters out music by `CollectionType`.
- Bad: TV library browsing requests or renders `Episode` items, flattening a show into individual episode cards instead of Series cards.

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
- Trigger: Changing video player display chrome, aspect-ratio behavior, fullscreen handling, or `VideoPlaybackState` fields consumed by `VideoPlayerScreen`.
- Scope: Media3 video-size observation, player-surface resize mode, UI controls, Android system bars, and orientation handling.
- Out of scope: Rebuilding Emby stream URLs, changing progress reporting, subtitle/audio-track selection, PiP, or next-episode behavior.

### 2. Signatures
```kotlin
enum class AspectRatioMode { FIT, CROP, FILL }

data class VideoPlaybackState(
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val videoAspectRatio: Float = 16f / 9f
)

fun VideoPlaybackEngine.cycleAspectRatio()
internal fun resolveNextAspectRatioMode(current: AspectRatioMode): AspectRatioMode
internal fun resolveVideoAspectRatio(width: Int, height: Int, pixelWidthHeightRatio: Float): Float

fun VideoPlayerScreen(
    onCycleAspectRatio: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean
)
```

### 3. Contracts
- `VideoPlaybackEngine` owns the current `AspectRatioMode`; Compose UI only renders the selected label and sends `onCycleAspectRatio`.
- `onVideoSizeChanged` must publish the actual content aspect ratio using Media3 `VideoSize.width`, `height`, and `pixelWidthHeightRatio`.
- Invalid video dimensions or invalid pixel ratios must fall back to `16f / 9f` so the surface never receives a zero or negative aspect ratio.
- `VideoPlayerScreen` must wrap the `SurfaceView` in Media3 `AspectRatioFrameLayout` and map:
  - `FIT` -> `RESIZE_MODE_FIT`
  - `CROP` -> `RESIZE_MODE_ZOOM`
  - `FILL` -> `RESIZE_MODE_FILL`
- The `SurfaceView` child must explicitly fill the `AspectRatioFrameLayout`.
- Fullscreen state belongs to the app shell because it controls system bars and requested orientation. `VideoPlayerScreen` receives `isFullscreen` and callbacks, but does not mutate Activity window state directly.
- Fullscreen hides system bars with transient swipe behavior and requests sensor landscape. Leaving fullscreen restores system bars and `SCREEN_ORIENTATION_UNSPECIFIED`.
- The Activity manifest must handle orientation/screen-size config changes when fullscreen orientation locking is used.

### 4. Validation & Error Matrix
- `width <= 0` or `height <= 0` -> publish `16f / 9f`.
- `pixelWidthHeightRatio <= 0f` -> treat pixel ratio as `1f`.
- `FIT` selected -> preserve full video in the viewport, accepting letterboxing.
- `CROP` selected -> fill viewport by cropping edges.
- `FILL` selected -> stretch to fill viewport.
- Back pressed while fullscreen -> exit fullscreen before closing video playback.
- Video player closed while fullscreen -> reset fullscreen state and restore system bars/orientation.
- `MainScreen` disposed while fullscreen -> restore system bars/orientation in `onDispose`.

### 5. Good/Base/Bad Cases
- Good: A 4:3 video reports pixel-adjusted aspect ratio and renders without horizontal stretching in `FIT`.
- Good: User can cycle Fit -> Crop -> Fill -> Fit without replacing the Media3 item or seeking.
- Good: Fullscreen removes app/system chrome and landscape-locks playback, then cleanly restores portrait-capable app UI on exit.
- Base: Unknown video dimensions render as 16:9 until Media3 reports a real size.
- Bad: Compose keeps a local aspect-ratio mode that diverges from `VideoPlaybackState`.
- Bad: `SurfaceView` is added without match-parent layout params and renders smaller than the frame.
- Bad: Fullscreen directly manipulates Activity state from `VideoPlayerScreen`, making the composable hard to test and reuse.

### 6. Tests Required
- Unit test `resolveNextAspectRatioMode(...)` for Fit -> Crop -> Fill -> Fit.
- Unit test `resolveVideoAspectRatio(...)` for pixel-ratio application, invalid dimensions, and invalid pixel ratio.
- Compile check for Media3 `AspectRatioFrameLayout` API usage and callback wiring.
- Lint and debug assemble when manifest config changes or fullscreen system UI handling changes.

### 7. Wrong vs Correct
#### Wrong
```kotlin
// UI owns playback display state and can drift from the engine.
var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }
```

#### Correct
```kotlin
// Engine owns the state; UI sends commands and renders state.
VideoPlayerScreen(
    state = videoPlaybackState,
    onCycleAspectRatio = videoPlaybackEngine::cycleAspectRatio
)
```

#### Wrong
```kotlin
frameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height.toFloat())
```

#### Correct
```kotlin
frameLayout.setAspectRatio(
    resolveVideoAspectRatio(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
)
```
