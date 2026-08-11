# 打磨软件 UI 与操作逻辑

## Goal

在已经完成图标/毛玻璃 polish 的基础上，做一轮**导航流一致性审计与修复**：覆盖音乐/有声书/视频三大模块的页面层级、回退行为、刷新后选中状态、首连引导，确保用户在不同入口和状态下导航都是可预测的；不重做整体信息架构。

## What I Already Know

* 上一轮刚完成共享 UI 图标与毛玻璃质感 polish，已统一 header、back、bottom nav、播放 dock 的图标语言。
* 音乐/有声书/视频三大模块均采用 state-driven navigation（`MusicLibraryPage` / `AudiobookLibraryPage` / 视频 `selectedVideo`），无 Jetpack Navigation Compose。
* 已有 `BackHandler` 优先级约定（page-level before config），`ConfigRepository savedConfig` 作为 config-change reset 边界，各屏有 `LaunchedEffect(savedConfig)`。
* 缓存层有 `NavidromeMusicCacheRepository` / `EmbyVideoCacheRepository` / `AudiobookCacheRepository`，支持 cache freshness 与 timestamp 展示。
* CHANGELOG 已记录近期大量操作逻辑改进（音乐队列编辑、视频沉浸式控制层、底部 dock 把手、播放器手势等）。
* 代表性文件：`MusicScreenV2.kt`、`MusicPlayerScreen.kt`、`MusicQueueSheet.kt`、`VideoScreen.kt`、`VideoPlayerScreen.kt`、`AudiobookScreen.kt`、`AudiobookPlayerScreen.kt`、`PlaybackDock.kt`、`SharedComponents.kt`。

## Requirements

### 本轮目标：审计 + 修复发现的偏差

对以下四个维度做一轮跨模块审计；对每个发现的偏差，按「可预测导航」原则修复，不强求新功能：

1. **回退路径**
   * 音乐：AlbumDetail / ArtistDetail / PlaylistDetail / Search 的 `BackHandler` + `onBack` 行为是否一致；从 ArtistDetail 回退是否能回到来源上下文而不是粗暴回 Home。
   * 视频：详情返回、`BackHandler` 与 `searchExpanded` / `searchQuery` / `selectedTypeFilter` 的交互是否一致；嵌套 `BackHandler` 的优先级是否正确。
   * 有声书：Detail 回退、library 切换后的 `selectedItem` 重置是否一致。

2. **刷新后选中/位置**
   * 音乐：刷新后 `selectedAlbum` / `selectedArtist` / `selectedPlaylist`、当前 `libraryPage` 是否仍指向存在的对象；缓存+刷新双路径下的状态竞争是否影响感知。
   * 视频：`selectedVideo` 在 catalog 刷新后是否按 `resolveVideoSelectionAfterCatalogRefresh` 正确保留或清空；`searchQuery` / `selectedTypeFilter` 是否保留。
   * 有声书：`selectedItem` 在 library 刷新后是否按预期保留；`AudiobookLibraryPage.Detail` 在 item 消失后是否回到 Home。

3. **首次连接引导**
   * 三个模块未就绪时的空状态是否都引导到「前往配置 tab」，文案是否一致。
   * 配置从就绪变为未就绪时，是否一致地清空内容并回 Home，还是会出现残留空 detail 页。

4. **配置变更后页面复位**
   * `LaunchedEffect(savedConfig)` 的 reset 是否一致覆盖了所有 account-scoped 状态。
   * 是否存在静默回 Home 但用户没收到反馈的情况。

### 修复原则

* 不改变现有信息架构；只修不一致/不可预测的偏差。
* 优先选择「静默正确」而不是「显式提示」，除非用户行为真的发生了歧义。
* 修复时遵循 Trellis backend `BackHandler` / 性能 chrome / state 隔离 spec。
* 用户可见行为变化需更新 `CHANGELOG.md`。

### Confirmed Fix Scope

本轮只修音乐相关 findings：

* **M1**：音乐详情页回退丢失来源上下文。AlbumDetail / ArtistDetail / PlaylistDetail / Search 的返回应尽量回到用户进入详情前的上下文；系统 back 和可见 back 按钮继续共用同一逻辑。
* **M3**：音乐刷新后可能留下悬挂 detail selection。刷新 albums / artists / playlists 后，`selectedAlbum` / `selectedArtist` / `selectedPlaylist` 必须与刷新后的列表一致；对象消失时清空 detail payload 并回到合理页面。
* **M5**：音乐配置变更强制复位时缺少反馈。仅当用户处于非 Home 或已有可见内容时，给出轻量提示说明配置变化导致页面回到音乐首页。

本轮不修视频/有声书 findings。`V4`、`V6`、`A4`、`C1` 作为后续任务候选保留在审计报告。

## Acceptance Criteria

* [ ] 音乐 AlbumDetail / ArtistDetail / PlaylistDetail 的系统 back 与可见 back 按钮都使用同一套来源感知回退逻辑。
* [ ] 从音乐 Home / Albums / Artists / Search / Playlists 进入详情后，返回尽量回到进入前的上下文；来源失效时安全回 Home 或对应列表页。
* [ ] 音乐刷新后 `selectedAlbum` / `selectedArtist` / `selectedPlaylist` 不会指向刷新后不存在的对象；相关 detail payload 同步清空或更新。
* [ ] 音乐配置变更导致非 Home 页面复位时，用户能看到轻量说明；正常首次加载不产生多余提示。
* [ ] 审计结果和本轮修复/不修复决策记录在 `info.md`。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。
* [ ] 用户可见导航行为变化已更新 `CHANGELOG.md`。

## Assumptions (temporary)

* 本轮不重做整体信息架构、不引入新依赖、不改底层媒体数据接口。
* 改动仍聚焦在 Compose UI 层和与之紧耦合的 ViewModel/Engine 调用边界。
* 改动后 `compileDebugKotlin` / `testDebugUnitTest` / `lintDebug` 仍需通过。

## Open Questions

* 音乐：从 ArtistDetail / AlbumDetail / PlaylistDetail / Search 回退的预期路径分别是什么？是否应回到来源页而不是统一回 Home？
* 视频：从详情返回后，搜索关键词、type filter、滚动位置是否应保留？目前是否真的会丢失？
* 有声书：刷新后 `selectedItem` 是否总是按预期保留或回退到 Home？
* 首次连接：未就绪 config 进入 Music / Video / Audiobook 时的引导文案与跳转入口是否一致？
* 是否需要为「配置已变更 → 当前页失效」提供更显式的提示，而不是静默回 Home？

## Technical Approach

1. 派 `trellis-research` 子代理对三大模块的导航相关代码做一轮静态审计，产出 `research/navigation-audit.md`，列出每个发现的偏差 + 证据（文件:行号）+ 建议。
2. 主会话基于审计报告与用户确认哪些需要修复、哪些可接受现状；确认后派 `trellis-implement` 子代理按清单修复。
3. 修复完成后派 `trellis-check` 子代理复核，确保没有引入回归（特别是 `BackHandler` 优先级 / 性能 chrome / state 隔离）。
4. 审计结论与修复决策记录在 `info.md`。

## Research References

* [`research/navigation-audit.md`](research/navigation-audit.md) — 静态审计发现 15 项：2 个 must-fix、6 个 should-fix、7 个 acceptable；最高优先级集中在音乐详情回退来源丢失和刷新后 detail selection reconciliation。

## Decision (ADR-lite)

**Context**: 三个方向（导航流 / 加载刷新 / 播放队列）一次性做范围太大、PR 过散；用户未观察到具体 bug，只是担心导航不一致。
**Decision**: 本轮只做页面/导航流梳理。先做静态审计产出报告，再按确认清单修复；用户确认只修音乐相关 M1/M3/M5，视频/有声书 findings 留作后续。
**Consequences**: 本轮实现范围集中在 `MusicScreenV2` / `MusicScreenLogic` 及必要测试；视频/有声书审计结论保留但不修改，避免一个 PR 横跨太多行为。

## Definition of Done (team quality bar)

* PRD 范围经用户确认。
* 代码改动聚焦本轮确认的 UI/操作逻辑范围，不做无关视觉重构。
* 遵循 `DESIGN.md`、Trellis backend UI 规范和文档规范。
* 用户可见行为更新 `CHANGELOG.md`。
* `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。

## Out of Scope (explicit)

* 不重做页面信息架构或三大媒体页面整体视觉布局。
* 不引入新图标库、新图片资产或大型动画系统。
* 不修改底层 Navidrome / AudiobookShelf / Emby 数据接口、播放引擎或缓存 schema。
* 本轮不做数据加载/刷新体验统一和播放/队列交互 polish（留到后续任务）。

## Technical Notes

* Task directory: `.trellis/tasks/08-11-polish-ui-interaction`
* 相关 UI 文件位于 `app/src/main/java/com/nordic/mediahub/ui`
* Spec 参见 `.trellis/spec/backend/index.md` 中的 quality / documentation / persistence / error-handling guidelines。
