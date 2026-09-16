# UI 精修第三轮：有声书域页面与弹层收口

## 目标与承接

全应用 UI 精修系列第三轮。首轮（09-13）确认样板方向并建立模拟器验收底座；第二轮（09-14）完成音乐域闭环（7 个浏览页 + 歌单弹层 + 下载/收藏操作层 + 均衡器修整），版本 0.1.10 / 10。本轮按同一方法论推进有声书域：逐页审计 → 严格基线 → 修整 → 双主题/字号矩阵验收 → 单次确认提交。

基线 main / 8bef047，工作区干净；沿用专用 AVD `nordic-ui-api34` / `emulator-5580`，不操作个人手机。

## What I already know

- 有声书 UI 全部在两个文件：`AudiobookScreen.kt`（884 行：书库 Home + 详情）与 `AudiobookPlayerScreen.kt`（523 行：播放器 + 4 个弹层）。
- coverage.md 列出的未验条目：书库/来源选择/筛选、书籍详情/章节列表、有声书播放器、章节面板、有声书倍速、睡眠定时、书签与笔记，共 7 个页面/弹层。
- 生产入口：`AudiobookScreen`（LazyColumn 单滚动：页头 + 书库选择 chip + 摘要卡列表 / 详情头 + 章节行）；`AudiobookPlayerScreen`（下拉关闭手势 + `MediaAudioPlayerBody` + 4 个 `MediaPlayerSheet` 弹层：书签/睡眠定时/章节/倍速）。
- 已复用的共享组件：`MediaPageHeader`、`MediaChoiceChip`（书库选择）、`CoverArt`、`resolveAudiobookCollectionLayout`（委托 `resolveMusicCollectionLayout`）、`MusicCollectionDescription`、`MediaPlayerSheet`、`MediaPlayerChoiceRow`、`MediaPlayerTimeline`、`MediaTransportRow`、`MediaPlaybackSpeedSheet`。
- Debug 样板底座已存在：`UiCatalogActivity` + `UiCatalogSamples.kt`（`UiSampleScreen` 枚举、`UiSampleState` 10 种状态）+ `MusicCatalogSample`（第二轮模式：内存数据宿主复用生产 Composable）。本轮照此新增 Audiobook 宿主 + 样板条目。
- 已有 JVM 测试：`AudiobookScreenTest.kt`（纯函数回归 27 例）。
- 规范合同已覆盖有声书部分规则：ui-consistency.md「业务页面扩展」已写明 audiobook 布局委托与作者缺省文案合同；尚未形成独立 audiobook-ui.md。
- 与音乐域同类的已知模式问题（待审计确认）：
  - 章节行（详情页）是纯展示 Surface，无点击跳转、无当前章节高亮——与播放器章节面板行为不一致。
  - `AudiobookSummaryCard` 列表为 LazyColumn 默认间距（未显式 8dp），与音乐第二轮收敛的 8dp 不一致。
  - 弹层（书签/定时/章节）内容用 `LazyColumn weight(1f, fill=false)`，短屏下是否可达需实测。
  - `AudiobookBookmarkSheet` 添加书签按钮在 LazyColumn 之外，短屏/IME 场景需实测。
  - `LocalAppPreferences.current.audiobookSkipBack/Forward/SleepMinutes` 直接内联在播放器控件描述中，大字体下 FlowRow 换行行为需实测。
- 状态矩阵候选（沿用 UiSampleState 语义）：normal / long / empty（未配置）/ library_empty（无书库）/ loading / error / no_art / refreshing（缓存刷新）/ cached_error；书签弹层另有空书签、当前书签高亮。
- 规范产物：本轮应新增 `.trellis/spec/backend/audiobook-ui.md`（对照 music-ui.md），并把音乐域已确认的规则映射到有声书。

## Assumptions (temporary)

- 有声书弹层与生产宿主数据流保持：样本宿主只提供内存状态/回调，不改 repository、请求版本保护、缓存存储。
- 设置域（含"有声书播放"设置页）不纳入本轮，与音乐轮一致留待设置批次。

## Decision (ADR-lite)

**章节行行为**：
- Context：详情页 `AudiobookChapterRow` 纯展示不可点击，播放器章节面板才可跳转，行为不一致。
- Decision：本轮仅展示修整（间距/层级/语义对齐），**不**新增点击跳转业务；把"详情页章节行点击跳转"记入未来 backlog（见 Out of Scope）。
- Consequences：本轮不扩回调链；未来加跳转时需补 onPlayChapter 回调 + instrumentation。

**版本号**：
- Decision：递增到 0.1.11 / 11，沿用每轮 +1 惯例，证据与 APK 对应。

## Requirements (evolving)

- 7 个实际存在的页面/弹层全部有真实生产入口、Debug 样例、逐页结论。
- 严格同条件前后基线（源码指纹 + APK 指纹 + 系统字号核对），不覆盖第二轮封存证据。
- 双主题 × 字号 1/1.5/2 × 宽度 320/360/392/720 中按覆盖矩阵选择实际渲染组合（第二轮已有底座）。
- 主要操作 ≥48dp、正文对比 ≥4.5:1、短屏/IME 完整可达。
- 保留全部真实回调：播放、进度、书签增删/跳转、睡眠定时、倍速、书库切换、刷新、详情缓存失效。
- 编译、完整 JVM、Lint、Debug/Release/测试 APK、专用模拟器 UI 测试通过。
- 版本递增 0.1.10 / 10 → 0.1.11 / 11，签名延续。
- DESIGN/CHANGELOG/规范更新，覆盖证据索引产出；单次提交，不 push。

## Acceptance Criteria (evolving)

- [ ] 7 个条目逐页有结论，非复制展示版 UI。
- [ ] 严格前后证据可追溯。
- [ ] 主要操作 ≥48dp、对比度、可达性通过。
- [ ] 真实回调不丢失。
- [ ] 全量门禁通过。
- [ ] 文档/规范/证据更新，单次确认提交。

## Definition of Done

- 测试新增/更新（单测 + instrumentation）。
- Lint / 全量 JVM 通过。
- 规范 audiobook-ui.md 新增并同步 ui-consistency.md 业务页面扩展。
- 提交计划经用户确认后一次提交，不 push。

## Out of Scope

- 视频、WebDAV、设置域逐页打磨（后续批次）。
- 新增业务能力：不做"笔记"（源码只有书签，coverage.md 的"书签与笔记"条目以实际源码为准，只验书签）。
- **未来 backlog：详情页章节行点击跳转**（本轮已确认只修展示；加跳转属业务扩展，需 onPlayChapter 回调 + instrumentation，另行任务）。
- 真实服务器/个人设备验收；TalkBack 完整路径。
- 更换字体/依赖、重做导航。

## Technical Notes

- 关键文件：`app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`、`AudiobookPlayerScreen.kt`、`app/src/debug/java/com/nordic/mediahub/ui/UiCatalogSamples.kt`、`MusicCatalogSamples.kt`（模式参考）、`app/src/test/java/com/nordic/mediahub/ui/AudiobookScreenTest.kt`。
- 第二轮方法论文档：`.trellis/tasks/archive/2026-09/09-14-ui-polish-music/research/`（implementation-plan.md、run-ui-checks.py、verify-build-artifacts.py、summarize-evidence.py、make-review.py 可复制改造）。
- 规范：ui-consistency.md、ui-catalog-verification.md、music-ui.md、quality-guidelines.md、build-release.md。
- 睡眠定时弹层含"使用预选"行读取 `LocalAppPreferences`，样本宿主不写真实偏好。
