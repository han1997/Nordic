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
  - query includes `ParentId`, `Recursive=true`, `IncludeItemTypes=Movie,Series,Episode,Video`
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
