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

---

## Testing Requirements

(To be filled by the team)

---

## Code Review Checklist

- [ ] No string-based error type checks (use typed exceptions)
- [ ] No duplicate utility functions across files
- [ ] Shared Compose primitives use `internal` visibility
- [ ] Repeated loading/error/empty state cards use shared media state components
- [ ] `NavidromeRepository` is `remember`ed, not constructed per call
- [ ] Config readiness checks use `isReadyForMusicSync()`, not inlined
- [ ] All public `NavidromeRepository` methods have both `NavidromeApiException` and `Exception` catch blocks

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
