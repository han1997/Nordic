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

When adding or changing server settings, update the data class in `ServerConfig.kt`, the matching DataStore keys in `ConfigRepository`, and the corresponding config card UI.

Do not read or write DataStore preferences directly from UI screens except through `ConfigRepository`.

## Cache Storage

Navidrome music library cache is JSON stored under a DataStore string preference. `NavidromeMusicCacheRepository` owns serialization, deserialization, cache key generation, and schema invalidation.

Rules:
- Keep cache model fields in `NavidromeMusicCache`.
- Build cache objects through `buildCache(...)` so `configKey` and `updatedAtMillis` are set consistently.
- Invalidate incompatible cached data by bumping `MUSIC_CACHE_SCHEMA_VERSION`.
- Include config identity in cache keys via `NavidromeConfig.cacheKey()` so one server/user cache is not shown for another.

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

- Do not add a Room database for simple server config; the project convention is DataStore Preferences.
- Do not store passwords or API keys in logs.
- Do not change cache field semantics without bumping the cache schema version.
- Do not duplicate readiness checks such as `serverUrl.isNotBlank() && username.isNotBlank()` inside composables.
