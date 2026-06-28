# Emby Integration Contract

## Scenario: Emby Video Browsing and Direct Playback

### 1. Scope / Trigger
- Trigger: The Android app now exposes Emby as the first real video provider instead of placeholder cards.
- Scope: Authentication, user media-library discovery, video item listing, thumbnail URL generation, direct stream URL generation, watched/resume/rating metadata, typed errors, and repository tests.
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
}
```
- Retrofit API:
```kotlin
POST Users/AuthenticateByName
GET Users
GET Users/{userId}/Views
GET Users/{userId}/Items
```

### 3. Contracts
- `VideoServerConfig.isReadyForVideoSync()` returns `true` only when:
  - `type == VideoServerType.EMBY`
  - `serverUrl` is not blank
  - either `apiKey` is not blank, or both `username` and `password` are not blank
- API key flow:
  - `GET Users` with `X-Emby-Token: <apiKey>`
  - choose the user whose `Name` matches `config.username` ignoring case, otherwise choose the first returned user
  - use the API key as the session token for later requests
- Username/password flow:
  - `POST Users/AuthenticateByName`
  - request body: `{"Username": "...", "Pw": "..."}`
  - header: `X-Emby-Authorization` with Nordic Android client metadata
  - response must include `User.Id` and non-blank `AccessToken`
- Library filtering:
  - Include libraries whose `CollectionType` is one of `movies`, `tvshows`, `homevideos`, or `mixed`
  - Include `Type == "CollectionFolder"` only as a fallback when `CollectionType` is blank
  - Do not include known non-video collections such as `music`
- Item listing:
  - `GET Users/{userId}/Items`
  - query includes `ParentId`, `Recursive=true`, `IncludeItemTypes=Movie,Series,Episode,Video`
  - query `Fields` includes `Overview`, `ProductionYear`, `SeriesId`, `SeriesName`, `ParentIndexNumber`, `IndexNumber`, `RunTimeTicks`, `ChildCount`, `ImageTags`, `CommunityRating`, and `UserData`
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
    - Continue watching: `playbackPositionSeconds > 0 && !isPlayed`, sorted by `lastPlayedDate` descending when present, then `playbackPositionSeconds` descending as the compatibility fallback
    - Top rated: non-null positive `communityRating`, sorted descending
    - Unplayed: `!isPlayed && playbackPositionSeconds <= 0`
  - These shelves are view state only. Do not persist local video history unless the PRD explicitly adds that scope.
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
- Direct playback start position:
  - A newly started unplayed `VideoItem` with `playbackPositionSeconds > 0` must seek to that resume position before playback starts.
  - Items marked `isPlayed == true`, or items with no positive resume position, start at `0`.
  - If `durationSeconds` is known, clamp the initial resume position to `durationSeconds`.
- Series detail UI:
  - A selected `Series` may derive related episodes from the already-loaded library items.
  - Match episodes by `seriesId == selectedSeries.id`, with `seriesName == selectedSeries.title` as a fallback for incomplete responses.
  - Sort derived episodes by `seasonNumber`, then `episodeNumber`, then title.
  - Episode rows play the episode item; the series header primary play action remains disabled when `streamUrl == null`.

### 4. Validation & Error Matrix
- Non-2xx response -> throw `EmbyApiException(kind = HTTP, message contains "HTTP <code>")`
- Empty response body -> throw `EmbyApiException(kind = API)`
- API key flow returns no users -> throw `EmbyApiException(kind = AUTH)`
- Password flow returns blank `AccessToken` -> throw `EmbyApiException(kind = AUTH)`
- Missing item `UserData` -> map resume position to `0` and played state to `false`
- Missing `UserData.LastPlayedDate` -> continue-watching shelf keeps the item eligible by resume position but sorts it behind dated resume items
- Missing item `CommunityRating` -> map rating to `null`; top-rated shelves should ignore it
- `playbackPositionSeconds` greater than known duration -> initial playback seek clamps to the duration instead of seeking beyond the item
- Missing episode relationship fields -> keep the episode playable, but only show it under a series detail when `seriesName` fallback matches
- `Series` item -> `VideoItem.streamUrl == null`; UI must not call playback for the series item directly
- Unknown repository exceptions -> wrap with user-action context, e.g. `"连接 Emby 失败: ..."`
- Do not classify errors by `message.contains(...)`; callers should catch `EmbyApiException` by type/kind.

### 5. Good/Base/Bad Cases
- Good: API key + username, multiple users, matching user selected, video libraries and items load.
- Good: Emby returns `UserData.PlaybackPositionTicks` and `CommunityRating`; repository maps resume/rating metadata and UI can show continue-watching/top-rated/unplayed shelves.
- Good: Emby returns `UserData.LastPlayedDate`; continue watching prioritizes recently watched items over older items with larger resume positions.
- Good: User starts an unfinished continue-watching item; playback seeks to the Emby resume position before playing.
- Good: A TV library returns both a `Series` item and its `Episode` items; series detail shows sorted episode rows, and tapping an episode plays the episode stream.
- Base: Username/password login, one video library, empty item list, UI shows an empty media-library state.
- Base: Older/incomplete Emby responses omit `UserData` and `CommunityRating`; catalog still loads and spotlight shelves simply omit unavailable groups.
- Base: A `Series` item has no matching loaded episodes; detail still shows metadata/overview and disables primary playback.
- Bad: Emby returns HTTP 500 for views, repository throws `EmbyApiException.Kind.HTTP` and UI shows the error card.
- Bad: Server has music and movie collections, repository filters out music by `CollectionType`.

### 6. Tests Required
- Readiness:
  - API key alone is enough with server URL
  - username/password is enough with server URL
  - Plex/WebDAV config is not ready for Emby sync
- API key flow:
  - asserts `GET /Users` and `X-Emby-Token`
  - asserts matching/first user behavior when applicable
- Password flow:
  - asserts `POST /Users/AuthenticateByName`
  - asserts `Username` and `Pw` body fields
  - asserts later requests use `AccessToken`
- Mapping:
  - asserts non-video libraries are filtered
  - asserts duration ticks become seconds
  - asserts `Fields` requests `UserData` and `CommunityRating`
  - asserts `UserData.PlaybackPositionTicks`, `UserData.Played`, and `CommunityRating` map to `VideoItem`
  - asserts `UserData.LastPlayedDate` maps to `VideoItem.lastPlayedDate`
  - asserts continue-watching shelf sorting uses last-played recency before resume-position fallback
  - asserts video initial start-position helper uses resume seconds for unfinished items, starts played items at zero, and clamps beyond duration
  - asserts `Fields` requests `SeriesId`, `SeriesName`, `ParentIndexNumber`, and `IndexNumber`
  - asserts `Series` items map `streamUrl` to `null`
  - asserts `Episode` relationship fields map to `VideoItem.seriesId`, `seriesName`, `seasonNumber`, and `episodeNumber`
  - asserts thumbnail URL contains item path, primary tag, and token query
  - asserts missing `ImageTags` maps to a `null` thumbnail instead of crashing catalog loading
  - asserts stream URL contains video stream path, `Static=true`, and token query
- Error:
  - asserts non-2xx responses throw typed `EmbyApiException.Kind.HTTP`

### 7. Wrong vs Correct

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
