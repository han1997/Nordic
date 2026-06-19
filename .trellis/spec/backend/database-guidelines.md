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
