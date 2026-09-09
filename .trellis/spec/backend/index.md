# Android App Development Guidelines

> Project-specific guidance for the Nordic media hub Android app.

## Overview

This spec layer covers the single Kotlin/Jetpack Compose Android app in `app/`. The most important boundaries are Retrofit API DTOs, repository/config/persistence code, Media3 playback engines, and Compose UI state surfaces.

## Guidelines Index

| Guide | Use When |
|-------|----------|
| [Directory Structure](./directory-structure.md) | Placing new source files, tests, DTOs, repositories, playback code, or Compose components |
| [Persistence Guidelines](./database-guidelines.md) | 修改加密配置、DataStore 迁移、播放偏好/订阅、缓存字段或就绪条件 |
| [Error Handling](./error-handling.md) | Adding repository calls, typed exceptions, `Response<T>` validation, or UI error propagation |
| [音乐页面 UI](./music-ui.md) | 修改音乐发现、列表、专辑/歌手/歌单详情、集合操作与文案 |
| [共享 UI 一致性](./ui-consistency.md) | 修改主题/字体、标题导航、分段选择、搜索输入或详情动作；执行跨页面一致性检查 |
| [Quality Guidelines](./quality-guidelines.md) | Reviewing shared components, cache semantics, test coverage, and known anti-patterns |
| [文档规范](./documentation-guidelines.md) | 编写中文优先沟通、用户可见文案、CHANGELOG 条目、README 更新或任务文档 |
| [Logging Guidelines](./logging-guidelines.md) | Adding or changing `Log.*` calls or OkHttp logging interceptors |
| [AudiobookShelf Integration Contract](./audiobookshelf-integration.md) | Changing audiobook auth, library browsing, playback sessions, progress sync, or Media3 audiobook state |
| [Emby Integration Contract](./emby-integration.md) | 修改 Emby 认证、媒体库映射、视频播放/画中画、章节与片头跳过、进度上报或分季选集 |
| [Navidrome Integration Contract](./navidrome-integration.md) | Changing star/favorite toggles, playlist CRUD, or Subsonic API star/unstar/getStarled2 endpoints |

## Pre-Development Checklist

- Read [Directory Structure](./directory-structure.md) before adding files or moving code between layers.
- Read [Persistence Guidelines](./database-guidelines.md) before changing `ConfigRepository`, server config models, or cache models.
- Read the "Cross-Domain Media Cache Refresh" scenario in [Persistence Guidelines](./database-guidelines.md) before changing any cache repository, TTL helpers, launch/manual refresh flow, or config-switch cache cleanup.
- Read [Error Handling](./error-handling.md) before adding repository methods or changing exception behavior.
- 修改音乐浏览/集合详情/歌单弹窗前，阅读 [音乐页面 UI](./music-ui.md)。
- 修改共享视觉/交互组件前，先阅读 [共享 UI 一致性](./ui-consistency.md)，并保留逐页真机验收与自动检查的区别。
- Read [Quality Guidelines](./quality-guidelines.md) before modifying shared UI state components, music library navigation, or cache contracts.
- 修改用户可见文案、项目文档、任务文档、发布记录或开发规范前，先阅读 [文档规范](./documentation-guidelines.md)。
- Read [Logging Guidelines](./logging-guidelines.md) before adding diagnostics.
- Read the service-specific contract when touching Navidrome, AudiobookShelf, or Emby behavior.

## Verification

Gradle daemon is enabled (no `--no-daemon`) so consecutive invocations reuse a warm JVM, plugin loads, and dependency resolution. Tasks are combined into a single Gradle invocation to share startup cost.

### Fast Verification (daily dev loop)

Run after routine code changes — compile + unit tests only:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest
```

### Full Verification (pre-commit)

Run before committing — adds `lintDebug` for the full quality gate:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

### Final Packaging Verification

Use `:app:assembleDebug` when playback, manifest, resources, or dependency wiring changes:

```powershell
.\gradlew.bat :app:assembleDebug
```
