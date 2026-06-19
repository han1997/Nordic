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

### Config readiness checks centralized

`NavidromeConfig.isReadyForMusicSync()` is defined once in `ServerConfig.kt` and imported where needed. Do not inline `serverUrl.isNotBlank() && username.isNotBlank()` or create duplicate extension functions.

### NavidromeRepository instance reuse

When a composable or screen needs `NavidromeRepository`, use `remember { NavidromeRepository(config) }` keyed on config changes, not `NavidromeRepository(config)` inline in each suspend call. Each construction creates a new Retrofit + OkHttpClient.

```kotlin
val navidromeRepository = remember(savedConfig) {
    if (savedConfig.isReadyForMusicSync()) NavidromeRepository(savedConfig) else null
}
```

### Navidrome all-song navigation data

The Music tab's "歌曲" navigation must use all songs, not the recently added preview list.

**Scope / Trigger**: Any change to the Music screen song tab, `NavidromeRepository` song loading, or `NavidromeMusicCache` song fields.

**Signatures**:
- `NavidromeApi.getAlbumList2(..., type: String, size: Int, offset: Int)` must expose `offset` so repositories can page through album lists.
- `NavidromeRepository.getAllSongs(): List<NavidromeSong>` is the source for the song navigation page.
- `NavidromeRepository.getRecentlyAddedSongs(albums, limit)` is only for recently added preview surfaces.

**Contract**:
- All songs are loaded by paging `getAlbumList2(type = "alphabeticalByName", size = ALBUM_PAGE_SIZE, offset = n)` until a short page or empty page, then expanding each album with `getAlbum`.
- Home "最近添加" may show `recentlyAddedSongs`, but selecting navigation "歌曲" must render cached/refreshed `songs` from `getAllSongs()`.
- When changing `NavidromeMusicCache` field semantics, bump `MUSIC_CACHE_SCHEMA_VERSION` so stale cached data is not displayed under the new meaning.

**Validation & Error Matrix**:
- Subsonic/HTTP error while paging albums -> preserve `NavidromeApiException`.
- Unknown failure while loading all songs -> wrap as `"获取全部歌曲失败: ..."` for UI context.
- Empty album list -> return an empty song list, do not fall back to random songs.

**Good/Base/Bad Cases**:
- Good: Song tab displays every track returned by all paged albums.
- Base: Recently added carousel displays only the limited recent-song set.
- Bad: Song tab uses `getRecentlyAddedSongs(...)` or `getRandomSongs(...)`.

**Tests Required**:
- Repository unit test with `MockWebServer` asserting `getAllSongs()` requests `type=alphabeticalByName`, `size=100`, `offset=0`, then expands album tracks via `getAlbum`.

**Wrong vs Correct**:
```kotlin
// Wrong: binds the Songs navigation page to a recent preview.
val freshSongs = repo.getRecentlyAddedSongs(freshAlbums)

// Correct: keep preview and all-song navigation separate.
val freshRecentlyAddedSongs = repo.getRecentlyAddedSongs(freshAlbums)
val freshSongs = repo.getAllSongs()
```

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
