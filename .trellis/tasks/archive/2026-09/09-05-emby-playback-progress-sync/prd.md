# Emby 播放记录拉取与及时同步

## User Report

1. **每次进入视频页需要拉取最新播放记录** — 当前视频目录有 30 分钟 TTL 缓存（`isCacheFresh`），进入视频页若缓存未过期则直接用缓存，播放记录（`playbackPositionSeconds/isPlayed`，来自其他设备或上次会话）可能过时
2. **本地播放记录需及时同步到 Emby** — 当前周期同步 30s 一次 + 暂停/关闭时同步；但播放器关闭路径以外（如切换 tab、进程回收）没有兜底

## Root Cause / 现状

- `VideoScreen.LaunchedEffect(savedConfig)`：`applyCachedVideo` → 仅当 `!isCacheFresh(cacheUpdatedAtMillis)` 才 `refreshVideo`。TTL 30 分钟内进页不拉取。
- 播放记录在 `VideoItem.playbackPositionSeconds`（进 `getCatalog`/`getLibraryItems` 全量返回时从 Emby `UserData` 解析）。
- 同步链路已有：周期 30s（播放中）、暂停瞬间、关闭时（stopped）；`VideoPlaybackViewModel.closeVideoPlaybackInternal` 失败会后台重试一次。
- 缺：播放器层可见性变化（打开播放器/关闭播放器/切 tab）时不触发即时 sync；视频页回前台不刷新播放记录。

## Requirements

### R1 进入视频页拉取最新播放记录
- `VideoScreen` 加 `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`：每次 ON_RESUME 且配置就绪时后台刷新（cache-then-network：先显示缓存，同时拉取 `getCatalog` 更新 `videos` 并写缓存）。
- 不改 TTL 逻辑本身（手动刷新仍 bypass TTL）；改为 **ON_RESUME 每次都触发一次静默 refresh**（`refreshVideo` 已有 `isLoading` 防重入）。启动路径保持原样（TTL 门控），避免启动时双请求。
- `refreshVideo` 的 catalog 全量拉取较重——可接受（用户明确要求"每次进入需要拉取播放记录"）。

### R2 及时同步本地播放记录到 Emby
- `VideoPlaybackViewModel` 增加 `syncNow()`：立即以当前 `engine.state` 位置 `syncPlaybackProgress`（fire-and-forget，失败记 `syncError`），供外部事件触发。
- `MainActivity` 在播放器可见性变化时调用：`showVideoPlayer` 由 true→false（关闭，已有 stopped 同步 ✅ 不动）；**打开播放器（false→true）时**不动（引擎刚起，位置无意义）。
- 关键兜底：`VideoPlayerLayer` 的 `DisposableEffect`/`ON_STOP`——App 退后台（ON_STOP）且视频在播放时调用 `syncNow()`，防止进程被杀丢进度（当前 30s 周期间隔内的最后进度会丢）。`LifecycleEventEffect(Lifecycle.Event.ON_STOP)` in `VideoPlayerLayer`，仅当 `showVideoPlayer` 时触发。

### 测试
- `resolveVideoProgressSyncBaselineSeconds` 已有测试；`syncNow` 逻辑薄（runCatching 包装），以现有 VM 模式为准不加 mock 测试（项目无 VM 测试基建）。

## Acceptance Criteria

* [ ] 视频页每次回到前台（ON_RESUME）都会后台拉取最新播放记录，UI 先显示缓存无阻塞
* [ ] 播放器打开时退后台/进程回收前会即时上报一次进度
* [ ] compile + test + lint 通过

## Out of Scope

* 增量播放记录 API（/Users/{id}/Items/Resume 等）— 全量 catalog 已含 UserData
* 续播精度优化
