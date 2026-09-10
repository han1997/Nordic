# Directory Structure

> Project-specific structure for the Nordic Android media hub app.

## Overview

## Overview

This repository is a single Android application module (`app`) written in Kotlin and Jetpack Compose. The package root is `com.nordic.mediahub`; there are no separate backend services, shared packages, or Room database modules.

**applicationId vs namespace**: the device-facing package identity (`applicationId`) is `fun.han1997.nordic`, while the Kotlin/资源 namespace stays `com.nordic.mediahub`. They are intentionally decoupled (official Android practice) — do NOT "fix" one to match the other. Renaming the applicationId does not touch source files; renaming the namespace would require rewriting every Kotlin file and is out of scope unless the user explicitly asks.

**Versioning rule (user decision, 2026-09-10)**: `versionName` starts at `0.1.1`; every code change bumps the patch digit by 1 (0.1.2, 0.1.3, …). The minor (second) and major (first) digits increase ONLY when the user explicitly says so. `versionCode` is a simple monotonic counter (1, 2, 3, …) incremented together with versionName. Keep both in sync in `app/build.gradle.kts` on every work commit that changes code.

**Release signing**: release builds sign with the debug keystore (`signingConfig = signingConfigs.getByName("debug")`) for sideload distribution — `assembleRelease` emits an installable `app-release.apk` directly. An unsigned APK fails to install on ColorOS/Android 16 with "安装包异常" (verified 2026-09-10: `apksigner verify` reported `Missing META-INF/MANIFEST.MF` on the unsigned artifact). If the user later wants store distribution, generate a dedicated release keystore then.

**applicationId vs namespace**: the device-facing package identity (`applicationId`) is `fun.han1997.nordic` (changed 2026-09-10), while the Kotlin/resource `namespace` stays `com.nordic.mediahub`. These are intentionally decoupled per official Android practice — do NOT rename the 119-file Kotlin package root just to chase an applicationId change; edit `applicationId` in `app/build.gradle.kts` instead.

Core source lives under `app/src/main/java/com/nordic/mediahub/`:

```text
app/src/main/java/com/nordic/mediahub/
  MainActivity.kt          App shell, tab routing, shared playback orchestration
  api/                     Retrofit interfaces and DTOs for remote media services
  data/                    Config models, repositories, auth helpers, cache helpers
  playback/                Media3 service and playback engines
  ui/                      Compose screens and reusable UI components
  ui/theme/                Material color/theme definitions
```

Unit tests live under matching package paths in `app/src/test/java/com/nordic/mediahub/`.

## Layer Ownership

### `api/`

Use `api/` for Retrofit endpoint interfaces and DTOs that mirror remote service payloads.

Reference files:
- `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`
- `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`
- `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`

Keep DTO mapping out of composables. Convert API DTOs to app-facing models in `data/` repositories.

### `data/`

Use `data/` for config data classes, readiness helpers, Retrofit-backed repositories, auth URL helpers, and local cache helpers.

Reference files:
- `app/src/main/java/com/nordic/mediahub/data/ServerConfig.kt`
- `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
- `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`

Repositories own network calls, response validation, domain mapping, and user-context error wrapping. UI should call repository methods, not Retrofit APIs directly.

### `playback/`

Use `playback/` for Media3 state and service integration.

Reference files:
- `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`
- `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`
- `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackService.kt`

Playback engines expose state objects and commands to the app shell/screens. Keep service/controller concerns out of `ui/` files except for passing callbacks and rendering state.

### `ui/`

Use `ui/` for Compose screens and reusable presentation components.

Reference files:
- `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`
- `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`
- `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MediaStateComponents.kt`
- `app/src/main/java/com/nordic/mediahub/ui/MusicHomeSections.kt`

Top-level screens may manage Compose state and launch repository refresh work. Reusable components that are shared across files should be `internal` instead of duplicated as private helpers.

## Naming Conventions

- Retrofit interfaces end with `Api`, for example `NavidromeApi`.
- Network DTOs use service-specific names and a `Dto` suffix when they are not already Subsonic model names, for example `EmbyItemDto` and `AudiobookShelfPlaybackSessionDto`.
- Repository classes end with `Repository`.
- Config data classes live in `ServerConfig.kt`; readiness helpers use names like `isReadyForMusicSync()`.
- Compose screens end with `Screen`; small private composables are allowed inside the owning screen file when they are not reused elsewhere.
- Tests mirror the subject under test, for example `EmbyRepositoryTest` and `AudiobookPlaybackEngineTest`.

## Common Mistakes

- Do not put Retrofit DTO parsing logic into Compose UI files.
- Do not construct repositories inline inside every suspend call; remember repository instances at the screen/app-shell boundary when config changes.
- Do not mix media domains. Navidrome songs, AudiobookShelf items/sessions, and Emby video items have separate model types.
- Do not add a new top-level package unless the code has a clear ownership boundary that does not fit `api`, `data`, `playback`, or `ui`.
