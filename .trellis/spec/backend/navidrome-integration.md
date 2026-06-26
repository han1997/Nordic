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
