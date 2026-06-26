# Android App Development Guidelines

> Project-specific guidance for the Nordic media hub Android app.

## Overview

This spec layer covers the single Kotlin/Jetpack Compose Android app in `app/`. The most important boundaries are Retrofit API DTOs, repository/config/persistence code, Media3 playback engines, and Compose UI state surfaces.

## Guidelines Index

| Guide | Use When |
|-------|----------|
| [Directory Structure](./directory-structure.md) | Placing new source files, tests, DTOs, repositories, playback code, or Compose components |
| [Persistence Guidelines](./database-guidelines.md) | Changing DataStore config, cache fields, readiness helpers, or local persistence behavior |
| [Error Handling](./error-handling.md) | Adding repository calls, typed exceptions, `Response<T>` validation, or UI error propagation |
| [Quality Guidelines](./quality-guidelines.md) | Reviewing shared components, cache semantics, test coverage, and known anti-patterns |
| [Logging Guidelines](./logging-guidelines.md) | Adding or changing `Log.*` calls or OkHttp logging interceptors |
| [AudiobookShelf Integration Contract](./audiobookshelf-integration.md) | Changing audiobook auth, library browsing, playback sessions, progress sync, or Media3 audiobook state |
| [Emby Integration Contract](./emby-integration.md) | Changing Emby auth, library/item mapping, video playback, progress reporting, or season/episode browsing |
| [Navidrome Integration Contract](./navidrome-integration.md) | Changing star/favorite toggles, playlist CRUD, or Subsonic API star/unstar/getStarred2 endpoints |

## Pre-Development Checklist

- Read [Directory Structure](./directory-structure.md) before adding files or moving code between layers.
- Read [Persistence Guidelines](./database-guidelines.md) before changing `ConfigRepository`, server config models, or cache models.
- Read [Error Handling](./error-handling.md) before adding repository methods or changing exception behavior.
- Read [Quality Guidelines](./quality-guidelines.md) before modifying shared UI state components, music library navigation, or cache contracts.
- Read [Logging Guidelines](./logging-guidelines.md) before adding diagnostics.
- Read the service-specific contract when touching Navidrome, AudiobookShelf, or Emby behavior.

## Verification

Run Gradle tasks sequentially on Windows:

```powershell
.\gradlew.bat :app:compileDebugKotlin --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:lintDebug --no-daemon
```

Use `:app:assembleDebug --no-daemon` for final packaging verification when playback, manifest, resources, or dependency wiring changes.
