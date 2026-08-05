# 服务器配置页面提取与优化

## Goal

将当前分散在 Music / Audiobook / Video 三个媒体页面内的服务器配置入口与保存逻辑提取出来，并优化配置体验，让用户能更集中、更一致地管理 Navidrome、AudiobookShelf、Emby 等服务器连接信息。

## What I already know

* 当前是单 Android app，Jetpack Compose UI，配置持久化由 `ConfigRepository` / `EncryptedConfigStore` 管理。
* 配置卡组件集中在 `app/src/main/java/com/nordic/mediahub/ui/ConfigCards.kt`：
  * `NavidromeConfigCard`
  * `AudiobookConfigCard`
  * `VideoConfigCard`
  * `ConfigTextField`
* 配置入口和保存逻辑分散在三个媒体页面：
  * `MusicScreenV2`：右上角齿轮展开 `MediaConfigPanel`，内部调用 `NavidromeConfigCard`，保存 `saveNavidromeConfig(config)`。
  * `AudiobookScreen`：右上角齿轮展开 `MediaConfigPanel`，内部调用 `AudiobookConfigCard`，保存 `saveAudiobookConfig(config)`。
  * `VideoScreen`：右上角齿轮展开 `MediaConfigPanel`，内部调用 `VideoConfigCard`，保存 `saveVideoConfig(config)`。
* 主界面 `MainActivity` 当前只有 3 个底部 tab：音乐 / 有声书 / 视频，由 `PolishedBottomNav` 固定渲染 index 0/1/2。
* `MediaPageHeader` / `HeaderAction` / `MediaConfigPanel` 是当前媒体页配置入口的统一视觉模式。

## Assumptions (temporary)

* “提取”可能指把三块配置从各媒体页右上角内联面板中抽离，形成一个统一的“服务器配置”页面。
* “优化”可能包括集中查看连接状态、减少重复表单/保存逻辑、保持三域配置 UI 一致、避免媒体页面承担太多配置状态。
* 暂不假设新增服务器类型、修改加密存储、或改变配置字段含义。

## Open Questions

* （已解决，见 Requirements / Decision）

## Requirements (evolving)

* 在底部导航新增第 4 个 tab：配置。
* 新配置 tab 展示统一服务器配置页，集中管理 Navidrome / AudiobookShelf / Video Server 三类配置。
* 配置页采用纵向三卡布局：Navidrome / AudiobookShelf / Video Server 三张卡片从上到下排列。
* 移除 Music / Audiobook / Video 三个媒体页右上角配置齿轮与内联 `MediaConfigPanel` 配置入口。
* 统一配置页支持对每个服务执行“测试连接”。
* 测试连接使用轻量网络探测，不复用完整媒体库刷新：
  * Navidrome：新增轻量 Subsonic 探测（优先 `ping.view`；若没有现成 API 方法则在 `NavidromeApi` 补充）。
  * AudiobookShelf：登录/获取 token 后请求 `getLibraries()`，不拉 items/detail。
  * Emby：认证并获取可用 library/views，不拉完整 catalog items。
* 保留 Navidrome / AudiobookShelf / Video Server 三类配置能力。
* 配置保存仍通过 `ConfigRepository`，不绕过现有加密存储。
* 每张配置卡保留独立保存按钮；测试连接使用当前表单值（可在保存前测试）。
* 不破坏现有媒体页加载、缓存、刷新和播放逻辑。

## Acceptance Criteria (evolving)

* [ ] 用户可以在统一位置编辑并保存三类服务器配置。
* [ ] 配置页使用纵向三卡布局，每张卡有独立保存与测试连接动作。
* [ ] 底部导航包含配置入口，且现有音乐 / 有声书 / 视频 tab 行为不回退。
* [ ] 媒体页不再显示旧配置齿轮和内联配置面板。
* [ ] 用户可以在保存前/后测试每类服务连接，并看到成功/失败反馈。
* [ ] 测试连接不触发完整媒体库同步、不写入媒体缓存、不启动播放或进度同步。
* [ ] 保存后现有媒体页能继续通过 `savedConfig` flow 接收新配置并刷新。
* [ ] 现有配置字段、 readiness helper、加密存储契约不变。
* [ ] 编译、单测、lint 通过。

## Technical Approach

* 新增 `ServerConfigScreen`（或同等命名）到 `ui/` 层，集中承载三类配置表单、保存动作、测试连接状态。
* 将当前 `ConfigCards.kt` 的三张配置卡升级为可在统一配置页复用的卡片：支持状态摘要、测试连接按钮、保存按钮和测试结果提示。
* `MainActivity` 将底部导航扩展为 4 项：音乐 / 有声书 / 视频 / 配置；`selectedTab == 3` 渲染统一配置页。
* `MusicScreenV2` / `AudiobookScreen` / `VideoScreen` 移除 `showConfig` 状态、配置齿轮 HeaderAction、`MediaConfigPanel` 配置块，以及相关 BackHandler。
* 测试连接新增轻量 repository/API 方法或 helper：
  * Navidrome：增加 `ping.view` 或等价轻量探测，验证 Subsonic auth 与 base URL。
  * AudiobookShelf：复用认证 + `getLibraries()`，只验证可认证且能读 library list。
  * Emby：复用认证 + library/views 请求，避免拉完整 items catalog。
* 测试连接结果只作为 UI 状态展示，不保存配置、不写媒体缓存、不触发媒体页刷新。
* 保存仍调用 `ConfigRepository.saveNavidromeConfig` / `saveAudiobookConfig` / `saveVideoConfig`，让现有 `savedConfig` flow 驱动媒体页刷新。

## Decision (ADR-lite)

**Context**: 当前配置入口分散在三个媒体页面，导致配置状态和保存逻辑重复，媒体页右上角动作过多，且用户无法在一个位置管理所有服务器。

**Decision**: 新增底部“配置”tab，使用纵向三卡布局集中管理三类服务器；移除媒体页旧齿轮入口；加入轻量测试连接能力。

**Consequences**: 配置入口更清晰，媒体页更聚焦浏览/播放；底部导航从 3 项变 4 项；测试连接需要新增轻量 API/repository 探测和对应测试。

## Definition of Done (team quality bar)

* Tests added/updated where behavior is testable.
* Gradle gates pass: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`.
* Specs updated if this establishes a new navigation/config convention.

## Out of Scope (explicit)

* 新增服务器类型。
* 修改 credential 加密存储实现。
* 修改 Navidrome / AudiobookShelf / Emby API 连接协议。
* 重做媒体浏览页、缓存刷新机制或播放器逻辑。

## Technical Notes

* Existing config components: `app/src/main/java/com/nordic/mediahub/ui/ConfigCards.kt`.
* Existing per-screen config panels: `MusicScreenV2.kt`, `AudiobookScreen.kt`, `VideoScreen.kt`.
* Existing app navigation: `MainActivity.kt` + `PlaybackDock.kt` (`PolishedBottomNav` has 3 fixed tabs).
* Relevant specs: `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/database-guidelines.md`, `.trellis/spec/backend/quality-guidelines.md`.
