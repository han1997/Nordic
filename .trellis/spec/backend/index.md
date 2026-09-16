# Android App Development Guidelines

> Project-specific guidance for the Nordic media hub Android app.

## Overview

This spec layer covers the single Kotlin/Jetpack Compose Android app in `app/`. The most important boundaries are Retrofit API DTOs, repository/config/persistence code, Media3 playback engines, and Compose UI state surfaces.

## Guidelines Index

| Guide | Use When |
|-------|----------|
| [Directory Structure](./directory-structure.md) | Placing new source files, tests, DTOs, repositories, playback code, or Compose components |
| [构建身份、版本与签名](./build-release.md) | 修改 applicationId、namespace、版本号、签名、打包或 ADB 启动文档；代码提交前检查版本递增 |
| [多服务器、WebDAV 与设置中心](./media-sources-webdav-settings.md) | 修改来源管理、媒体认证与数据隔离、WebDAV 或设置导航/偏好 |
| [Persistence Guidelines](./database-guidelines.md) | 修改加密配置、DataStore 迁移、播放偏好/订阅、缓存字段或就绪条件 |
| [Error Handling](./error-handling.md) | Adding repository calls, typed exceptions, `Response<T>` validation, or UI error propagation |
| [音乐页面 UI](./music-ui.md) | 修改音乐发现、列表、专辑/歌手/歌单详情、集合操作与文案 |
| [有声书页面 UI](./audiobook-ui.md) | 修改有声书书库 Home、书籍详情/章节列表、播放器或书签/睡眠定时/章节/倍速弹层 |
| [视频页面 UI](./video-ui.md) | 修改视频书库 Home、搜索/筛选、继续观看架、详情/分季选集 |
| [音乐歌词](./music-lyrics.md) | 修改歌词解析/加载状态、完整显示、同步跟随、手动浏览和播放器视图记忆 |
| [共享 UI 一致性](./ui-consistency.md) | 修改主题/字体、标题导航、分段选择、搜索输入或详情动作；执行跨页面一致性检查 |
| [UI 样板验收](./ui-catalog-verification.md) | 修改 Debug UI 目录、Compose instrumentation、系统字号/窗口截图或逐页证据 |
| [Quality Guidelines](./quality-guidelines.md) | Reviewing shared components, cache semantics, test coverage, and known anti-patterns |
| [文档规范](./documentation-guidelines.md) | 编写中文优先沟通、用户可见文案、CHANGELOG 条目、README 更新或任务文档 |
| [Logging Guidelines](./logging-guidelines.md) | Adding or changing `Log.*` calls or OkHttp logging interceptors |
| [AudiobookShelf Integration Contract](./audiobookshelf-integration.md) | Changing audiobook auth, library browsing, playback sessions, progress sync, or Media3 audiobook state |
| [Emby Integration Contract](./emby-integration.md) | 修改 Emby 认证、媒体库映射、视频播放/画中画、章节与片头跳过、转码与清晰度、进度上报或分季选集 |
| [视频自动连播](./video-auto-play-next.md) | 修改前台连播、下一项解析、连播偏好、结束事件或异步视频交接 |
| [Navidrome Integration Contract](./navidrome-integration.md) | Changing star/favorite toggles, playlist CRUD, or Subsonic API star/unstar/getStarled2 endpoints |

## Pre-Development Checklist

- 修改构建身份、版本号、签名或发布/安装文档前，阅读[构建身份、版本与签名](./build-release.md)；每个代码工作提交同步递增 patch 与 versionCode。
- 修改多来源、媒体 URL 认证、WebDAV 或设置中心前，阅读[多服务器、WebDAV 与设置中心](./media-sources-webdav-settings.md)。
- Read [Directory Structure](./directory-structure.md) before adding files or moving code between layers.
- Read [Persistence Guidelines](./database-guidelines.md) before changing `ConfigRepository`, server config models, or cache models.
- Read the "Cross-Domain Media Cache Refresh" scenario in [Persistence Guidelines](./database-guidelines.md) before changing any cache repository, TTL helpers, launch/manual refresh flow, or config-switch cache cleanup.
- Read [Error Handling](./error-handling.md) before adding repository methods or changing exception behavior.
- 修改音乐浏览/集合详情/歌单弹窗前，阅读 [音乐页面 UI](./music-ui.md)。
- 修改有声书书库、详情、播放器或弹窗前,阅读[有声书页面 UI](./audiobook-ui.md)。
- 修改视频书库 Home、搜索/筛选、继续观看架、详情/分季选集前，阅读[视频页面 UI](./video-ui.md)。
- 修改歌词获取、播放页歌词或相关手势前，阅读[音乐歌词](./music-lyrics.md)。
- 修改共享视觉/交互组件前，先阅读 [共享 UI 一致性](./ui-consistency.md)，并保留逐页真机验收与自动检查的区别。
- Read [Quality Guidelines](./quality-guidelines.md) before modifying shared UI state components, music library navigation, or cache contracts.
- 修改 Debug 样板、截图与 UI instrumentation 前，阅读 [UI 样板验收](./ui-catalog-verification.md)；只能操作专用模拟器，自动化通过不代表用户已确认视觉方向。
- 修改用户可见文案、项目文档、任务文档、发布记录或开发规范前，先阅读 [文档规范](./documentation-guidelines.md)。
- Read [Logging Guidelines](./logging-guidelines.md) before adding diagnostics.
- Read the service-specific contract when touching Navidrome, AudiobookShelf, or Emby behavior.
- 修改手动/自动下一项解析、连播偏好、播放结束或异步切集前，阅读[视频自动连播](./video-auto-play-next.md)。

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

涉及包名、版本或签名时，还需按[构建身份、版本与签名](./build-release.md)检查 release APK 的实际签名和 manifest，不能仅凭文件名判定成功。

Use `:app:assembleDebug` when playback, manifest, resources, or dependency wiring changes:

```powershell
.\gradlew.bat :app:assembleDebug
```
