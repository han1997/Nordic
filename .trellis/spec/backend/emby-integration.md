# Emby Integration Contract

## Scenario: Read-Only Video Browsing MVP

### 1. Scope / Trigger
- Trigger: The Android app now exposes Emby as the first real video provider instead of placeholder cards.
- Scope: Authentication, user media-library discovery, video item listing, thumbnail URL generation, typed errors, and repository tests.
- Out of scope for the browsing MVP: Plex, WebDAV browsing, persistent Emby token storage, and provider-wide account management.
- Video playback is covered by the later "Direct Video Playback MVP" scenario below.

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
  - query includes `ParentId`, `Recursive=true`, `IncludeItemTypes=Movie,Series,Video`
  - Main library browsing excludes `Episode`; episode rows stay available through search, continue-watching, Next Up, and Series detail season/episode drill-down.
  - map `RunTimeTicks` to seconds using `10_000_000` ticks per second
- Thumbnail URL:
  - Build `/Items/{itemId}/Images/Primary`
  - Include `maxWidth=640`, `quality=90`, `tag=<ImageTags.Primary>`, and `api_key=<session token>`
  - Return `null` when `ImageTags.Primary` is absent

### 4. Validation & Error Matrix
- Non-2xx response -> throw `EmbyApiException(kind = HTTP, message contains "HTTP <code>")`
- Empty response body -> throw `EmbyApiException(kind = API)`
- API key flow returns no users -> throw `EmbyApiException(kind = AUTH)`
- Password flow returns blank `AccessToken` -> throw `EmbyApiException(kind = AUTH)`
- Unknown repository exceptions -> wrap with user-action context, e.g. `"连接 Emby 失败: ..."`
- Do not classify errors by `message.contains(...)`; callers should catch `EmbyApiException` by type/kind.

### 5. Good/Base/Bad Cases
- Good: API key + username, multiple users, matching user selected, video libraries and items load.
- Base: Username/password login, one video library, empty item list, UI shows an empty media-library state.
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
- Password flow:
  - asserts `POST /Users/AuthenticateByName`
  - asserts `Username` and `Pw` body fields
  - asserts later requests use `AccessToken`
- Mapping:
  - asserts non-video libraries are filtered
  - asserts main library browsing excludes `Episode` item types
  - asserts duration ticks become seconds
  - asserts thumbnail URL contains item path, primary tag, and token query
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

#### Wrong
```kotlin
api.getItems(userId, token, parentId = libraryId)
```

Using the `getItems` broad default can include `Episode` and make TV libraries render as a flat episode list.

#### Correct
```kotlin
api.getItems(
    userId = userId,
    token = token,
    parentId = libraryId,
    includeItemTypes = "Movie,Series,Video"
)
```

Main library browsing should request series-level TV entries. Keep `Episode` only in search, resume, Next Up, and season drill-down requests.

---

## Scenario: Direct Video Playback MVP

### 1. Scope / Trigger
- Trigger: User taps a playable Emby video item; the app must launch a Media3 video playback surface.
- Scope: `GET /Items/{Id}/PlaybackInfo`, direct/static stream URL construction, domain playback model, ExoPlayer wrapper, video player screen.
- Out of scope: HLS/transcoding fallback, subtitles, PIP, gesture controls, Live TV.

### 2. Signatures
- API endpoint:
```kotlin
@GET("Items/{itemId}/PlaybackInfo")
suspend fun getPlaybackInfo(
    @Path("itemId") itemId: String,
    @Header("X-Emby-Token") token: String,
    @Query("UserId") userId: String
): Response<EmbyPlaybackInfoResponse>
```
- Repository:
```kotlin
suspend fun getPlaybackInfo(item: VideoItem): VideoPlaybackInfo
```
- Domain model:
```kotlin
@Stable data class VideoPlaybackInfo(
    val itemId: String, val title: String, val streamUrl: String,
    val mediaSourceId: String, val playSessionId: String,
    val overview: String = "", val durationSeconds: Int = 0, val imageUrl: String? = null
)
```
- Playback engine:
```kotlin
class VideoPlaybackEngine(context: Context) {
    val state: StateFlow<VideoPlaybackState>
    val player: Player
    fun play(playbackInfo: VideoPlaybackInfo)
    fun togglePlayPause()
    fun seekTo(positionSeconds: Int)
    fun stop()
    fun release()
}
```

### 3. Contracts
- `GET /Items/{itemId}/PlaybackInfo` requires `X-Emby-Token` header and `UserId` query parameter.
- Response `EmbyPlaybackInfoResponse` contains `MediaSources` list and `PlaySessionId`.
- Media source selection: first source where `id` is not blank and `supportsDirectStream` is not `false`.
- Direct stream URL pattern: `/Videos/{itemId}/stream?static=true&MediaSourceId={id}&PlaySessionId={sid}&api_key={token}`
- Duration fallback: use `mediaSource.runTimeTicks` first, then fall back to `item.durationSeconds`.
- `VideoPlaybackEngine` owns the ExoPlayer instance; UI reads `VideoPlaybackState` and sends commands (`play`, `togglePlayPause`, `seekTo`, `stop`).
- On player error, `VideoPlaybackState.errorMessage` is populated and surfaced in the UI; the app never silently ignores playback failures.

### 4. Validation & Error Matrix
- Non-2xx from PlaybackInfo -> `EmbyApiException(kind = HTTP, message contains "HTTP <code>")`
- No direct-playable media source -> `EmbyApiException(kind = API, message mentions "没有可直接播放的媒体源")`
- Blank `PlaySessionId` -> `EmbyApiException(kind = API, message mentions "缺少播放会话")`
- Generic exception during playback-info fetch -> wrap with context: `"启动 Emby 播放失败: ..."`
- ExoPlayer `onPlayerError` -> `VideoPlaybackState.errorMessage = "视频播放失败: ..."`

### 5. Good/Base/Bad Cases
- Good: PlaybackInfo returns a direct-stream source and play session; stream URL plays in ExoPlayer.
- Base: Item with no `RunTimeTicks` falls back to `VideoItem.durationSeconds`.
- Bad: PlaybackInfo returns no `SupportsDirectStream != false` source; `EmbyApiException.Kind.API` thrown, UI shows error card.
- Bad: Emby returns HTTP 500 for PlaybackInfo; `EmbyApiException.Kind.HTTP` thrown.

### 6. Tests Required
- `getPlaybackInfo_mapsDirectStreamUrlFromPlaybackInfo`: asserts `PlaybackInfo` endpoint called with correct path/header/query, stream URL contains `static=true`, `MediaSourceId`, `PlaySessionId`, `api_key`, and duration mapped from ticks.
- `getPlaybackInfo_throwsTypedApiErrorWhenNoDirectSourceExists`: asserts `EmbyApiException.Kind.API` when all sources have `SupportsDirectStream = false`.
- `getPlaybackInfo_throwsTypedHttpErrorForNon2xx`: asserts `EmbyApiException.Kind.HTTP` with "HTTP 500" on server error.
- `getPlaybackInfo_throwsTypedApiErrorWhenPlaySessionIdIsMissing`: asserts `EmbyApiException.Kind.API` mentioning "播放会话" when `PlaySessionId` is blank.

### 7. Wrong vs Correct

#### Wrong
```kotlin
val streamUrl = "$baseUrl/Videos/${itemId}/stream?api_key=$token"
```
Omitting `static=true`, `MediaSourceId`, and `PlaySessionId` causes Emby to return a transcode playlist or reject the request.

#### Correct
```kotlin
val streamUrl = baseUrl.toHttpUrl().newBuilder()
    .addPathSegment("Videos").addPathSegment(itemId).addPathSegment("stream")
    .addQueryParameter("static", "true")
    .addQueryParameter("MediaSourceId", mediaSourceId)
    .addQueryParameter("PlaySessionId", playSessionId)
    .addQueryParameter("api_key", token)
    .build().toString()
```
`static=true` requests the original file bytes; the session and source IDs let Emby track and serve the correct media.

---

## Scenario: Video Progress Reporting & Season/Episode Browsing

### 1. Scope / Trigger
- Trigger: User plays a video item (progress reporting) or browses a Series item (season/episode drill-down).
- Scope: `POST Sessions/Playing`, `POST Sessions/Playing/Progress`, `POST Sessions/Playing/Stopped`; `GET` seasons and episodes via `getItems` with `ParentId`, `IncludeItemTypes`, and episode `UserData` fields; domain models `VideoSeason` and `VideoEpisode`.
- Out of scope: Now-playing session management UI, transcoding URL construction.

### 2. Signatures
- Progress reporting endpoints:
```kotlin
@POST("Sessions/Playing")
suspend fun reportPlaybackStart(@Body body: EmbyPlaybackReportBody, @Header("X-Emby-Token") token: String): Response<Unit>

@POST("Sessions/Playing/Progress")
suspend fun reportPlaybackProgress(@Body body: EmbyPlaybackReportBody, @Header("X-Emby-Token") token: String): Response<Unit>

@POST("Sessions/Playing/Stopped")
suspend fun reportPlaybackStopped(@Body body: EmbyPlaybackReportBody, @Header("X-Emby-Token") token: String): Response<Unit>
```
- Report body: `EmbyPlaybackReportBody(itemId, sessionId, mediaSourceId, isPaused, positionTicks)`
- Repository:
```kotlin
suspend fun reportPlaybackStart(itemId: String, mediaSourceId: String, playSessionId: String)
suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int)
suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String, playSessionId: String, positionSeconds: Int)
suspend fun getSeasons(seriesId: String): List<VideoSeason>
suspend fun getEpisodes(seasonId: String): List<VideoEpisode>
```
- Domain models:
```kotlin
@Stable data class VideoSeason(val id: String, val name: String, val indexNumber: Int, val episodeCount: Int, val imageUrl: String? = null)
@Stable data class VideoEpisode(
    val id: String,
    val name: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val overview: String,
    val durationSeconds: Int,
    val imageUrl: String? = null,
    val progress: VideoProgress? = null
)
```

### 3. Contracts
- Progress reporting: `positionTicks = positionSeconds * 10_000_000L`. Reports at play start, every 10 seconds during playback, and on stop/release.
- Seasons: fetched via `getItems(parentId=seriesId, includeItemTypes=Season)`.
- Episodes: fetched via `getItems(parentId=seasonId, includeItemTypes=Episode)` with fields including `Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags,UserData`.
- Episode `UserData.PlaybackPositionTicks`, `PlayedPercentage`, `Played`, and `LastPlayedDate` map through the same `VideoProgress` contract used by resume items.
- Series detail episode cards should render watched/resume state from `VideoEpisode.progress`. When starting an episode from the card, copy `episode.progress` into the temporary `VideoItem` passed to `getPlaybackInfo(...)` so playback resumes from the episode-specific position.
- `VideoPlaybackEngine` calls progress callbacks from coroutine scope; callbacks are `suspend` functions.
- Video detail screen intercepts card taps to show metadata before playback; Series items show season chips and episode lists.

### 4. Validation & Error Matrix
- Non-2xx from reporting endpoints → fire-and-forget (wrapped in `runCatching` in engine callbacks, does not block playback)
- Non-2xx from seasons/episodes → `EmbyApiException(kind = HTTP)` per existing pattern
- Empty seasons list → UI shows "暂无季信息"
- Episode without `RunTimeTicks` → `durationSeconds` falls back to 0
- Episode without `UserData` → `VideoEpisode.progress == null`; card renders the existing simple metadata.
- Episode with `Played == true` → card may show watched state, but continue-watching behavior should not override playback start with an arbitrary nonzero progress unless `getPlaybackInfo(...)` receives that progress explicitly.

### 5. Good/Base/Bad Cases
- Good: Series has 3 seasons, each with 8 episodes; tapping a season loads episodes; tapping an episode plays it.
- Good: Episode includes `UserData.PlaybackPositionTicks`; series detail shows resume state and playback starts from that position.
- Base: Movie item shows detail page with play button; no season/episode data loaded.
- Base: Episode has no `UserData`; card remains simple and playback starts from 0.
- Bad: Emby returns 500 for seasons; error surfaced in UI; play button still works for the series itself.
- Bad: Repository maps episode overview/duration but drops `UserData`, so playback from a season list restarts from 0 even though Emby knows the position.

### 6. Tests Required
- `reportPlaybackStart_sendsCorrectBodyWithTicks`: verify positionTicks=0 and correct itemId/mediaSourceId/playSessionId
- `getSeasons_mapsDtoToVideoSeason`: verify season name, indexNumber, episodeCount, image URL
- `getEpisodes_mapsDtoToVideoEpisode`: verify episode name, season/episode numbers, duration from ticks, image URL, episode `VideoProgress`, and request fields include `UserData`

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Not reporting progress means Emby server has no idea where you stopped
// Tapping a Series card immediately plays it (no detail screen)
// Constructing an episode VideoItem without episode.progress loses resume position.
```

#### Correct
```kotlin
// VideoPlaybackEngine reports start/progress/stop via suspend callbacks
// Card tap → detail screen → play button → playback
scope.launch { onPlaybackStart?.invoke(info.itemId, info.mediaSourceId, info.playSessionId, 0) }

val episodeVideoItem = VideoItem(
    id = episode.id,
    libraryId = series.libraryId,
    title = episode.name,
    type = "Episode",
    durationSeconds = episode.durationSeconds,
    progress = episode.progress
)
```

## Scenario: Video Continue Watching

### 1. Scope / Trigger

- Trigger: Any change to Emby continue-watching shelves, `GET /Users/{userId}/Items/Resume`, `VideoProgress`, or resume-position playback.
- This is cross-layer work: Emby user progress fields are mapped in the repository, rendered in `VideoScreen`, carried through `VideoPlaybackInfo`, and consumed by `VideoPlaybackEngine`.

### 2. Signatures

- API:
```kotlin
@GET("Users/{userId}/Items/Resume")
suspend fun getResumeItems(
    @Path("userId") userId: String,
    @Header("X-Emby-Token") token: String,
    @Query("MediaTypes") mediaTypes: String = "Video",
    @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Episode,Video",
    @Query("Fields") fields: String = "Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags",
    @Query("Limit") limit: Int = 12
): Response<EmbyItemsResponse>
```
- DTO/domain:
```kotlin
data class EmbyItemDto(..., val parentId: String? = null, val userData: EmbyUserDataDto? = null)
data class EmbyUserDataDto(
    val playedPercentage: Double? = null,
    val playbackPositionTicks: Long? = null,
    val played: Boolean = false,
    val lastPlayedDate: String? = null
)

@Stable data class VideoProgress(
    val currentTimeSeconds: Int = 0,
    val playedPercentage: Float = 0f,
    val isPlayed: Boolean = false,
    val lastPlayedDate: String? = null
)
@Stable data class VideoItem(..., val progress: VideoProgress? = null)
@Stable data class VideoCatalog(..., val resumeItems: List<VideoItem> = emptyList())
@Stable data class VideoPlaybackInfo(..., val resumePositionSeconds: Int = 0)
```
- Repository/playback:
```kotlin
suspend fun EmbyRepository.getResumeItems(): List<VideoItem>
fun VideoPlaybackEngine.play(playbackInfo: VideoPlaybackInfo)
```

### 3. Contracts

- `getCatalog()` fetches resume items with the same authenticated session as libraries/items and stores them in `VideoCatalog.resumeItems`.
- Resume requests use `GET /Users/{userId}/Items/Resume` with `MediaTypes=Video` and `IncludeItemTypes=Movie,Episode,Video`.
- `UserData.PlaybackPositionTicks` maps to `VideoProgress.currentTimeSeconds` using `10_000_000` ticks per second.
- `UserData.PlayedPercentage` maps to `VideoProgress.playedPercentage` and is clamped to `0f..100f`.
- Continue-watching shelves include only items where `currentTimeSeconds > 0` and `isPlayed == false`.
- `EmbyRepository.getPlaybackInfo(item)` copies `item.progress.currentTimeSeconds` into `VideoPlaybackInfo.resumePositionSeconds`, clamped to the item duration when known.
- `VideoPlaybackEngine.play()` seeks to `resumePositionSeconds` before playback starts and reports playback start at that same position.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Resume endpoint returns no items | `VideoCatalog.resumeItems` is empty and the shelf is hidden |
| Resume item has missing `UserData` | Exclude it from continue watching |
| `PlaybackPositionTicks <= 0` | Exclude it from continue watching |
| `Played == true` | Exclude it from continue watching |
| `PlayedPercentage` is out of range | Clamp to `0f..100f` |
| Resume position exceeds known duration | Clamp playback start to duration |
| Resume endpoint returns non-2xx | Throw `EmbyApiException.Kind.HTTP` through the repository pattern |

### 5. Good/Base/Bad Cases

- Good: Emby returns an unfinished movie with `PlaybackPositionTicks`; Video home shows it in "继续观看", and playback starts at that position.
- Base: Emby returns an empty resume list; normal library browsing and playback remain unchanged.
- Bad: UI derives continue watching by scanning only the current library list and misses resumable items from other video libraries.

### 6. Tests Required

- Repository test asserting `getResumeItems()` requests `/Users/{userId}/Items/Resume`, passes token, `MediaTypes=Video`, and `IncludeItemTypes=Movie,Episode,Video`.
- Repository test asserting `UserData.PlaybackPositionTicks`, `PlayedPercentage`, `Played`, and `LastPlayedDate` map to `VideoProgress`.
- Repository test asserting played or zero-position items are filtered out.
- Repository test asserting `getPlaybackInfo()` carries `VideoItem.progress.currentTimeSeconds` into `VideoPlaybackInfo.resumePositionSeconds`.
- Compile/lint checks for `VideoScreen` shelf rendering and `VideoPlaybackEngine.play()` callback wiring.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Wrong: this only sees the currently selected library and does not use Emby's resume contract.
val resumeItems = currentLibraryItems.filter { it.progress?.currentTimeSeconds ?: 0 > 0 }
```

#### Correct
```kotlin
// Correct: ask Emby for the user's cross-library resumable video items.
val resumeItems = api.getResumeItems(
    userId = session.userId,
    token = session.token,
    mediaTypes = "Video",
    includeItemTypes = "Movie,Episode,Video"
)
```

## Scenario: PlaybackInfo Media Streams and Player Track Controls

### 1. Scope / Trigger

- Trigger: Any change to Emby `PlaybackInfo` stream DTOs, `VideoPlaybackInfo`, `VideoPlaybackEngine` track selection, or video player audio/subtitle controls.
- This is cross-layer work: Emby media stream payloads are mapped in the repository, attached to Media3 items, selected by the playback engine, and exposed by Compose controls.

### 2. Signatures

- DTO/domain:
```kotlin
data class EmbyMediaSourceDto(
    ..., val mediaStreams: List<EmbyMediaStreamDto> = emptyList()
)

data class EmbyMediaStreamDto(
    val index: Int = -1,
    val type: String? = null,
    val codec: String? = null,
    val language: String? = null,
    val displayTitle: String? = null,
    val title: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val deliveryUrl: String? = null
)

data class VideoPlaybackInfo(
    ..., val audioTracks: List<VideoMediaTrack> = emptyList(),
    val subtitleTracks: List<VideoMediaTrack> = emptyList()
)

data class VideoMediaTrack(
    val index: Int,
    val label: String,
    val language: String? = null,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val deliveryUrl: String? = null
)
```
- Playback engine:
```kotlin
fun VideoPlaybackEngine.selectAudioTrack(trackIndex: Int?)
fun VideoPlaybackEngine.selectSubtitleTrack(trackIndex: Int?)
fun VideoPlaybackEngine.setSubtitleScale(scale: Float)
```

### 3. Contracts

- `EmbyRepository.getPlaybackInfo()` maps `MediaSources[].MediaStreams` into separate audio and subtitle track lists.
- Audio tracks are streams where `Type == "Audio"` ignoring case; subtitle tracks are streams where `Type == "Subtitle"` ignoring case.
- Track labels prefer `DisplayTitle`, then `Title`, then uppercased language/codec, then `"<kind> <index>"`.
- External subtitle `DeliveryUrl` values must be converted to absolute URLs and include `api_key=<session token>` unless the URL already contains an API key.
- `VideoPlaybackEngine.play()` selects the default audio track when present, otherwise the first audio track. It selects a default or forced subtitle when present.
- Embedded Media3 subtitle/audio selection may be language-based when the API does not expose a stable renderer-track id. UI state must still reflect the user's selected Emby stream index.
- Subtitle scale is applied to `PlayerView.subtitleView`.
- Do not expose subtitle offset controls unless the implementation actually retimes subtitle cues in the current Media3 playback path. A state-only offset is misleading and must be removed or disabled.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| PlaybackInfo has no media streams | Video playback still starts; track controls are hidden or disabled |
| Track has blank display/title/language/codec | Use the fallback `Audio <index>` or `Subtitle <index>` label |
| External subtitle URL is relative | Resolve against the normalized Emby base URL and append `api_key` |
| User selects `null` subtitle track | Disable text track selection and update state to no selected subtitle |
| User selects unknown track index | Ignore the unavailable track and keep playback stable |
| Media3 cannot apply a subtitle timing offset | Do not show active offset controls |

### 5. Good/Base/Bad Cases

- Good: PlaybackInfo returns English AAC and Chinese SRT; UI shows both labels, default audio is selected, external subtitle URL includes the token, and scale changes affect rendered captions.
- Base: Movie has one embedded audio track and no subtitles; controls stay minimal and playback works.
- Bad: UI builds track labels from raw codec only, or repository exposes Emby DTOs directly to Compose.

### 6. Tests Required

- Repository test asserting audio/subtitle streams from `MediaStreams` map to `VideoMediaTrack` with index, label, language, default/forced/external flags.
- Repository test asserting external subtitle `DeliveryUrl` is absolute and tokenized.
- Compile/lint checks for Media3 subtitle configuration and player callback wiring.
- Add pure helper tests if track selection grows beyond simple language/default preference.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// UI reaches into Emby DTOs and tries to render raw stream payloads.
playbackInfo.mediaSources.first().mediaStreams.filter { it.Type == "Audio" }
```

#### Correct
```kotlin
// Repository exposes app-facing tracks; playback/UI consume the domain model.
VideoPlaybackInfo(
    audioTracks = mediaSource.mediaStreams.filterAudioTracks(),
    subtitleTracks = mediaSource.mediaStreams.filterSubtitleTracks()
)
```

## Scenario: Manual Next Episode Playback

### 1. Scope / Trigger

- Trigger: Any change to video player next-episode controls, `VideoEpisodeQueue`, episode playback from `VideoDetailScreen`, or Activity-level video playback state.
- This is UI/playback orchestration over existing Emby APIs: do not introduce a new Retrofit endpoint when the current season's episode list already contains the ordering needed for a same-season next button.

### 2. Signatures

```kotlin
@Stable data class VideoEpisodeQueue(
    val libraryId: String,
    val episodes: List<VideoEpisode>,
    val currentIndex: Int
)

fun resolveNextVideoEpisodeIndex(currentIndex: Int, itemCount: Int): Int?
fun VideoEpisode.toPlaybackVideoItem(libraryId: String): VideoItem

fun VideoPlayerScreen(
    ..., hasNextEpisode: Boolean = false,
    isLoadingNextEpisode: Boolean = false,
    externalError: String? = null,
    onPlayNextEpisode: () -> Unit = {}
)
```

### 3. Contracts

- Only episode playback launched from a Series detail season list should create a `VideoEpisodeQueue`.
- Queue context is same-season only; `currentIndex` refers to the loaded `episodes` list from `VideoDetailScreen`.
- `resolveNextVideoEpisodeIndex(...)` returns `currentIndex + 1` only when `currentIndex >= 0` and there is an item after it; otherwise it returns `null`.
- `VideoEpisode.toPlaybackVideoItem(...)` must preserve episode id, title, overview, duration, image URL, and `progress` so resume playback still works.
- `VideoPlayerScreen` must render the next-episode action as disabled or absent when `hasNextEpisode == false`.
- Clicking Next Episode loads `EmbyRepository.getPlaybackInfo(nextEpisode.toPlaybackVideoItem(...))`, then calls `VideoPlaybackEngine.play(...)` and advances the queue only after success.
- Loading the next episode must not stop or clear the current video on failure; surface the failure through `externalError`.
- Movie playback, standalone video playback, search-result episode playback, Continue Watching, and Next Up entries do not get queue context unless they are routed through the Series detail episode list.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Queue is null | Next action disabled/absent; click is a no-op |
| Current episode is last in queue | Next action disabled/absent |
| `currentIndex` is invalid | `resolveNextVideoEpisodeIndex(...) == null` |
| Next episode PlaybackInfo succeeds | Start next episode and advance `VideoEpisodeQueue.currentIndex` |
| Next episode PlaybackInfo fails | Keep current playback, preserve queue index, and show `externalError` |
| User closes video player | Clear queue context and transient next-episode error |

### 5. Good/Base/Bad Cases

- Good: User opens a Series, plays S1E2, then taps Next and S1E3 starts without returning to the detail screen.
- Base: User plays the last episode in a season; the player remains usable but Next is disabled.
- Bad: User plays a movie after watching an episode and still sees a stale Next button from the previous episode queue.
- Bad: Next PlaybackInfo fails and the app stops the current episode, losing the user's current playback surface.

### 6. Tests Required

- Pure helper test for `resolveNextVideoEpisodeIndex(...)` covering normal, last-item, empty, and invalid-index cases.
- Pure helper/queue test asserting `VideoEpisodeQueue.advanceToNext()` moves one item at a time and stops at the end.
- Pure helper test asserting `VideoEpisode.toPlaybackVideoItem(...)` preserves `VideoProgress`.
- Compile/lint checks for `VideoDetailScreen`, `VideoPlayerScreen`, and `MainActivity` callback wiring.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Wrong: stale queue remains active for non-episode playback.
videoPlaybackEngine.play(moviePlaybackInfo)
showVideoPlayer = true
```

#### Correct
```kotlin
videoEpisodeQueue = null
videoPlaybackError = null
videoPlaybackEngine.play(moviePlaybackInfo)
showVideoPlayer = true
```

#### Wrong
```kotlin
// Wrong: advancing before PlaybackInfo succeeds loses the current queue position.
videoEpisodeQueue = videoEpisodeQueue?.advanceToNext()
repo.getPlaybackInfo(nextEpisode.toPlaybackVideoItem(libraryId))
```

#### Correct
```kotlin
val nextQueue = queue.advanceToNext() ?: return
val info = repo.getPlaybackInfo(nextEpisode.toPlaybackVideoItem(nextQueue.libraryId))
videoPlaybackEngine.play(info)
videoEpisodeQueue = nextQueue
```

## Scenario: Video Search, Discovery Controls, and User Item State

### 1. Scope / Trigger

- Trigger: Any change to global Emby video search, library sort/filter controls, Next Up shelves, favorite toggles, or watched/unwatched toggles.
- This is cross-layer work: Emby endpoint payloads are declared in `api/`, mapped to `VideoItem` and `VideoCatalog` in `EmbyRepository`, rendered in `VideoScreen` / `VideoDetailScreen`, and verified with `MockWebServer` request assertions.

### 2. Signatures

- API:
```kotlin
@GET("Search/Hints")
suspend fun searchHints(..., @Query("SearchTerm") searchTerm: String): Response<EmbySearchHintsResponse>

@GET("Shows/NextUp")
suspend fun getNextUp(@Query("UserId") userId: String, ...): Response<EmbyItemsResponse>

@GET("Users/{userId}/Items")
suspend fun getItems(..., @Query("SortBy") sortBy: String, @Query("SortOrder") sortOrder: String, @Query("Filters") filters: String?): Response<EmbyItemsResponse>

@POST("Users/{userId}/PlayedItems/{itemId}") suspend fun markPlayed(...)
@DELETE("Users/{userId}/PlayedItems/{itemId}") suspend fun markUnplayed(...)
@POST("Users/{userId}/FavoriteItems/{itemId}") suspend fun markFavorite(...)
@DELETE("Users/{userId}/FavoriteItems/{itemId}") suspend fun markUnfavorite(...)
```
- Domain/repository:
```kotlin
data class VideoItem(..., val progress: VideoProgress? = null, val isFavorite: Boolean = false)
data class VideoCatalog(..., val resumeItems: List<VideoItem> = emptyList(), val nextUpItems: List<VideoItem> = emptyList())
data class VideoItemQuery(val sort: VideoSortOption = DateAdded, val filter: VideoItemFilter = All)

suspend fun EmbyRepository.searchVideos(searchTerm: String, limit: Int = 20): List<VideoItem>
suspend fun EmbyRepository.getNextUpItems(): List<VideoItem>
suspend fun EmbyRepository.setItemPlayed(itemId: String, played: Boolean)
suspend fun EmbyRepository.setItemFavorite(itemId: String, favorite: Boolean)
```

### 3. Contracts

- Library list requests include `Fields=Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags,UserData` so watched and favorite state can be rendered without extra per-item calls.
- `VideoSortOption` maps UI choices to server-side `SortBy` / `SortOrder`; do not locally sort a truncated Emby page and call it server sort parity.
- `VideoItemFilter.Favorite`, `Played`, and `Unplayed` map to Emby `Filters=IsFavorite`, `IsPlayed`, and `IsUnplayed`.
- `searchVideos()` calls `Search/Hints` with `MediaTypes=Video` and `IncludeItemTypes=Movie,Series,Episode,Video`; repository maps hints into normal `VideoItem` instances.
- Search hint identity prefers `ItemId`, then `Id`; hints with no resolved id are ignored.
- Next Up uses `Shows/NextUp?UserId=<userId>` and maps returned episodes into `VideoItem`; `VideoCatalog.nextUpItems` is loaded alongside libraries, current-library items, and resume items.
- Favorite state maps from `UserData.IsFavorite` into `VideoItem.isFavorite`.
- Watched state maps from `UserData.Played` through `VideoProgress.isPlayed`; manual toggles update Emby through the PlayedItems endpoints and update local UI state after success.
- UI must call repository methods only. Compose must not call Retrofit APIs or inspect Emby DTOs directly.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Blank search term | Return an empty search result list without an API request |
| Search hint has no `ItemId` or `Id` | Drop that hint |
| Search/NextUp/library query returns non-2xx | Throw `EmbyApiException.Kind.HTTP` through `requireBody()` |
| Mutation endpoint returns non-2xx | Throw `EmbyApiException.Kind.HTTP` through `requireSuccess()` |
| Favorite/watched mutation succeeds | Update local `VideoItem` state in visible lists/detail surfaces |
| Favorite/watched mutation fails | Preserve local state and surface an error message |

### 5. Good/Base/Bad Cases

- Good: Search for a title returns movies, series, and episodes grouped by type; cards reuse `VideoItem` rendering and detail navigation.
- Good: Favorites filter sends `Filters=IsFavorite`; item cards and detail chips toggle `FavoriteItems` endpoints.
- Good: Marking a resume item watched removes it from the continue-watching shelf after the server call succeeds.
- Base: Search returns no hints; UI shows an empty search state while library browsing remains usable.
- Bad: UI filters favorites locally after fetching only the first page of non-favorite items, missing server-side matches.
- Bad: UI flips favorite/watched state before the mutation succeeds and leaves stale state after a server error.

### 6. Tests Required

- Repository test asserting `getLibraryItems(..., VideoItemQuery)` sends `SortBy`, `SortOrder`, `Filters`, and `Fields` including `UserData`.
- Repository test asserting `searchVideos()` requests `/Search/Hints`, sends `SearchTerm`, `MediaTypes=Video`, item types, token, and maps `PrimaryImageTag`, `UserData.IsFavorite`, and `UserData.Played`.
- Repository test asserting `getNextUpItems()` requests `/Shows/NextUp?UserId=<userId>` with token and maps returned episodes.
- Repository test asserting `setItemPlayed()` calls POST/DELETE `PlayedItems`, and `setItemFavorite()` calls POST/DELETE `FavoriteItems`.
- Compile/lint checks for `VideoScreen` and `VideoDetailScreen` state wiring.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Wrong: local-only filtering can miss favorites outside the current page.
val favorites = repo.getLibraryItems(libraryId).filter { it.isFavorite }
```

#### Correct
```kotlin
// Correct: ask Emby for the requested server-side filter.
val favorites = repo.getLibraryItems(
    libraryId,
    VideoItemQuery(filter = VideoItemFilter.Favorite)
)
```
