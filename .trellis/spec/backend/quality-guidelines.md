# Quality Guidelines

> Code quality standards for the Nordic media hub app.

---

## Overview

This document records project-specific code quality conventions discovered during development.

---

## Forbidden Patterns

### String-based error classification

**Don't**: Use `e.message?.contains("keyword")` to distinguish error types.
**Why**: Any message rewording or i18n change silently breaks the classification.
**Do**: Use typed exceptions (e.g., `NavidromeApiException`) with enum kinds.

### Duplicate utility functions

**Don't**: Create `formatPlayerDuration()` in one file and `formatTrackDuration()` in another when they do the same thing.
**Do**: Single source of truth in a shared file (`MusicFormatters.formatDuration()`).

---

## Required Patterns

### Shared Compose components must be `internal`

Small UI primitives reused across files (e.g., `MusicMetaChip`, `rememberPressScale`) must be `internal` (not `private`) so they can be imported without exposing them as public API. Private helpers that are duplicated across files should be lifted to a shared file with `internal` visibility.

### Shared media state surfaces

Top-level Music, Audiobook, and Video screens should use the shared media state components instead of reimplementing one-off loading, error, and empty cards:

```kotlin
MediaStateCard(
    title = "连接失败",
    subtitle = errorMessage,
    tone = MediaStateTone.Error
)

MediaLoadingCard(
    title = "正在同步 Navidrome",
    subtitle = "加载专辑、歌曲和歌手..."
)
```

Use `MediaStateDensity.Compact` for detail-level empty states and the default prominent density for first-run/setup/empty-library states.

**Why**: These surfaces encode the design-system alpha levels (`0.72f` empty, `0.76f` loading, error container for errors). Repeating local `Surface` blocks causes visual drift and makes copy/encoding fixes harder to audit.

### Compose media list stability

Image-heavy `LazyColumn` and `LazyRow` sections should provide stable `key` values from domain identity and a stable `contentType` for each row/card family. Small fixed control rows such as sort chips do not need this.

Cache preview slices with `remember(sourceList) { sourceList.take(n) }` when they are passed into lazy lists or playback callbacks, and prefer `itemsIndexed` when item click handling needs the index.
Do not resolve playback click indexes with `list.indexOf(song)`; duplicate song entries can resolve to the wrong item and large lists pay an unnecessary O(n) lookup on click. Use the index provided by `itemsIndexed` and position-aware keys such as `"playlist-song-${song.id}-$index"`.
When a shelf is only a visual preview of a longer playback source, keep the preview slice separate from the playback queue. For example, the Music home recently-added shelf may render 12 cards but should pass the full `recentlyAddedSongs` backing list to playback so listening continues beyond the visible preview.

Playback scrubbers should keep local scrub state while dragging and call the playback engine's `seekTo(...)` only from `onValueChangeFinished`. Do not call seek on every slider `onValueChange`; it can flood Media3 with repeated seeks and make video/audio playback stutter.

```kotlin
val homeSongs = remember(recentlyAddedSongs) { recentlyAddedSongs.take(12) }

LazyRow {
    itemsIndexed(
        items = homeSongs,
        key = { _, song -> "home-song-${song.id}" },
        contentType = { _, _ -> "home-song-card" }
    ) { index, song ->
        SongShelfCard(
            song = song,
            onClick = { onSongSelected(homeSongs, index) }
        )
    }
}
```

**Why**: Stable keys preserve item identity across inserts/reorders, `contentType` lets Compose reuse compatible item composition, and remembered slices avoid allocating new preview lists on unrelated recompositions.

### Config readiness checks centralized

`NavidromeConfig.isReadyForMusicSync()` is defined once in `ServerConfig.kt` and imported where needed. Do not inline `serverUrl.isNotBlank() && username.isNotBlank()` or create duplicate extension functions.

### Media repository instance reuse

When a composable or screen needs a media repository such as `NavidromeRepository`, `AudiobookShelfRepository`, or `EmbyRepository`, use `remember(config) { Repository(config) }` keyed on config changes. Do not construct repositories inline in every refresh/list-detail suspend call when the saved config already has a remembered repository. Each construction creates a new Retrofit + OkHttpClient.

```kotlin
val navidromeRepository = remember(savedConfig) {
    if (savedConfig.isReadyForMusicSync()) NavidromeRepository(savedConfig) else null
}

val repo = if (targetConfig == savedConfig) {
    navidromeRepository ?: NavidromeRepository(targetConfig)
} else {
    NavidromeRepository(targetConfig)
}
```

Construct a temporary repository only for unsaved form values being tested/saved, where `targetConfig != savedConfig`.

### Navidrome all-song navigation data

The Music tab's "歌曲" navigation must use all songs, not the recently added preview list.

**Scope / Trigger**: Any change to the Music screen song tab, `NavidromeRepository` song loading, or `NavidromeMusicCache` song fields.

**Signatures**:
- `data class NavidromeSong(..., val created: String? = null)` must preserve the Subsonic song `created` timestamp for added-time sorting.
- `NavidromeApi.getAlbumList2(..., type: String, size: Int, offset: Int)` must expose `offset` so repositories can page through album lists.
- `NavidromeRepository.getAllSongs(): List<NavidromeSong>` is the source for the song navigation page.
- `NavidromeRepository.getRecentlyAddedSongs(albums, limit)` is only for recently added preview surfaces.

**Contract**:
- All songs are loaded by paging `getAlbumList2(type = "alphabeticalByName", size = ALBUM_PAGE_SIZE, offset = n)` until a short page or empty page, then expanding each album with `getAlbum`.
- Song added-time sorting is a UI sort over `NavidromeSong.created`, newest first. Do not use the limited `recentlyAddedSongs` preview as the Songs page data source.
- Home "最近添加" may show `recentlyAddedSongs`, but selecting navigation "歌曲" must render cached/refreshed `songs` from `getAllSongs()`.
- When changing `NavidromeMusicCache` field semantics, bump `MUSIC_CACHE_SCHEMA_VERSION` so stale cached data is not displayed under the new meaning.

**Validation & Error Matrix**:
- Subsonic/HTTP error while paging albums -> preserve `NavidromeApiException`.
- Unknown failure while loading all songs -> wrap as `"获取全部歌曲失败: ..."` for UI context.
- Empty album list -> return an empty song list, do not fall back to random songs.
- Missing song `created` value -> added-time sort keeps those songs after timestamped songs via empty-string fallback, then title tiebreaker.

**Good/Base/Bad Cases**:
- Good: Song tab displays every track returned by all paged albums.
- Base: Recently added carousel displays only the limited recent-song set.
- Bad: Song tab uses `getRecentlyAddedSongs(...)` or `getRandomSongs(...)`, or drops `created` during DTO mapping / Media3 queue conversion.

**Tests Required**:
- Repository unit test with `MockWebServer` asserting `getAllSongs()` requests `type=alphabeticalByName`, `size=100`, `offset=0`, then expands album tracks via `getAlbum`.
- Repository unit test asserting album song JSON `created` is preserved on returned `NavidromeSong`.

**Wrong vs Correct**:
```kotlin
// Wrong: binds the Songs navigation page to a recent preview.
val freshSongs = repo.getRecentlyAddedSongs(freshAlbums)

// Correct: keep preview and all-song navigation separate.
val freshRecentlyAddedSongs = repo.getRecentlyAddedSongs(freshAlbums)
val freshSongs = repo.getAllSongs()
```

### Navidrome album browsing sort modes

**Scope / Trigger**: Any change to the Music screen album page, album sort UI, `NavidromeRepository.getAlbums(...)`, or `NavidromeApi.getAlbumList2(...)`.

**Signatures**:
- `enum class NavidromeAlbumSort { RecentlyAdded, ReleaseYear, Name }`
- `NavidromeApi.getAlbumList2(..., type: String, size: Int, offset: Int, fromYear: Int? = null, toYear: Int? = null)`
- `suspend fun NavidromeRepository.getAlbums(sort: NavidromeAlbumSort): List<NavidromeAlbum>`

**Contract**:
- Album browsing is entered from the Music home album section's "All" action; do not replace the top-level Music tabs unless the PRD explicitly changes that scope.
- `RecentlyAdded` maps to `getAlbumList2(type = "newest")`.
- `ReleaseYear` maps to `getAlbumList2(type = "byYear", fromYear = 2100, toYear = 1900)` so the API returns newest release years first across a broad year range.
- `Name` maps to `getAlbumList2(type = "alphabeticalByName")`.
- Album browsing should page with `size = 100` and `offset = n` until an empty or short page.
- The sorted album list is view state, not part of `NavidromeMusicCache`; cached `albums` remains the home/recent album preview unless the cache contract is explicitly revised and schema-bumped.

**Validation & Error Matrix**:
- Subsonic/HTTP error while loading sorted albums -> preserve `NavidromeApiException`.
- Unknown failure while loading sorted albums -> wrap as `"获取专辑列表失败: ..."` for UI context.
- Empty sorted album response -> show album empty state; do not fall back to songs or random albums.

**Good/Base/Bad Cases**:
- Good: Home shows recent album previews, "All" opens album browsing, and sort chips refetch with the mapped API type.
- Base: Recently added sort shows the same ordering family as the home album preview but can page beyond the preview limit.
- Bad: Release-year sorting uses alphabetical albums and sorts locally; this can be incorrect across paged server data.

**Tests Required**:
- Repository unit test with `MockWebServer` asserting `getAlbums(...)` sends `type=newest`, `type=byYear&fromYear=2100&toYear=1900`, and `type=alphabeticalByName` for the three sort modes.

**Wrong vs Correct**:
```kotlin
// Wrong: local sort after fetching alphabetical data can miss server-side page ordering.
val albums = repo.getAlbums(NavidromeAlbumSort.Name).sortedByDescending { it.year ?: 0 }

// Correct: request the server-side album list type for the selected sort.
val albums = repo.getAlbums(NavidromeAlbumSort.ReleaseYear)
```

### Navidrome playlist browsing

**Scope / Trigger**: Any change to Music playlist UI, `NavidromeApi` playlist DTOs/endpoints, or `NavidromeRepository` playlist methods.

**Signatures**:
- `SubsonicData.playlists: NavidromePlaylistList?`
- `SubsonicData.playlist: NavidromePlaylistDetail?`
- `NavidromeApi.getPlaylists(...): Response<SubsonicResponse>`
- `NavidromeApi.getPlaylist(..., playlistId: String): Response<SubsonicResponse>`
- `suspend fun NavidromeRepository.getPlaylists(): List<NavidromePlaylist>`
- `suspend fun NavidromeRepository.getPlaylistSongs(playlistId: String): List<NavidromeSong>`

**Contract**:
- Playlist browsing is read-only unless the PRD explicitly includes playlist mutation.
- `getPlaylists()` calls Subsonic `getPlaylists.view` and maps `playlists.playlist`.
- `getPlaylistSongs(playlistId)` calls `getPlaylist.view` with `id=<playlistId>` and maps `playlist.entry`.
- Playlist and playlist-song cover art must be converted through `getCoverArt.view`; playlist detail cover art may be used as a fallback for entries with missing `coverArt`.
- Playlist entries returned to UI must include playable `streamUrl` values built through `stream.view`.
- Playlist data is view state, not part of `NavidromeMusicCacheRepository`, unless the cache schema is explicitly revised and version-bumped.
- The Music playlist tab should show an empty state for empty responses, not a "coming soon" placeholder.

**Validation & Error Matrix**:
- Subsonic/HTTP error while loading playlists or playlist detail -> preserve `NavidromeApiException`.
- Unknown failure while loading playlists -> wrap as `"获取歌单失败: ..."` for UI context.
- Unknown failure while loading playlist songs -> wrap as `"获取歌单曲目失败: ..."` for UI context.
- Empty `playlists` or missing `playlist` detail -> return empty lists; do not fall back to albums, songs, or random songs.

**Good/Base/Bad Cases**:
- Good: Playlist tab loads real Navidrome playlists, opens detail, and plays the detail song list through the existing `onSongSelected(list, index)` flow.
- Base: Empty server playlist list shows a compact empty state.
- Bad: Playlist tab remains a hard-coded placeholder after playlist APIs exist, or UI calls Retrofit directly instead of `NavidromeRepository`.

**Tests Required**:
- Repository unit test asserting `getPlaylists()` requests `/rest/getPlaylists.view` and maps playlist fields plus cover art URLs.
- Repository unit test asserting `getPlaylistSongs(id)` requests `/rest/getPlaylist.view?id=<id>`, maps entries, applies fallback cover art, and builds stream URLs.
- Compile/lint checks for UI callback wiring.

**Wrong vs Correct**:
```kotlin
// Wrong: UI bypasses repository mapping and auth conventions.
api.getPlaylist(username, token, salt, playlistId = id)
```

```kotlin
// Correct: UI asks repository for app-facing playable songs.
val songs = repo.getPlaylistSongs(playlist.id)
onSongSelected(songs, index)
```

### Navidrome music detail loading errors

**Scope / Trigger**: Any change to Music album detail, artist detail, or playlist detail loading paths in `MusicScreenV2`.

**Signatures**:
- `internal fun musicAlbumDetailLoadErrorMessage(error: Throwable): String`
- `internal fun musicArtistDetailLoadErrorMessage(error: Throwable): String`

**Contract**:
- Starting album or artist detail navigation must clear the previous screen-level `errorMsg` before launching the detail load.
- Album detail load failures must set a contextual error such as `"获取专辑曲目失败: ..."` instead of swallowing the exception.
- Artist detail load failures must set a contextual error such as `"获取歌手专辑失败: ..."` instead of swallowing the exception.
- Do not convert network/API failures into valid empty album or empty artist states. Empty states are only for successful empty responses.
- Playlist detail loading should keep its existing contextual error pattern and remain the reference for detail-load failure behavior.

**Validation & Error Matrix**:
- Album detail repository call throws -> set album-detail load error and stop the loading indicator.
- Artist detail repository call throws -> set artist-detail load error and stop the loading indicator.
- Error has no message -> use a non-empty fallback message such as `"未知错误"`.
- Detail load starts after an older error -> clear the old error before showing loading/content for the new target.

**Good/Base/Bad Cases**:
- Good: Album songs request fails and the Music tab shows a contextual album-song load error.
- Good: Artist albums request fails and the Music tab shows a contextual artist-album load error.
- Base: Album or artist detail request succeeds with an empty list; show the appropriate empty detail state.
- Bad: Catching `Exception` with an empty catch block, causing a failed request to look like a real empty album or artist.

**Tests Required**:
- Unit tests for album and artist detail error-message helpers, including the context prefix and cause/fallback.

### Navidrome lyrics parsing

**Scope / Trigger**: Any change to `NavidromeRepository.getLyrics(...)`, lyric DTOs, plain LRC parsing, or `MusicPlayerScreen` lyric rendering.

**Signatures**:
- `suspend fun NavidromeRepository.getLyrics(song: NavidromeSong): MusicLyrics?`
- `data class MusicLyrics(val lines: List<MusicLyricsLine>, val synced: Boolean)`
- `data class MusicLyricsLine(val startMillis: Int? = null, val text: String)`

**Contract**:
- Try `getLyricsBySongId.view` first; fall back to `getLyrics.view` only when song-id lookup yields no lyrics and the song has a non-blank artist.
- Structured lyrics take priority over plain `lyrics.value` when they contain non-blank lines.
- OpenSubsonic structured lyrics use millisecond `line.start` values. Preserve those values as milliseconds before applying any offset.
- OpenSubsonic `structuredLyrics.offset` is optional and in milliseconds. Positive means lyrics appear sooner and negative means later, so subtract the signed offset from each structured line start. Clamp adjusted timestamps below zero to `0`.
- Plain LRC timestamp rows such as `[00:10.00]Line` become synced `MusicLyricsLine(startMillis = 10000, text = "Line")`.
- Known LRC metadata-only rows such as `[ar:...]`, `[ti:...]`, `[al:...]`, `[length:...]`, and `[offset:...]` must be skipped instead of displayed in the lyric view.
- Plain LRC `[offset:<signed milliseconds>]` applies globally to timestamped rows. Follow LRC semantics: positive offsets make lyrics appear sooner, so subtract the signed offset from parsed timestamps. Clamp adjusted timestamps below zero to `0`.
- Bracketed plain lyric rows that are not known LRC metadata, such as `[Chorus]` or `[custom:Keep this line]`, remain visible lyric text.
- Empty or whitespace-only parsed rows are ignored; if nothing remains, return `null`.

**Validation & Error Matrix**:
- Song-id lyric lookup throws or returns no usable lyrics -> fallback to artist/title lookup when possible.
- Both lyric lookups fail or return no usable lyrics -> return `null`; do not surface a playback error.
- Structured line start `2000` with no offset -> `startMillis = 2000`.
- Structured offset `250` with line start `2000` -> `startMillis = 1750`.
- Structured offset `-250` with line start `2000` -> `startMillis = 2250`.
- Structured positive offset pushes an adjusted timestamp below zero -> clamp to `0`.
- Metadata-only LRC rows -> omit from `MusicLyrics.lines`.
- `[offset:+500]` with `[00:10.00]Line` -> `startMillis = 9500`.
- `[offset:-500]` with `[00:10.00]Line` -> `startMillis = 10500`.
- Positive offset pushes an adjusted timestamp below zero -> clamp to `0`.
- Non-metadata bracketed plain rows -> keep as unsynced text.

**Good/Base/Bad Cases**:
- Good: LRC with metadata and two timed rows displays only the two lyric rows, synced to timestamps.
- Good: Structured lyrics preserve OpenSubsonic millisecond starts and apply the structured offset without showing any metadata.
- Good: LRC offset metadata shifts all timed rows by the file's intended global adjustment without showing the offset row.
- Base: Plain unsynced lyrics display in source order.
- Bad: Structured offset is ignored, leaving all synced lines consistently early or late.
- Bad: Structured `line.start = 2000` is treated as seconds and becomes `2000000`.
- Bad: `[ar:Artist]` or `[offset:+500]` appears as a lyric line in `MusicPlayerScreen`.
- Bad: `[offset:+500]` is added to `startMillis`, making lyrics appear later instead of sooner.
- Bad: `[Chorus]` is dropped just because it uses brackets.

**Tests Required**:
- Repository unit test with `MockWebServer` asserting known LRC metadata rows are skipped while timed lyric rows keep expected `startMillis`.
- Repository unit tests asserting structured lyric millisecond starts are preserved with no offset, positive structured offsets make lines earlier, negative structured offsets make lines later, and adjusted timestamps below zero clamp to `0`.
- Repository unit tests asserting positive offset makes lines earlier, negative offset makes lines later, and adjusted timestamps below zero clamp to `0`.
- Repository unit test asserting non-metadata bracketed plain rows remain visible and unsynced.

**Wrong vs Correct**:
```kotlin
// Wrong: every non-timestamp bracket row is treated as lyric text.
if (matches.isEmpty()) listOf(MusicLyricsLine(text = text))
```

```kotlin
// Correct: skip only known metadata rows and preserve other bracketed lyric text.
if (matches.isEmpty() && rawLine.isLrcMetadataLine()) emptyList()
```

### Music playback queue management

**Scope / Trigger**: Any change to the music queue sheet, `MusicPlaybackEngine`, `MusicPlaybackState.queue`, `MusicPlaybackState.queueIndex`, or Media3 music playlist mutations.

**Signatures**:
- `data class MusicPlaybackState(..., val queue: List<NavidromeSong>, val queueIndex: Int)`
- `data class MusicPlaybackState(..., val shuffleModeEnabled: Boolean)`
- `fun MusicPlaybackEngine.playQueue(songs: List<NavidromeSong>, startIndex: Int = 0, allowUnplayableStartFallback: Boolean = true)`
- `internal fun resolvePlayableMusicQueue(songs: List<NavidromeSong>, startIndex: Int, allowUnplayableStartFallback: Boolean = true): PlayableMusicQueue?`
- `fun MusicPlaybackEngine.seekToQueueIndex(index: Int)`
- `fun MusicPlaybackEngine.moveQueueItemToPlayNext(index: Int)`
- `fun MusicPlaybackEngine.removeQueueItem(index: Int)`
- `fun MusicPlaybackEngine.clearUpcomingQueueItems()`
- `fun MusicPlaybackEngine.toggleShuffleMode()`
- `internal fun resolvePlayNextTargetIndex(index: Int, currentIndex: Int, itemCount: Int): Int?`

**Contract**:
- `MusicPlaybackEngine` owns all queue mutations. Compose UI passes commands and renders `MusicPlaybackState`; it must not maintain or reorder a shadow queue.
- `MusicPlaybackEngine.playQueue(...)` must filter out songs whose `streamUrl` is null or blank before creating Media3 media items. Media3 should not receive empty-URI queue entries.
- When filtering a queue, preserve the requested start song if it is playable. If it is not playable and `allowUnplayableStartFallback = true`, map the start to the next playable song after the requested position, or the nearest earlier playable song when no later playable song exists.
- Direct song row/card selection must call `playQueue(..., allowUnplayableStartFallback = false)` through the Music screen callback. In this mode, an unplayable requested start returns no queue even when later songs are playable, and `playQueue(...)` publishes `"这首歌缺少播放地址"` instead of silently starting another track.
- If a requested queue has no playable songs, clear pending queue requests, keep existing playback state intact, and publish a contextual error instead of replacing Media3 items.
- After any Media3 playlist mutation, invalidate cached queue state (`cachedTimelineGeneration = -1`) before publishing state so `queue` and `queueIndex` cannot stay stale.
- Queue commands must guard invalid indexes. Invalid/current/already-next "play next" requests are no-ops.
- Removing the only queued item stops playback and clears state.
- `clearUpcomingQueueItems()` preserves the current item and removes only items after `queueIndex`.
- Queue UI keys must tolerate duplicate songs in the queue; do not key rows by `song.id` alone.
- If the Media3 controller is not connected yet, mutate `pendingQueue` and `_state` consistently so the eventual controller start uses the same queue order/index shown in UI.
- Pending-queue current-index recalculation must be position-based, not `song.id` based. Queues may contain the same song multiple times, so using `indexOfFirst { it.id == currentSong.id }` can highlight or start the wrong duplicate after a pending reorder.
- Shuffle mode is player state, not a locally shuffled list. `MusicPlaybackEngine.toggleShuffleMode()` must delegate to Media3 `shuffleModeEnabled`, publish `MusicPlaybackState.shuffleModeEnabled`, and listen for `onShuffleModeEnabledChanged`.

**Validation & Error Matrix**:
- Index outside `0 until mediaItemCount` -> no-op.
- Current index outside queue bounds -> no-op for queue reordering/clearing.
- "Play next" on the current item or the item already immediately after current -> no-op.
- Queue contains songs with missing/blank `streamUrl` -> filter them before Media3 playlist creation.
- Direct selection requested start has missing/blank `streamUrl` and fallback disabled -> do not replace Media3 items; publish `"这首歌缺少播放地址"`.
- Queue contains no playable songs -> do not replace the active Media3 playlist; publish an error.
- Remove single-item queue -> call `stop()` and clear `MusicPlaybackState`.
- Duplicate song ids in queue -> use position-aware UI keys such as `"$id:$index"`.
- Duplicate song ids in a pending queue -> preserve the current position by index math after moves/removals; do not search by song id.
- Shuffle toggle -> update Media3 `shuffleModeEnabled`; UI should render active state from `MusicPlaybackState.shuffleModeEnabled`.

**Good/Base/Bad Cases**:
- Good: Queue sheet actions call `MusicPlaybackEngine`, engine mutates Media3, then publishes fresh `queue` and `queueIndex`.
- Good: Play-all queue contains unavailable tracks; playback engine queues only playable songs and maps the current index to the intended playable start.
- Good: Direct row/card click on an unavailable song passes fallback disabled and surfaces the missing-stream error without starting a different song.
- Base: Tapping a queue row seeks with `seekToQueueIndex(index)` and closes the sheet.
- Bad: `playQueue(...)` maps every `NavidromeSong` to a Media3 item even when `streamUrl` is blank; continuous playback can fail when the player reaches that entry.
- Bad: Direct row/card click uses fallback-enabled queue resolution and starts the next playable song after the unavailable row.
- Bad: UI removes a row from a local list while Media3 keeps the original playlist, causing the highlighted item and actual playback item to diverge.
- Bad: Pending queue reordering finds the current item by `song.id`, causing duplicate queued songs to shift the current index to the first matching duplicate.
- Bad: UI shuffles its own list while Media3 keeps the original timeline, causing next/previous and queue sheet state to disagree.

**Tests Required**:
- Unit tests for pure queue index rules, especially future item, previous item, current item, already-next item, and invalid indexes.
- Unit tests for playable queue resolution: filtering blank stream URLs, preserving a playable requested start, mapping an unplayable start to next/previous playable entries, returning null for fallback-disabled unplayable starts, and all-unplayable queues.
- Unit tests for pending current-index recalculation when items before/after the current item move.
- Compile checks for Media3 API usage and callback wiring.
- Unit tests or focused helper tests when adding non-trivial queue ordering logic.

**Wrong vs Correct**:
```kotlin
// Wrong: local UI state diverges from Media3 playback state.
visibleQueue = visibleQueue.toMutableList().also { it.removeAt(index) }
```

```kotlin
// Correct: command goes through playback layer and UI observes published state.
onRemoveFromQueue = playbackEngine::removeQueueItem
```

### Music play-all start index

**Scope / Trigger**: Any change to Music screen bulk playback entry points, including album quick-play, album detail play-all, artist detail play-all, or playlist detail play-all.

**Signature**:
- `internal fun firstPlayableSongIndex(songs: List<NavidromeSong>): Int?`
- `onSongSelected: (List<NavidromeSong>, Int, Boolean) -> Unit`

**Contract**:
- Bulk playback must start at the first song whose `streamUrl` is not null or blank.
- If no songs in the list are playable, show a contextual UI error and do not call `onSongSelected`.
- Bulk play-all entry points must call `onSongSelected(songs, firstPlayableIndex, BULK_PLAY_ALLOW_UNPLAYABLE_START_FALLBACK)`.
- Do not change single song-row click behavior; a direct click calls `onSongSelected(songs, clickedIndex, DIRECT_SELECTION_ALLOW_UNPLAYABLE_START_FALLBACK)` so the playback engine can surface the specific song error.

**Good/Base/Bad Cases**:
- Good: Album or playlist starts at track 2 when track 1 has no stream URL and track 2 is playable.
- Good: Tapping the unplayable track 1 directly surfaces `"这首歌缺少播放地址"` instead of starting track 2.
- Base: All tracks are playable, so play-all starts at index `0`.
- Bad: Play-all blindly calls `onSongSelected(songs, 0)` and fails before reaching playable tracks later in the list.
- Bad: Direct row clicks reuse play-all's first-playable index and ignore the clicked row.

**Tests Required**:
- Unit tests for `firstPlayableSongIndex(...)` covering playable first match, null/blank stream URLs, and no playable songs.

---

## Testing Requirements

Run the smallest reliable Gradle gate for the change, and run tasks sequentially on Windows.

- Kotlin compile check for app code changes:
  `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
- Unit tests for repository, auth, playback, or copy-encoding behavior:
  `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
- Android lint before broader UI/resource/dependency changes:
  `.\gradlew.bat :app:lintDebug --no-daemon`
- Debug assemble for final packaging verification when Media3 service, manifest, resources, or dependency wiring changes:
  `.\gradlew.bat :app:assembleDebug --no-daemon`

Repository tests use `MockWebServer` to assert request paths, query parameters, auth headers, response mapping, and typed error behavior. Existing examples:
- `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`

Playback logic tests should isolate pure calculations where possible, as in `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.

---

## Code Review Checklist

- [ ] No string-based error type checks (use typed exceptions)
- [ ] No duplicate utility functions across files
- [ ] Shared Compose primitives use `internal` visibility
- [ ] Repeated loading/error/empty state cards use shared media state components
- [ ] `NavidromeRepository` is `remember`ed, not constructed per call
- [ ] Config readiness checks use `isReadyForMusicSync()`, not inlined
- [ ] All public `NavidromeRepository` methods have both `NavidromeApiException` and `Exception` catch blocks
- [ ] Music "歌曲" navigation uses `NavidromeRepository.getAllSongs()`, not the recently added preview list

---

## Common Mistakes

### Running Gradle verification tasks in parallel on Windows

**Don't**: Run `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, or `:app:assembleDebug` concurrently against the same checkout on Windows.

**Why**: These tasks share `app/build/` outputs. Parallel runs can lock class files and produce misleading incremental compilation errors such as `AccessDeniedException` or broad unresolved-reference cascades.

**Do**: Run Gradle verification sequentially:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:lintDebug --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

### Rewriting UTF-8 Kotlin files with PowerShell `Set-Content`

**Don't**: Use `Set-Content` or shell range rewrites for whole Kotlin source files unless you explicitly preserve UTF-8 encoding.

**Why**: Older Windows PowerShell defaults can rewrite UTF-8 files as UTF-16 LE with a BOM. Git then treats the file as binary, and non-ASCII UI copy can appear corrupted in diffs.

**Do**: Prefer `apply_patch` for source edits. If a whole-file rewrite is unavoidable, write with an explicit UTF-8 encoding and verify the file still starts with ASCII bytes such as `70 61 63 6B` for `package`.
