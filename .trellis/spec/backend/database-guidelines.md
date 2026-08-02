# Persistence Guidelines

> Local persistence patterns for the Nordic Android app.

## Overview

The app does not use Room, SQLite migrations, or an ORM. Local persistence is currently handled with AndroidX DataStore Preferences in `ConfigRepository` and JSON-encoded cache values in `NavidromeMusicCacheRepository`.

Reference files:
- `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/NavidromeMusicCacheRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/ServerConfig.kt`

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

## Readiness Helpers

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
