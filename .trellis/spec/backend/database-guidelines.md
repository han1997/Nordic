# Persistence Guidelines

> Local persistence patterns for the Nordic Android app.

## Overview

应用不使用 Room、SQLite 迁移或 ORM。服务器配置与播放偏好由 `ConfigRepository` 委托 `EncryptedConfigStore`（EncryptedSharedPreferences）持久化；旧配置 DataStore 仅用于迁移。`NavidromeMusicCacheRepository` 等缓存仓库仍使用 DataStore 中的 JSON 值。

Reference files:
- `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/NavidromeMusicCacheRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/ServerConfig.kt`

## 多来源更新（0.1.4）

当前配置已升级为来源集合和独立 active ID；旧单配置 Flow 是当前来源投影。迁移、缓存命名空间、凭证范围和失败回滚以 [多服务器、WebDAV 与设置中心](./media-sources-webdav-settings.md) 为准。新来源切换保留其他来源缓存；下文单配置旧场景仅适用于空 sourceId 的兼容路径。

## Config Storage

`ConfigRepository` is the only owner of saved server configuration. It exposes `Flow` values for:

- `navidromeConfig`
- `audiobookConfig`
- `videoConfig`

and suspend save methods:

- `saveNavidromeConfig(config)`
- `saveAudiobookConfig(config)`
- `saveVideoConfig(config)`

When adding or changing server settings, update the data class in `ServerConfig.kt`, the matching keys in `ConfigRepository` (backed by `EncryptedConfigStore` — see "Encrypted Credential Storage" below), and the corresponding config card UI.

Do not read or write stored config directly from UI screens except through `ConfigRepository`. `ConfigRepository` is backed by `EncryptedConfigStore` (EncryptedSharedPreferences); the legacy plaintext DataStore `settings` file is only read once during one-time migration.

## Cache Storage

Navidrome music library cache is JSON stored under a DataStore string preference. `NavidromeMusicCacheRepository` owns serialization, deserialization, cache key generation, and schema invalidation.

Rules:
- Keep cache model fields in `NavidromeMusicCache`.
- Build cache objects through `buildCache(...)` so `configKey` and `updatedAtMillis` are set consistently.
- Invalidate incompatible cached data by bumping `MUSIC_CACHE_SCHEMA_VERSION`.
- Bump `MUSIC_CACHE_SCHEMA_VERSION` when adding persisted DTO fields required by UI behavior, such as `NavidromeSong.created` for added-time sorting.
- Include config identity in cache keys via `NavidromeConfig.cacheKey()` so one server/user cache is not shown for another.
- Each media domain owns its own cache repository and schema version: `NavidromeMusicCacheRepository` (`MUSIC_CACHE_SCHEMA_VERSION`), `AudiobookShelfCacheRepository` (`AUDIOBOOK_CACHE_SCHEMA_VERSION`), `EmbyVideoCacheRepository` (`VIDEO_CACHE_SCHEMA_VERSION`).
- Shared cache TTL and age-label helpers live in `data/CacheTtl.kt` (`CACHE_TTL_MILLIS`, `isCacheFresh(...)`, `formatCacheAge(...)`). Do NOT duplicate these in screen or logic files.

## Scenario: Local Audiobook Bookmarks

### 1. Scope / Trigger

- Trigger: Any change to local audiobook bookmarks, listening notes, audiobook player bookmark UI, or DataStore keys used for app-owned audiobook listening state.
- This is local-only persistence. Do not infer AudiobookShelf server bookmark endpoints from this contract.

### 2. Signatures

```kotlin
data class AudiobookBookmark(
    val id: String,
    val libraryItemId: String,
    val positionSeconds: Int,
    val label: String = "",
    val createdAtMillis: Long = 0L
)

class AudiobookBookmarkRepository(private val context: Context) {
    suspend fun load(): List<AudiobookBookmark>
    suspend fun loadForItem(libraryItemId: String): List<AudiobookBookmark>
    suspend fun addBookmark(libraryItemId: String, positionSeconds: Int, label: String = ""): List<AudiobookBookmark>
    suspend fun deleteBookmark(bookmarkId: String): List<AudiobookBookmark>
}

internal fun addAudiobookBookmark(current: List<AudiobookBookmark>, bookmark: AudiobookBookmark, maxPerItem: Int): List<AudiobookBookmark>
internal fun deleteAudiobookBookmark(current: List<AudiobookBookmark>, bookmarkId: String): List<AudiobookBookmark>
internal fun bookmarksForItem(current: List<AudiobookBookmark>, libraryItemId: String): List<AudiobookBookmark>
internal fun parseAudiobookBookmarksJson(json: String?): List<AudiobookBookmark>
```

### 3. Contracts

- DataStore preference key is `audiobook_bookmarks`; value is a Gson JSON array of `AudiobookBookmark`.
- `libraryItemId` is the AudiobookShelf session/library item id and is the only grouping key for bookmarks.
- `positionSeconds` is the absolute audiobook position from `AudiobookPlaybackState.positionSeconds`, not a track-local offset.
- Bookmarks are sorted newest-first by `createdAtMillis`, with stable tie-breakers.
- Bookmarks are bounded per audiobook by `AUDIOBOOK_BOOKMARK_MAX_PER_ITEM` to avoid unbounded preference growth.
- UI must call `AudiobookPlaybackEngine.seekTo(bookmark.positionSeconds)` when selecting a bookmark.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Stored JSON is missing, blank, or malformed | Return an empty list without crashing |
| Bookmark id or `libraryItemId` is blank | Drop that row while parsing/filtering |
| Negative `positionSeconds` | Coerce to `0` |
| Blank `libraryItemId` on add | Do not create a bookmark |
| Deleting an unknown bookmark id | Leave existing bookmarks unchanged |
| More than max bookmarks for one audiobook | Keep newest entries for that audiobook and leave other audiobooks' bookmarks intact |

### 5. Good/Base/Bad Cases

- Good: User adds a bookmark while playing an audiobook, restarts the app, and the player shows that bookmark for the same `libraryItemId`.
- Base: User has no bookmarks or corrupt local bookmark JSON; playback still works and the bookmark section renders an empty state.
- Bad: Bookmark positions are saved as track-local offsets, so tapping a bookmark seeks to the wrong part of multi-track books.

### 6. Tests Required

- Unit test that adding a bookmark keeps it under the current `libraryItemId`.
- Unit test that bookmark lists are sorted newest-first.
- Unit test that malformed JSON parses as an empty list.
- Unit test that delete removes only the target bookmark.
- Unit test that per-audiobook bounding keeps newest entries and does not delete another audiobook's bookmarks.
- Compile/lint checks for player callback wiring.

### 7. Wrong vs Correct

#### Wrong

```kotlin
// Wrong: stores a track-local Media3 offset and cannot survive multi-track books.
repository.addBookmark(session.libraryItemId, player.currentPosition.toInt() / 1000)
```

#### Correct

```kotlin
// Correct: stores the absolute audiobook position exposed by the playback state.
repository.addBookmark(session.libraryItemId, audiobookPlaybackState.positionSeconds)
```

## Scenario: Navidrome Downloaded Song Metadata Sidecars

### 1. Scope / Trigger

- Trigger: Any change to `MusicDownloadManager`, downloaded-song restore behavior, downloaded-song deletion, or the Music home downloaded section.
- This is a local persistence boundary: media files are stored in the app external music directory, while song metadata is stored as a JSON sidecar next to the media file.

### 2. Signatures

```kotlin
class MusicDownloadManager(private val context: Context) {
    fun downloadSong(song: NavidromeSong, config: NavidromeConfig)
    fun restoreDownloadState()
    fun updateSongMetadata(songs: List<NavidromeSong>)
    fun deleteDownload(songId: String)
    fun getDownloadedSongs(): List<NavidromeSong>
    fun getLocalFilePath(songId: String): String?
}

internal fun musicDownloadMetadataFileName(songId: String): String
internal fun isDownloadedMusicFile(fileName: String): Boolean
internal fun saveDownloadedSongMetadata(file: File, song: NavidromeSong)
internal fun loadDownloadedSongMetadata(file: File): NavidromeSong?
```

### 3. Contracts

- Downloaded audio file name remains `<songId>.<extension>`, where the extension is derived from the response content type.
- Downloaded metadata sidecar name is `<songId>.metadata.json`.
- A successful `downloadSong(...)` must write the audio file and then write the metadata sidecar for the same `NavidromeSong`.
- `restoreDownloadState()` must ignore `*.tmp` and `*.metadata.json` files when scanning for downloaded media.
- If a sidecar exists, `restoreDownloadState()` restores `DownloadStateEntry.song` from the sidecar so the Downloaded section can render before a network/library refresh.
- If an older download has no sidecar, restore it as downloaded with `song = null`; `updateSongMetadata(...)` may later repair the entry and write the sidecar.
- `deleteDownload(songId)` must remove both matching media files and the metadata sidecar.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Metadata sidecar is missing | Restore file state as downloaded with `song = null` |
| Metadata sidecar is malformed | Ignore metadata and keep restore stable |
| Download fails before final rename | Delete temp file and leave state as not downloaded |
| Delete is requested while item is downloading | No-op to avoid racing an in-flight write |
| Audio file exists but sidecar is absent | Keep backwards compatibility with pre-sidecar downloads |

### 5. Good/Base/Bad Cases

- Good: User downloads a song, restarts offline, and the Downloaded section can render the song from the sidecar metadata.
- Base: User has old downloaded files without sidecars; files still restore as downloaded and metadata is repaired after the library loads.
- Bad: Restore treats `<songId>.metadata.json` as an audio file, creating a fake downloaded item id like `<songId>.metadata`.

### 6. Tests Required

- Unit test `musicDownloadMetadataFileName_usesStableSidecarName`.
- Unit test `isDownloadedMusicFile_excludesTempAndMetadataSidecarFiles`.
- Unit test that `saveDownloadedSongMetadata` and `loadDownloadedSongMetadata` round-trip a `NavidromeSong`.
- Unit test that missing/malformed metadata returns `null` without throwing.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val existingFiles = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
```

This includes metadata sidecars as if they were playable media files.

#### Correct

```kotlin
val existingFiles = dir.listFiles()?.filter { file -> isDownloadedMusicFile(file.name) }
```

This preserves backwards-compatible audio restore while excluding temp and sidecar files.

## Scenario: Navidrome Config-Scoped Music Refresh

### 1. Scope / Trigger

- Trigger: Any change to `MusicScreenV2.refreshMusicData(...)`, `loadNavidromeMusicRefresh(...)`, `NavidromeMusicCacheRepository`, or Navidrome saved-config flow.
- This is a config/cache boundary: the repository used to fetch remote data and the config used to build/save the cache must be the same logical config.

### 2. Signatures

- `suspend fun loadNavidromeMusicRefresh(targetConfig: NavidromeConfig, savedConfig: NavidromeConfig? = null, savedRepository: NavidromeMusicDataSource? = null, repositoryFactory: (NavidromeConfig) -> NavidromeMusicDataSource = { NavidromeRepository(it) }): NavidromeMusicRefreshData?`
- `interface NavidromeMusicDataSource`
  - `suspend fun getRecentAlbums(): List<NavidromeAlbum>`
  - `suspend fun getRecentlyAddedSongs(albums: List<NavidromeAlbum>): List<NavidromeSong>`
  - `suspend fun getAllSongs(): List<NavidromeSong>`
  - `suspend fun getArtists(): List<NavidromeArtist>`
- `fun NavidromeConfig.cacheKey(): String`

### 3. Contracts

- If `targetConfig.isReadyForMusicSync()` is false, refresh returns `null`/`false` and must not fetch or save cache data.
- If `targetConfig == savedConfig`, refresh may reuse the remembered `savedRepository`.
- If `targetConfig != savedConfig`, refresh must call `repositoryFactory(targetConfig)` and must not use the remembered repository for the old saved config.
- Cache objects must be built and saved with the same `targetConfig` used to create/select the repository.
- Saving a new config from the UI must refresh with that new config even before the DataStore flow emits it back as `savedConfig`.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Target config lacks URL, username, or password | Do not construct a repository; leave refresh as not performed |
| Target config differs from saved config | Build a repository from the target config |
| Target config matches saved config and remembered repository exists | Reuse remembered repository |
| Repository fetch fails | Surface refresh error and keep existing cached UI content when available |
| Cache key does not match current config | `NavidromeMusicCacheRepository.load(...)` returns no cache |

### 5. Good/Base/Bad Cases

- Good: User edits Navidrome credentials, taps save, fresh data is fetched with the edited credentials and cached under the edited config key.
- Base: User taps refresh without changing config; the remembered repository is reused.
- Bad: User saves new credentials, but refresh reads from the old saved repository and saves old-server data under the new config key.

### 6. Tests Required

- Unit test `loadNavidromeMusicRefresh_usesTargetConfigInsteadOfSavedRepositoryForNewConfig` must assert the factory receives `targetConfig` and the saved repository is unused when configs differ.
- Unit test `loadNavidromeMusicRefresh_reusesSavedRepositoryWhenTargetConfigMatches` must assert the factory is not called for the same saved config.
- Readiness tests must assert Navidrome refresh requires URL, username, and password.

### 7. Wrong vs Correct

#### Wrong

```kotlin
val repo = navidromeRepository ?: return false
val freshAlbums = repo.getRecentAlbums()
cacheRepository.save(targetConfig, freshCache)
```

#### Correct

```kotlin
val freshData = loadNavidromeMusicRefresh(
    targetConfig = targetConfig,
    savedConfig = savedConfig,
    savedRepository = navidromeRepository
) ?: return false
val freshCache = cacheRepository.buildCache(config = targetConfig, ...)
cacheRepository.save(targetConfig, freshCache)
```

## Scenario: Cross-Domain Media Cache Refresh

### 1. Scope / Trigger

- Trigger: Any change to a media domain cache repository (`NavidromeMusicCacheRepository` / `AudiobookShelfCacheRepository` / `EmbyVideoCacheRepository`), the shared `CacheTtl.kt` helpers, or a screen's `LaunchedEffect(savedConfig)` launch refresh / manual refresh / detail-open / config-switch path.
- Scope: cache-then-refresh launch flow, browse TTL, manual-refresh TTL bypass, detail cache-then-refresh, config-switch cache cleanup, and cross-domain consistency. Applies to Music, Audiobook, and Video.

### 2. Signatures

- Shared helpers (`data/CacheTtl.kt`):
  - `val CACHE_TTL_MILLIS: Long = 30 * 60 * 1000L`
  - `fun isCacheFresh(updatedAtMillis: Long?): Boolean`
  - `fun formatCacheAge(updatedAtMillis: Long?): String`
- Per-domain cache key:
  - `fun NavidromeConfig.cacheKey(): String`
  - `fun AudiobookShelfConfig.cacheKey(): String`
  - `fun VideoServerConfig.cacheKey(): String`
- Per-domain cache repository (`data/`):
  - `suspend fun load(config): <DomainCache>?`
  - `suspend fun save(config, cache)`
  - `fun buildCache(config, ...): <DomainCache>`
  - `suspend fun clear(config)` — removes the stored JSON only when the stored `configKey` matches `config.cacheKey()`

### 3. Contracts

- Each domain cache is JSON under a DataStore string preference key, scoped by a `cacheKey()` that embeds `url|user|schemaVersion`. Emby's key additionally includes the apiKey identity (or username for password-login) so apiKey-only configs at the same `url|user` do not collide.
- Launch flow (`LaunchedEffect(savedConfig)`): apply cached data first, then gate the background refresh on `isCacheFresh(cacheUpdatedAtMillis)`. If fresh, skip refresh. If stale or null, run the refresh and write back to cache.
- Manual refresh (the ↻ header action): MUST bypass the TTL gate. The `onClick = { scope.launch { refreshXxx() } }` path calls the refresh function directly, NOT through the `isCacheFresh` check.
- On refresh failure with cached content present: keep cached content and surface a hint such as `"正在显示上次缓存：..."`. On refresh failure with no cached content: surface a connection error such as `"连接失败: ..."`. The error branch must check `hasCachedContent` and never collapse to an empty error state when a cache exists.
- Detail cache-then-refresh (Music album/artist/playlist detail, Audiobook item detail): on open, load cached detail first (show it), then fetch fresh in the background, save to cache, and update UI. Detail caches have NO TTL (always refresh on open). Failure with cached detail: keep cached + hint; failure without cache: show error.
- Browse-refresh save must MERGE detail-cache maps, not wipe them. When a browse refresh writes a new cache object, existing detail-cache entries (album songs / artist albums / playlist songs / audiobook item detail by id) must be preserved so the user does not lose cached detail state on every browse refresh. Browse list fields (`albums`, `songs`, `libraries`, `items`, `videos`) are authoritative and overwritten; detail maps are merged.
- Video detail has NO separate detail cache. `relatedEpisodes` is derived from the cached `videos` list at render time, so no extra request or detail-cache map is needed.
- Config-switch cleanup: track the previous saved config. When the new config's `cacheKey()` differs from the previous, call `cacheRepository.clear(previousConfig)`. Never call `clear(newConfig)` — that would wipe the cache the launch flow just applied. `clear()` only removes the stored JSON when the stored `configKey` matches the argument.
- Bump the domain's `*_CACHE_SCHEMA_VERSION` whenever persisted cache field semantics change (e.g. adding the Music detail-cache maps bumped Music v3→v4). Old caches are auto-invalidated because the `schemaVersion` in the key changes.
- Per-library item caches (Video v3 / Audiobook v2): each domain cache additionally keeps `itemsByLibrary: Map<libraryId, List<item>>` + `libraryFetchedAt: Map<libraryId, Long>` so switching media libraries renders instantly from cache (stale-while-revalidate) instead of re-paginating the whole library. Writes go through `saveLibraryItems(config, libraryId, items)`, which stamps the fetch time and evicts the least-recently-fetched libraries beyond `LIBRARY_CACHE_MAX_ENTRIES` (4). Browse list fields (`videos` / `items`) stay authoritative for the currently selected library and are refreshed alongside the per-library map.
- Library-switch flow (chip onSelect): if the target library has cached rows, render them immediately (no spinner), keep the existing `xxxLibraryRequestVersion` / config-version guards, then fetch fresh in the background; a failed background fetch keeps the cached rows visible and only surfaces an error when nothing is rendered. If the target library has no cached rows, keep the previous loading full-fetch behavior.
- ON_RESUME catalog refresh (Video only) is TTL-gated per selected library: within `RESUME_CATALOG_TTL_MILLIS` (5 min, `data/CacheTtl.kt`) of the selected library's last fetch, re-entry only re-pulls the cheap Resume list (`refreshResumeItems`); beyond it, the full `refreshVideo` runs. Manual ↻ refresh always bypasses this gate. Use the shared `isCacheFresh(updatedAtMillis, ttlMillis)` overload for per-domain TTL windows; do not inline time math in screens.
- Existing `xxxConfigStateVersion` guards must remain in place so stale async writes after a config change cannot repopulate state. Cache apply/refresh must respect the same request-version checks.
- Cache repositories are constructed once per screen via `remember { XxxCacheRepository(context) }`. Do not construct them per refresh call.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Launch with cache fresh (< 30 min) | Apply cached data; skip background refresh |
| Launch with cache stale (>= 30 min) or null | Apply cached data if any; run background refresh; write back on success |
| Manual refresh button tapped | Bypass TTL; force network refresh regardless of cache age |
| Refresh fails and cached browse content exists | Keep cached content; show `"正在显示上次缓存：…"` hint |
| Refresh fails and no cached content exists | Show `"连接失败: …"` error; do not show empty content |
| Detail open with cached detail | Show cached detail; fetch fresh in background; update UI on success |
| Detail fetch fails with cached detail present | Keep cached detail; show hint |
| Detail fetch fails with no cached detail | Show contextual detail-load error |
| Browse refresh saves a new cache | Overwrite browse list fields; preserve existing detail-cache maps (merge) |
| Empty server response on browse refresh | Overwrite browse fields with empty lists; do NOT keep stale browse content |
| Config switch and new cacheKey differs from previous | Call `clear(previousConfig)`; do NOT clear `newConfig` |
| Config switch and cacheKey unchanged | Do not call clear (same account re-emission) |
| `clear(config)` called but stored configKey does not match | No-op; do not remove another config's cache |
| Cache schema version bumped | Old caches under the previous version are ignored (key mismatch) |
| Library switch with cached rows for the target library | Render cached rows instantly (no spinner); background refresh updates in place and stamps `libraryFetchedAt` |
| Library switch without cached rows | Loading full fetch as before |
| Background library fetch fails with cached rows visible | Keep cached rows; no error card |
| Background library fetch fails with nothing rendered | Show the contextual load error |
| ON_RESUME within `RESUME_CATALOG_TTL_MILLIS` of the selected library's fetch | Only the Resume list re-pulls; no full catalog re-pagination |
| ON_RESUME beyond the TTL or no selected library | Full `refreshVideo` runs (TTL-gated launch path unchanged) |
| Old async refresh writes after config change | Ignored by `xxxConfigStateVersion` guard; cache not repopulated |

### 5. Good/Base/Bad Cases

- Good: User opens the app within 30 min of last sync; Music home shows cached content instantly and skips the network. Tapping ↻ forces a fresh fetch.
- Good: User is offline with a 2-hour-old cache; Music/Audiobook/Video show stale cache with an offline hint instead of an empty error state.
- Good: User switches Navidrome account; the previous account's cache is cleared from DataStore and does not accumulate across multiple switches.
- Good: User opens an album detail, sees cached songs instantly, and the songs refresh in the background; a later browse refresh preserves the cached album-detail map.
- Good: User opens a Video Series; episode rows derive from the cached `videos` list without an extra network request or a separate video detail cache.
- Base: First launch with no cache; refresh runs immediately and caches the result.
- Bad: Launch refresh always hits the network even when the cache is 1 minute old (TTL gate missing or bypassed on the launch path).
- Bad: Manual ↻ refresh is accidentally TTL-gated, so the user cannot force a fresh fetch when the cache is "fresh".
- Bad: Browse refresh wipes the detail-cache maps, so reopening an album detail after a browse refresh shows a loading state instead of the cached songs.
- Bad: `clear(newConfig)` is called on config switch, wiping the cache that was just applied for the new account.
- Bad: Emby `cacheKey` uses only `url|user` and two apiKey-only configs at the same URL collide, showing one user's video library under another.

### 6. Tests Required

- Per-domain cache repository unit tests: load/save round-trip, config-scoped isolation (different `cacheKey` does not load another config's cache), `clear(config)` removes only matching configKey, malformed JSON returns null without crashing.
- `CacheTtlTest`: `isCacheFresh` boundary (just under TTL → true, exactly TTL → false, null → false), `formatCacheAge` formatting.
- `CacheKeyTest`: each domain's `cacheKey()` embeds `url|user|schemaVersion`; Emby key distinguishes apiKey vs username identity at the same URL.
- Detail-cache merge test: save a browse cache with detail entries, then save a new browse cache with empty detail maps; assert the previous detail entries are preserved.
- Browse-refresh-preserves-detail test: after a browse refresh save, existing detail entries remain loadable by id.

### 7. Wrong vs Correct

#### Wrong

```kotlin
// Launch path always refreshes, ignoring TTL.
LaunchedEffect(savedConfig) {
    applyCachedMusicData(savedConfig, requestVersion)
    refreshMusicData(savedConfig, requestVersion)  // no TTL gate
}
```

```kotlin
// Manual refresh accidentally goes through the TTL gate.
onClick = { scope.launch {
    if (isCacheFresh(cacheUpdatedAtMillis)) return@launch  // wrong: blocks user-requested refresh
    refreshMusicData()
} }
```

```kotlin
// Browse refresh wipes detail caches by replacing the whole object.
cacheRepository.save(config, freshBrowseCache)  // freshBrowseCache has empty detail maps → detail cache lost
```

```kotlin
// clear() removes regardless of which config the stored cache belongs to.
suspend fun clear(config: VideoServerConfig) {
    context.dataStore.edit { it.remove(videoCacheKey) }  // unconditional wipe
}
```

#### Correct

```kotlin
// Launch path: TTL-gated; manual path: direct.
LaunchedEffect(savedConfig) {
    applyCachedMusicData(savedConfig, requestVersion)
    if (!isCacheFresh(cacheUpdatedAtMillis)) {
        refreshMusicData(savedConfig, requestVersion)
    }
}
// ↻ button:
onClick = { scope.launch { refreshMusicData() } }  // bypasses TTL
```

```kotlin
// Browse refresh merges detail maps (Music example).
suspend fun save(config, incoming: NavidromeMusicCache) {
    val merged = loadRaw(config)?.copy(
        albums = incoming.albums,
        songs = incoming.songs,
        recentlyAddedSongs = incoming.recentlyAddedSongs,
        artists = incoming.artists,
        updatedAtMillis = incoming.updatedAtMillis
    ) ?: incoming
    context.dataStore.edit { it[musicCacheKey] = gson.toJson(merged.copy(configKey = config.cacheKey())) }
}
```

```kotlin
// clear() only removes when the stored configKey matches.
suspend fun clear(config: VideoServerConfig) {
    val key = config.cacheKey()
    context.dataStore.edit { prefs ->
        val json = prefs[videoCacheKey] ?: return@edit
        val stored = runCatching { gson.fromJson(json, EmbyVideoCache::class.java) }.getOrNull()
        if (stored?.configKey == key) prefs.remove(videoCacheKey)
    }
}
```

## Readiness Helpers

## Scenario: Browse Selection and Detail Request Isolation

### 1. Scope / Trigger

- Trigger: Any change to Music, Audiobook, or Video browse refreshes, media-library selection, selected detail reconciliation, or cache-backed error presentation.
- Scope: state ownership between the current config/library and asynchronous list/detail requests.

### 2. Signatures

- Per-config request guards: `musicConfigStateVersion`, `audiobookConfigStateVersion`, `videoConfigStateVersion`.
- Audiobook detail guard: `audiobookDetailRequestVersion`.
- Selection helpers such as `resolveAudiobookSelectedItemAfterLibraryRefresh(...)`, `resolveVideoSelectionAfterCatalogRefresh(...)`, and `shouldShow...DetailInvalidationNotice(...)`.

### 3. Contracts

- A saved config change is a hard state boundary: clear old content, selected detail, filters, errors, and loading state before applying the new config cache.
- A media-library selection change is also a data boundary: clear the previous library's items and detail before requesting the new library.
- Every asynchronous result, error, and loading-finally write must validate both the current config/library request identity and, where applicable, the current detail request identity.
- Same-config refresh keeps cached browse content while loading. A failure with real media entries keeps that content and is exposed in the page subtitle; a failure with no media entries uses the shared actionable error card.
- If refresh reconciliation cannot find the selected detail id, clear the selected object, return to its owning list page, and show one lightweight explanation. Never render or play the stale detail object.
- Successful library selection refreshes must persist the selected library's browse cache under the current config key so a later config emission or process restart cannot restore a different library selection.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Old config request finishes after config change | Ignore result, error, and loading writes |
| Old library request finishes after library change | Ignore result, error, and loading writes |
| Old detail request finishes after another detail opens | Ignore result, error, and loading writes |
| Same-config refresh fails with media entries | Keep entries and show cache-failure subtitle |
| Refresh fails with only library metadata and no media entries | Show independent actionable error card |
| Selected detail id disappears after refresh | Clear detail, return to list, show one explanation |
| Library selection succeeds | Save refreshed browse cache with current selected library id |

### 5. Good/Base/Bad Cases

- Good: User switches AudiobookShelf libraries quickly; only the latest library response can update the list.
- Good: User opens two audiobook details quickly; the older detail response cannot replace the newer detail.
- Good: An offline refresh leaves cached media visible and communicates the failure in the header.
- Base: A first load with no cached media shows the shared error card when the request fails.
- Bad: Guard only config version but not detail request identity; a slow first detail response overwrites the second detail.
- Bad: Treating a non-empty library list as cached media and hiding the actionable error card when no books/videos are available.
- Bad: Updating the selected library only in Compose state and never persisting it to the current config cache.

### 6. Tests Required

- Test stale config/library/detail responses cannot mutate current state.
- Test selected detail retained when its id remains and cleared with list fallback when it disappears.
- Test cached-content error presentation distinguishes real media entries from library metadata only.
- Test successful library selection persists the selected library id in the current cache.

### 7. Wrong vs Correct

```kotlin
// Wrong: an old detail response can overwrite the currently opened item.
selectedItem = repository.getLibraryItem(item.id)
```

```kotlin
// Correct: capture request identity and check it before every state write.
val requestId = ++detailRequestVersion
val detail = repository.getLibraryItem(item.id)
if (requestId == detailRequestVersion && configVersion == currentConfigVersion) {
    selectedItem = detail
}
```

---

Config readiness should be centralized in helper functions, not reimplemented in screens:

- `NavidromeConfig.isReadyForMusicSync()`
- `AudiobookShelfConfig.isReadyForAudiobookSync()`
- `VideoServerConfig.isReadyForVideoSync()`

These helpers define when repositories may be constructed and when screens should show setup/empty states.

## Network Data Flow

Remote data is not persisted as relational entities. Repositories fetch remote payloads, map DTOs to domain models, and return lists or state objects to Compose screens.

Reference tests:
- `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`
- `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`

Use `MockWebServer` tests when adding repository behavior that depends on request paths, query parameters, auth headers, response mapping, or error mapping.

## Common Mistakes

- Do not add a Room database for simple server config; the project convention is DataStore Preferences for non-secret cache and EncryptedSharedPreferences for credentials.
- Do not store passwords or API keys in logs.
- Do not change cache field semantics without bumping the cache schema version.
- Do not duplicate readiness checks such as `serverUrl.isNotBlank() && username.isNotBlank()` inside composables.

## Scenario: Encrypted Credential Storage

### 1. Scope / Trigger
- Trigger: Any change to `ConfigRepository`, `EncryptedConfigStore`, server credential persistence, or config migration.
- All server credentials (Navidrome/AudiobookShelf/Emby passwords + Emby API key) must be encrypted at rest. The app sets `android:allowBackup="false"`, so backup leakage is handled; this contract governs on-device at-rest encryption.

### 2. Signatures
- `val Context.dataStore: DataStore<Preferences>` (legacy plaintext store, read only during migration)
- `class EncryptedConfigStore(context: Context)` backed by `EncryptedSharedPreferences` (file `secret_prefs`, `MasterKey` AES256_GCM, AES256_SIV key scheme, AES256_GCM value scheme)
- `EncryptedConfigStore` exposes the same `Flow`/`suspend` surface `ConfigRepository` previously had over `context.dataStore`: `navidromeConfig`/`audiobookConfig`/`lastAudiobookItemId`/`videoConfig` + 4 `suspend fun saveXxx`
- `internal object EncryptedConfigKeys` — 12 string keys: 4 high-sensitivity (`navidrome_pass`, `audiobook_pass`, `video_pass`, `video_api_key`), 3 PII (`*_user`), 5 config (`*_url`, `audiobook_last_item_id`, `video_type`)
- `internal fun runEncryptedConfigMigration(prefs, snapshot, deleteLegacy)` — pure migration step for deterministic testing
- `class ConfigRepository(context)` — public API unchanged; internal backing swapped to `EncryptedConfigStore`

### 3. Contracts
- `ConfigRepository` public `Flow`/`suspend` signatures must not change; callers (MainActivity, repositories) need zero edits when the backing store changes.
- `EncryptedConfigStore` exposes `Flow` via `OnSharedPreferenceChangeListener` → `callbackFlow` → `distinctUntilChanged` (cold flow that registers/unregisters the listener).
- `suspend save*` functions use `commit()` (synchronous, guarantees durability) wrapped in `withContext(Dispatchers.IO)`, not `apply()` (fire-and-forget can lose durability before the legacy file is deleted).
- One-time migration: ESP `migrated` boolean guard → read legacy `context.dataStore.data.first()` on a background `Dispatchers.IO` coroutine (NOT on the main thread; `runBlocking` is only allowed inside the IO scope) → `commit()` all 12 keys → set `migrated=true` → delete `context.filesDir.resolve("datastore/settings.preferences_pb")`.
- Migration is idempotent: the `migrated` flag prevents re-running after interruption; a partially-written legacy snapshot is safe because `commit()` is atomic per call and the flag is set in the same `commit()` as the keys.
- `android:allowBackup="false"` must stay set so the encrypted store is not backed up.
- Never log credentials, API keys, or the encrypted file path.

### 4. Validation & Error Matrix
| Condition | Behavior |
|---|---|
| First launch (no legacy file) | Migration no-op; `migrated=true` set on first `commit()`; ESP stores new config directly |
| Upgrade from plaintext DataStore | Migration copies all 12 keys into ESP via `commit()`, sets `migrated=true`, deletes legacy `settings.preferences_pb` |
| Migration interrupted before `commit()` | `migrated` stays `false`; next launch re-reads legacy DataStore and re-runs; ESP has no partial state because `commit()` is atomic |
| Migration interrupted after `commit()` but before file delete | `migrated=true` already set; next launch skips migration; legacy file is harmless (no longer read) and may be deleted on a later cleanup |
| Keystore unavailable / master key rotate fails | `EncryptedSharedPreferences.create` throws `GeneralSecurityException`/`IOException`; `EncryptedConfigStore` fails fast — do not silently fall back to plaintext |
| `ConfigRepository` public API change | Rejected at review; callers must need zero edits |

### 5. Good/Base/Bad Cases
- Good: User upgrades from a plaintext build; saved Navidrome/ABS/Emby config reappears without re-entry; the legacy `settings.preferences_pb` file is gone after first launch.
- Base: Fresh install; ESP is empty until the user saves config; no migration runs.
- Bad: `save*` uses `apply()` and the user kills the app before the async write flushes; the config is lost.
- Bad: Migration runs `runBlocking` on the main thread to read the legacy DataStore; the app ANRs on cold start.
- Bad: A new credential key is added to `EncryptedConfigKeys.ALL` but the legacy snapshot reader does not include it; upgrades lose that field.

### 6. Tests Required
- `EncryptedConfigStoreTest`: Flow emits initial values; `save*` triggers a new emission; `distinctUntilChanged` drops no-op writes; `lastAudiobookItemId` blank coalesces to `null`; `video_type` blank falls back to `EMBY`.
- Migration tests via `runEncryptedConfigMigration`: copies all 12 keys, skips `null` values, sets `migrated=true`, deletes the legacy file, and is idempotent (second run is a no-op).
- `ConfigRepository` compile-check: public `Flow`/`suspend` signatures unchanged.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// apply() is fire-and-forget; durability before deleting the legacy file is not guaranteed.
prefs.edit().putString(NAVIDROME_PASS, config.password).apply()
deleteLegacyDataStoreFile()
```

#### Correct
```kotlin
withContext(Dispatchers.IO) {
    prefs.edit().apply {
        putString(EncryptedConfigKeys.NAVIDROME_PASS, config.password)
    }.commit()  // synchronous; guarantees durability before any cleanup
}
```

#### Wrong
```kotlin
// Migration blocks the main thread reading the legacy DataStore.
fun init() = runBlocking { context.dataStore.data.first() }
```

#### Correct
```kotlin
// Migration runs on a background IO scope; the UI is never blocked.
private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
init { migrationScope.launch { runMigrationIfNeeded() } }
```

## Scenario: 播放偏好与配置订阅

### 1. 范围 / 触发条件

新增视频/全局偏好，或修改 `EncryptedConfigStore` 的实例创建、`configFlow` 初始快照、监听和清理逻辑时适用。此共享路径也服务模块显示与三类服务器配置，不能以“仅一个开关”跳过跨实例/跨域回归。

### 2. 关键签名

- `EncryptedConfigKeys.VIDEO_PIP_ENABLED = "video_pip_enabled"`，存储为字符串 `"true"` / `"false"`。
- `EncryptedConfigStore.videoPipEnabled: Flow<Boolean>`、`suspend saveVideoPipEnabled(enabled: Boolean)`；`ConfigRepository` 透传。
- `VideoPlaybackViewModel.pipEnabled: StateFlow<Boolean>`（初始 true）、`setPipEnabled(Boolean)`；UI 不直接写 SharedPreferences。
- `private fun <T> configFlow(watchedKeys: Set<String>, read: (SharedPreferences) -> T): Flow<T>`。
- `EncryptedPreferencesInstance.getOrCreate(create: () -> SharedPreferences): SharedPreferences` 是加密包装器的线程安全惰性持有者；默认 `createEncryptedSharedPreferences(context)` 使用同一个进程级持有者和 `applicationContext`。

### 3. 可执行合同

- PiP 键缺失或为非法字符串时默认 true；持久化 false 在新 store / 下次启动后仍是 false。保存使用 `Dispatchers.IO` 内的 `commit()`，不引入另一份内存-only 偏好来源。
- 同一进程访问 `secret_prefs` 的所有默认 store 必须共享一个成功初始化的 `EncryptedSharedPreferences` **包装器对象**。AndroidX 1.1.0 的 listener 列表属于包装器，同文件的新包装器不会通知旧包装器的订阅者；仅文件名相同不构成响应式共享。
- 包装器并发首访只初始化一次；Keystore/工厂异常原样传播且不缓存失败，下次可以重试，不能降级明文或缓存空配置。
- 设置页与根界面可以使用不同 `ConfigRepository`，但模块开关成功保存后，已有 `preferences` 订阅应立即收到新状态，驱动 `LocalAppPreferences`、媒体入口、设置分类与搜索；不得用重启 Activity、手动 reload 或开关局部假状态代替通知链路。
- 新播放偏好不属于旧版配置 DataStore 的迁移键，不为它扩展 `EncryptedConfigKeys.ALL` 的历史迁移快照。
- 配置订阅顺序必须为：**注册 listener → 读取并发送当前值 → 等待关闭 → finally 注销 listener**。先读后监听会丢掉两步之间的并发保存，使 UI 保持旧配置或 `first { changed }` 永久等待。
- 回调仍过滤 `watchedKeys`，`changedKey == null` 表示整体变化；保留 `distinctUntilChanged`，无变化保存不多发状态。
- 初始读取失败或订阅取消也必须注销，不能只在成功执行到 `awaitClose` 后才安排清理。

### 4. 验证与错误矩阵

| 条件 | 结果 |
|---|---|
| PiP 键缺失 / 非法字符串 | true |
| 保存 false 后重建 store | false |
| 页面 A 已订阅，页面 B 保存模块/播放偏好或来源 | A 无需重建或重订阅即可获得新状态 |
| 并发创建多个默认 store | 同一个包装器和监听注册表 |
| 首次 Keystore 创建失败后重试 | 首次异常透传，重试可成功且之后复用 |
| 在 listener 注册过程中发生保存 | 注册后的初始快照可见新值，不漏掉最后一次保存 |
| 保存无变化值 | distinctUntilChanged 丢弃重复状态 |
| 取消订阅 / 初始 read 抛错 | finally 注销 listener |
| 测试长时间停在 testDebugUnitTest | 先抓指定 worker 的线程栈，不能直接假定 Gradle daemon 坏了 |

### 5. 正常 / 基础 / 错误案例

- 正常：关闭 PiP → 快速离开播放器 → 下次启动仍保持关闭。
- 正常：模块页连续隐藏/显示 → 原有根界面 Flow 每次获得新值；销毁一个订阅者不影响其他页面。
- 错误：每个 store 都调用一次 `EncryptedSharedPreferences.create`，误以为同文件代表共享监听；持久化成功但界面直到重启才变化。
- 基础：没有偏好键的旧安装直接默认开启，不新增一次历史迁移。
- 错误：初始值先发出，再注册 listener；此时 IO 保存已经完成且没有后续写入，订阅永远收不到更新。

### 6. 必需测试

- `EncryptedConfigStoreTest.videoPipEnabled_defaultsTrueWhenUnsetAndRoundTrips`、`restoresDisabledPreferenceInNewStore`、`defaultsTrueForMalformedValue`。
- `videoPipEnabled_observesWriteDuringListenerRegistration` 使用委托 SharedPreferences，在真正注册 listener 前写入 false；原先先读后监听实现应确定性超时，正确实现应立即获得 false，不靠 sleep 调度概率复现。
- 配置流测试使用有限等待（该类 JUnit Timeout 5 秒，竞态用例 `withTimeout(1_000)`），避免一次通知丢失阻塞整个测试进程。
- `EncryptedConfigStoreReactivityTest` 用共享数据 Map、独立 listener 列表模拟同文件的不同包装器；断言持续订阅的多个 store 经七种模块组合切换后立即更新，稳定标签/回退、设置分类/搜索、恢复默认、全关保护、播放投影与来源更新一致。不能仅以新 store 的 `first()` 证明实时通知。
- `EncryptedPreferencesInstanceTest` 断言八线程首次获取只创建一个包装器、初始化异常透传/可重试、成功后不再次调用工厂。
- 共享 helper 改动后运行所有配置/迁移测试与完整 app 单测，不仅测试 PiP 单一字段。

### 7. 错误与正确示例

```kotlin
// 错误：保存可能发生在这两行之间。
emitCurrent()
prefs.registerOnSharedPreferenceChangeListener(listener)

// 正确：先建立监听，读取或等待异常时也能清理。
prefs.registerOnSharedPreferenceChangeListener(listener)
try {
    emitCurrent()
    awaitClose()
} finally {
    prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
```
