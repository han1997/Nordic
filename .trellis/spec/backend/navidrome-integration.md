# Navidrome Integration Contract

## Scenario: Star/Favorite and Playlist CRUD

### 1. Scope / Trigger
- Trigger: User toggles star/favorite on an album, song, or artist; user creates, renames, or deletes a playlist; user adds a song to a playlist.
- Scope: `GET /star2`, `GET /unstar`, `GET /getStarred2`, `GET /createPlaylist`, `GET /updatePlaylist`, `GET /deletePlaylist`.
- Out of scope: Rating (5-star), playlist reordering, smart playlists.

### 2. Signatures
- Star/unstar:
```kotlin
@GET("star2")
suspend fun star(@Query("u") username: String, ..., @Query("id") id: String? = null, @Query("albumId") albumId: String? = null, @Query("artistId") artistId: String? = null): Response<SubsonicResponse>

@GET("unstar")
suspend fun unstar(@Query("u") username: String, ..., @Query("id") id: String? = null, @Query("albumId") albumId: String? = null, @Query("artistId") artistId: String? = null): Response<SubsonicResponse>

@GET("getStarred2")
suspend fun getStarred2(@Query("u") username: String, ...): Response<SubsonicResponse>
```
- Playlist CRUD:
```kotlin
@GET("createPlaylist")
suspend fun createPlaylist(@Query("u") username: String, ..., @Query("name") name: String, @Query("songId") songId: List<String>? = null): Response<SubsonicResponse>

@GET("updatePlaylist")
suspend fun updatePlaylist(@Query("u") username: String, ..., @Query("playlistId") playlistId: String, @Query("name") name: String? = null, @Query("songIdToAdd") songIdToAdd: List<String>? = null, @Query("songIndexToRemove") songIndexToRemove: List<Int>? = null): Response<SubsonicResponse>

@GET("deletePlaylist")
suspend fun deletePlaylist(@Query("u") username: String, ..., @Query("id") id: String): Response<SubsonicResponse>
```
- Repository:
```kotlin
suspend fun star(id: String? = null, albumId: String? = null, artistId: String? = null)
suspend fun unstar(id: String? = null, albumId: String? = null, artistId: String? = null)
suspend fun getStarred(): StarredContent
suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): NavidromePlaylist
suspend fun renamePlaylist(playlistId: String, newName: String)
suspend fun addToPlaylist(playlistId: String, songId: String)
suspend fun removeFromPlaylist(playlistId: String, songIndex: Int)
suspend fun deletePlaylist(playlistId: String)
```

### 3. Contracts
- All Subsonic API calls require auth params: `u`, `t`, `s`, `v`, `c` (handled internally by `NavidromeRepository`).
- `star`/`unstar`: at least one of `id`, `albumId`, `artistId` must be provided.
- `getStarred2` returns `StarredContent(albums, songs, artists)`; repository maps DTOs using existing `withCoverArtUrl()` helpers.
- `createPlaylist` returns the created `NavidromePlaylist` (mapped from `SubsonicData.playlist` which is `NavidromePlaylistDetail`).
- `updatePlaylist` can combine name change + song additions + song removals in one call.
- `deletePlaylist` takes the playlist `id`.

### 4. Validation & Error Matrix
- Non-2xx from Subsonic API → `NavidromeApiException(kind = SUBSONIC)`
- Empty response body → `NavidromeApiException(kind = SUBSONIC, "响应为空")`
- `createPlaylist` returns null playlist → `NavidromeApiException(kind = SUBSONIC, "创建歌单返回为空")`
- Generic errors → wrap with user-action context: `"操作失败: ..."`

### 5. Good/Base/Bad Cases
- Good: Star an album, `getStarred2` returns it, UI shows it in "我的收藏" section.
- Base: Empty starred list, "我的收藏" section hidden.
- Bad: `unstar` fails with network error; UI shows error toast, star state unchanged locally.

### 6. Tests Required
- `star_callsCorrectEndpointWithAlbumId`: verify `GET /star2?albumId=...`
- `unstar_callsCorrectEndpointWithArtistId`: verify `GET /unstar?artistId=...`
- `getStarred2_mapsAlbumsSongsArtists`: verify domain mapping with cover art URLs
- `createPlaylist_callsEndpointAndMapsResponse`: verify `GET /createPlaylist?name=...` and returned `NavidromePlaylist`
- `updatePlaylist_addSongAndRemoveByIndex`: verify query params `songIdToAdd` and `songIndexToRemove`
- `deletePlaylist_callsCorrectEndpoint`: verify `GET /deletePlaylist?id=...`

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Not passing at least one id/albumId/artistId to star/unstar
api.star(username, token, salt, id = null, albumId = null, artistId = null)
```

#### Correct
```kotlin
// Always pass the relevant ID type
api.star(username, token, salt, albumId = album.id)
// Or for a song:
api.star(username, token, salt, id = song.id)
```

## Scenario: Smart Radio, Scrobbling, and Play History

### 1. Scope / Trigger
- Trigger: User starts smart radio from the music player, a song starts playback, or a song crosses the play-submission threshold.
- Scope: `GET /getSimilarSongs`, `GET /getRandomSongs`, `GET /scrobble`, `PlayHistoryRepository`, music player/app-shell scrobble orchestration.
- Out of scope: Last.fm scrobbling, server-side play-history browsing, and smart playlist mutation.

### 2. Signatures
- Retrofit:
```kotlin
@GET("rest/getSimilarSongs.view")
suspend fun getSimilarSongs(@Query("u") username: String, ..., @Query("id") id: String, @Query("count") count: Int = 50): Response<SubsonicResponse>

@GET("rest/getRandomSongs.view")
suspend fun getRandomSongs(@Query("u") username: String, ..., @Query("size") size: Int = 20): Response<SubsonicResponse>

@GET("rest/scrobble.view")
suspend fun scrobble(@Query("u") username: String, ..., @Query("id") id: String, @Query("submission") submission: Boolean): Response<SubsonicResponse>
```
- Repository:
```kotlin
suspend fun getSimilarSongs(songId: String): List<NavidromeSong>
suspend fun getRandomSongs(count: Int = 20): List<NavidromeSong>
suspend fun scrobble(songId: String, submission: Boolean)
```
- Local persistence:
```kotlin
data class PlayHistoryEntry(val songId: String, val timestamp: Long, val playCount: Int = 1)
suspend fun PlayHistoryRepository.load(): List<PlayHistoryEntry>
suspend fun PlayHistoryRepository.recordPlay(songId: String)
```

### 3. Contracts
- All Subsonic calls require auth params `u`, `t`, `s`, `v`, `c`, and `f=json` where applicable; `NavidromeRepository` owns auth parameter construction.
- Smart radio first requests `getSimilarSongs(id=<currentSongId>, count=50)`. If it returns no songs, fall back to `getRandomSongs(size=20)`.
- Smart-radio results must be mapped through existing song mapping helpers so cover art and playable stream URLs are populated before enqueueing.
- `scrobble(submission=false)` is sent when a song starts to mark now-playing.
- `scrobble(submission=true)` is sent once per song transition after either at least 50% of known duration has played or playback position reaches 240 seconds.
- Do not calculate the 50% threshold from a zero or not-yet-loaded playback duration. Re-read playback duration inside the polling loop and fall back to the song DTO duration; if both are unknown, only the 240-second threshold may submit.
- Local play history is app-owned DataStore state keyed by Navidrome song id. Replaying an existing song moves it to the front, updates `timestamp`, and increments `playCount`.
- The home "Recently Played" section should resolve history IDs from the music library/cache data, not only from the active playback queue.

### 4. Validation & Error Matrix
- Non-2xx or Subsonic error from smart radio/scrobble -> preserve `NavidromeApiException`.
- Unknown repository error -> wrap with context such as `"获取相似歌曲失败: ..."` or `"记录播放失败: ..."`.
- Empty similar-song response -> call `getRandomSongs`; empty fallback -> show a no-results message and leave the queue unchanged.
- Missing local song metadata for a play-history ID -> skip that ID in UI rather than rendering a partial row.
- Malformed play-history JSON -> return an empty history list; do not crash the app shell.

### 5. Good/Base/Bad Cases
- Good: Smart radio adds mapped similar songs after the current queue item and shows the added count.
- Base: Similar songs are empty, random songs are enqueued instead.
- Bad: Scrobble submission fires 10 seconds into a track because duration was initially `0`; this over-reports plays.
- Bad: Recently Played only checks the current queue, so prior plays disappear after app restart despite cached library data being available.

### 6. Tests Required
- `getSimilarSongs_callsEndpointAndMapsSongs`: assert path, `id`, `count=50`, and stream URL mapping.
- `getRandomSongs_callsEndpointAndMapsSongs`: assert path, `size`, and stream URL mapping.
- `scrobble_callsEndpointWithSubmissionTrue/False`: assert path, `id`, and `submission`.
- For non-trivial scrobble threshold changes, isolate the threshold decision in a pure helper and test unknown duration, half duration, and 240-second cases.

### 7. Wrong vs Correct

#### Wrong
```kotlin
val duration = playbackState.durationSeconds.coerceAtLeast(1)
val playedRatio = playbackState.positionSeconds.toFloat() / duration
if (playedRatio >= 0.5f) {
    repo.scrobble(song.id, submission = true)
}
```

#### Correct
```kotlin
val duration = playbackState.durationSeconds.takeIf { it > 0 } ?: song.duration
val playedRatio = if (duration > 0) {
    playbackState.positionSeconds.toFloat() / duration.toFloat()
} else {
    0f
}
if (playedRatio >= 0.5f || playbackState.positionSeconds >= 240) {
    repo.scrobble(song.id, submission = true)
}
```

## Scenario: Media URL Disk Cache Hygiene (Query Auth)

### 1. Scope / Trigger
- Trigger: Any change to `NavidromeRepository` stream/cover URL construction, `MusicPlaybackService` `CacheKeyFactory`, Coil `ImageLoader`, or `AuthedAsyncImage`.
- Subsonic/Navidrome auth is strictly query-param based (`u`, `t`, `s`, `v`, `c`); there is no header option. The token `t` is a one-time salted MD5 (`md5(password+salt)`), not the reusable password, so a persisted URL exposes a replay token for that request only. Disk hygiene keeps even that one-time token off disk.

### 2. Signatures
- `internal fun stripAuthQuery(url: String): String` (`data/AuthUrl.kt`)
- `MusicPlaybackService` `CacheDataSource.Factory().setCacheKeyFactory { dataSpec -> stripAuthQuery(dataSpec.uri) }`
- `internal fun AuthedAsyncImage(url: String?, ...)` (`ui/AuthedAsyncImage.kt`) — `diskCacheKey`/`memoryCacheKey` = `stripAuthQuery(url)`

### 3. Contracts
- Navidrome stream/cover URLs keep `u`/`t`/`s`/`v`/`c` query params in the actual request URL (Subsonic protocol requires them); `stripAuthQuery` is used ONLY as the `CacheKeyFactory`/`diskCacheKey` so the disk cache index stores the de-authed URL.
- Do NOT register Navidrome auth into `MediaAuthHeaderRegistry` (no header auth exists); do NOT strip auth from the request URL (only from the cache key).
- `AuthedAsyncImage` is used for Navidrome cover art so Coil's disk cache key is the de-authed URL.
- The OkHttp client in `MusicPlaybackService` may still include `MediaAuthHeaderInterceptor` (it no-ops for Navidrome origins because nothing is registered), but it must not strip Navidrome query params from the request.

### 4. Validation & Error Matrix
| Condition | Behavior |
|---|---|
| Navidrome stream/cover request | URL keeps `u/t/s/v/c`; cache key strips them; `exo_player_cache` index and Coil disk cache hold only de-authed URLs |
| OkHttp logging raised above `NONE` | Tokens can hit logcat — keep `HttpLoggingInterceptor.Level.NONE` (see logging-guidelines) |
| A future change adds a Navidrome header-auth option | Register into `MediaAuthHeaderRegistry` and drop query params from the request URL; update this contract |

### 5. Good/Base/Bad Cases
- Good: `exo_player_cache` `cached_content_index` and Coil disk cache contain only de-authed Navidrome URLs; the one-time `t` hash never reaches disk.
- Base: Navidrome playback works because the request URL still carries auth query params.
- Bad: `stripAuthQuery` is applied to the actual request URL (not just the cache key); Navidrome requests 401 because Subsonic requires query auth.

### 6. Tests Required
- `AuthUrlTest`: `stripAuthQuery` removes `u`/`t`/`s`/`v`/`c` from a Navidrome sample URL and preserves non-auth params.
- `NavidromeRepositoryTest`: stream/cover URLs still include `u`/`t`/`s`/`v`/`c` (request URL unchanged); the cache-key path uses `stripAuthQuery`.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Stripping auth from the request URL breaks Subsonic auth (no header option exists).
val requestUrl = stripAuthQuery(dataSpec.uri.toString())
```

#### Correct
```kotlin
// Auth stays in the request URL; only the cache key is de-authed.
CacheDataSource.Factory()
    .setCache(cache)
    .setCacheKeyFactory { dataSpec -> stripAuthQuery(dataSpec.uri) }
```

## Scenario: 播放 Scrobble（两段式）

### 1. 范围 / 触发条件

修改音乐播放上报（scrobble/now-playing）、播放计数或「最近播放」同步时读取。Subsonic 协议无进度（position）上报概念，只有 `scrobble.view?id=&submission=`。

### 2. 关键签名

- `NavidromeRepository.scrobble(songId: String, submission: Boolean)`（`GET rest/scrobble.view`，`submission=false` 为 now-playing，`true` 为提交播放记录）。
- `internal fun shouldScrobbleMusicSubmission(positionSeconds, durationSeconds, alreadySubmitted): Boolean`（`MusicPlaybackViewModel.kt`）。
- `MusicPlaybackViewModel` 状态机字段：`nowPlayingSongId: String?`、`submittedSongId: String?`。

### 3. 可执行合同

- **两段式**：开始播放一首歌（`currentSong.id` 变化）→ `submission=false`；过半（`position >= duration/2`，`duration > 0`）或切歌/停止离开该歌 → `submission=true`。每首歌只提交一次。
- 状态机：切歌时若 `previousSongId != submittedSongId` 先提交前一首（置 `submittedSongId`），再对新车发 now-playing（`songId != submittedSongId` 时）。过半路径由播放状态流驱动，`submittedSongId == songId` 即跳过。
- duration ≤ 0（未知）时过半规则不触发，提交只能由切歌路径完成。
- **失败静默**：`runCatching` + `Log.w`，不重试、不弹 UI（与收藏失败 UX 分离——scrobble 失败用户不可感知）。
- repo 未就绪（null）或 songId 空白时直接跳过，不发请求。
- 过半判定必须保持纯函数以便单测；ViewModel 只做状态机与 IO。

### 4. 验证与错误矩阵

| 条件 | 行为 |
|---|---|
| 播放至 duration/2 | 提交一次 submission=true |
| 过半前切歌 | 切歌路径提交前一首 |
| 同一首歌再次过半/切回 | 不重复提交 |
| duration 未知（≤0） | 过半规则不触发；切歌时提交 |
| scrobble 网络失败 | Log.w 静默；播放与 UI 不受影响 |
| repo 未配置 | 跳过请求 |

### 5. 正常 / 基础 / 错误案例

- 正常：完整听一首歌 → 服务器「最近播放」+1、播放计数 +1、正在播放可见。
- 基础：快速切歌（<半首）→ 前一首仍计一次播放。
- 错误：重复提交同一首；scrobble 失败弹出 UI 错误；把 position 进度上报当 scrobble 发。

### 6. 必需测试

- `MusicPlaybackEngineTest`：`shouldScrobbleMusicSubmission` 过半边界、once 语义、duration 未知行为。
- compile + 完整单测 + lint + assemble；真机验收确认 Navidrome 最近播放/计数更新。

### 7. 错误与正确示例

```kotlin
// 错误：每次位置更新都发 submission=true，播放计数爆炸。
if (positionSeconds >= durationSeconds / 2) repo.scrobble(songId, true)

// 正确：once 标志 + 纯函数判定。
if (shouldScrobbleMusicSubmission(positionSeconds, durationSeconds, alreadySubmitted)) {
    submittedSongId = songId
    submitScrobble(repo, songId, submission = true)
}
```
