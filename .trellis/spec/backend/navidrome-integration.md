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
