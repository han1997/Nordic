# UI 精修第四轮：视频浏览与详情

## Goal

全应用 UI 精修系列第四轮。首轮（09-13）确认样板方向；第二轮（09-14）音乐域闭环；第三轮（09-15）有声书域闭环，版本 0.1.11 / 11。本轮按同一方法论推进**视频浏览 + 详情**：逐页审计 → Debug 样板宿主 → 严格基线 → 修整 → 双主题/字号矩阵 → 单次确认提交。

用户已确认四域都要做，但拆成独立任务：本轮只收浏览/搜索/续播架/详情选集。播放器+7 面板、WebDAV、设置分别作为第 5/6/7 轮，不混入。

基线 main / 3efb17a，工作区干净；应用 0.1.11 / 11；沿用专用 AVD `nordic-ui-api34` / `emulator-5580`，不操作个人手机。

## What I already know

- 生产入口：`VideoScreen.kt`（718 行：书库 Home + 打开详情）嵌套 `VideoDetailScreen.kt`（414 行，已是纯 UI：数据/回调入参）。浏览积木在 `VideoBrowseComponents.kt`（384 行）：`VideoLibrarySelector`、`VideoCard`、`VideoSpotlightSections`/`Row`、`ContinueWatchingCard`、`VideoBrowserControls`。纯函数在 `VideoScreenLogic.kt`。
- coverage.md 视频段前 4 行未验：媒体库/筛选/排序、搜索、继续观看、详情/分季选集。源码浏览页**没有独立排序 UI**（只有类型筛选 + 本地搜索）；coverage 的「筛选/排序」以实际源码为准，只验筛选+搜索。
- 09-07 已做一批评：节头 heading+SemiBold、`videoShelfCardSize`、分集筛选迁 `MediaChoiceChip`、简介复用 `MusicCollectionDescription`、倍速面板迁 `MediaPlayerChoiceRow`（播放器属第 5 轮回归）。本轮不重做那些点，只回归并修残留。
- Debug 样板已有音乐/有声书宿主；`UiSampleScreen` 尚无视频浏览条目。有声书轮把 Home 内容镜像进 debug 私有 composable，已出现与生产漂移风险；本轮优先抽生产 `VideoHomeContent`，样板与生产共用，不复制展示版。
- `VideoDetailScreen` 已可直接给样板复用。`VideoEpisodeRow.isCurrent` 只在播放器选集面板传入；**详情页调用未传 isCurrent**，当前集不高亮。
- 已确认品牌/设备/密度：紫/青、深浅主题、原生字体、现有导航。Hero 白字叠黑渐变是已记录的视频 overlay 例外，不改成主题色。

## Assumptions (temporary)

- 海报栅格 `spacedBy(lg=16dp)` + `GridCells.Adaptive(156.dp)` 是海报卡节奏，不强制改成音乐列表 8dp；实测若乱再收，合同写明例外。
- 样板宿主只提供内存 `VideoItem` / 回调，不改 Emby repository、分库缓存、Resume 协议、请求版本保护。
- 设置域「视频播放」页不纳入本轮。

## Decision (ADR-lite)

**范围切分**：
- Context：用户要求视频浏览、播放器、WebDAV、设置全都做。播放器是 white-on-black + 窗内 panel + 横屏/PiP/连播，体量约等于有声书整域，验收矩阵与浏览页不同。
- Decision：本任务只做浏览+详情。播放器/WebDAV/设置另开第 5/6/7 轮任务，不在本轮声称覆盖。
- Consequences：coverage 视频段后 3 行（播放器/PiP/连播）和 7 个面板保持未验；本轮截图不做横屏播放器。

**内容层抽取**：
- Context：`VideoScreen` 把仓库/缓存/刷新和 LazyVerticalGrid 写在一起；有声书轮在 debug 里镜像了一份 Home，易漂移。
- Decision：从生产抽出无副作用 `VideoHomeContent`（状态卡 + 选择器 + 推荐架 + 筛选/搜索 + 海报栅格），`VideoScreen` 继续持有 Flow/缓存/刷新。`VideoDetailScreen` 已是内容层，不重复抽。
- Consequences：样板复用生产 Composable；抽取时保持全部状态分支与回调，禁止为样板简化空态。

**海报间距**：
- Decision：栅格保持 `spacedBy(lg)` / 横向架 `md`，不盲跟音乐 8dp。合同写「海报栅格例外」。
- Consequences：若实测 16dp 在 320dp 过空，可降到 `md`，但不降到 `sm`。

**详情当前集高亮**：
- Decision：详情分集列表按「可续播/正在看」传入 `isCurrent`（与播放器选集面板同一 `VideoEpisodeRow` 语义：`primaryContainer` + `onPrimaryContainer` + `selected`）。高亮规则用纯函数，详情与样板共用。
- Consequences：不新增「详情行点击与播放器不同」的业务；详情行本来就可 `onPlayEpisode`。

**版本号**：
- Decision：递增到 0.1.12 / 12，沿用每轮 +1。

## Requirements (evolving)

- 4 个实际存在的页面全部有真实生产入口、Debug 样例、逐页结论：媒体库（含库选择/类型筛选/空态）、搜索、继续观看（含最受好评/未播放架）、详情/分季选集。
- 严格同条件前后基线（源码指纹 + APK 指纹 + 系统字号核对），批次名 `r4-*`，不覆盖 r1–r3。
- 双主题 × 字号 1/1.5/2 × 宽度 320/360/392/720 按覆盖矩阵实跑，不做假笛卡尔积。
- 主要操作 ≥48dp、正文合成对比 ≥4.5:1、短屏/IME 完整可达。
- 海报卡/续播卡/分集行：整卡 `Role.Button`；封面与标题去重朗读；次级文字实色 `onSurfaceVariant`（高亮行用 `onPrimaryContainer`），不得 `onSurface.copy(alpha=…)` 叠半透明卡。
- `ContinueWatchingCard` 触控 ≥48dp；宽度随 `fontScale` 增长（新建续播卡尺寸函数或复用/扩展 `videoShelfCardSize` 策略），不得写死 240dp。
- 错误态显式 `SecondaryActionButton("重试")`，与页头刷新同源；错误且无缓存时不叠加「没有内容」。
- 保留全部真实回调：打开详情、播放/从头播、分集播放、库切换、搜索/筛选、刷新、配置重置清理。
- 编译、完整 JVM、Lint、Debug/Release/测试 APK、专用模拟器 UI 测试通过。
- 版本 0.1.11 / 11 → 0.1.12 / 12，签名延续；Release 排除 Debug 样板。
- 新增 `.trellis/spec/backend/video-ui.md`（本轮只写浏览/详情章），同步 `ui-consistency.md` 业务扩展、DESIGN/CHANGELOG。
- 单次确认提交，不 push。

## Acceptance Criteria (evolving)

- [x] 4 个条目已接入生产入口与 Debug 样板；专用模拟器截图/交互因当前无 ADB 设备未执行。
- [x] `VideoHomeContent` 生产与样板共用；`VideoDetailScreen` 样板直连。
- [x] 代码级语义、48dp、对比度实现与单测/AndroidTest 编译通过。
- [x] 真实回调保留；样板仅使用内存数据和 `preview://` 地址，不进网络层。
- [x] 全量 JVM、Lint、Debug/Release 构建通过；版本 0.1.12 / 12。
- [x] `video-ui.md`、DESIGN、CHANGELOG 与索引同步；工作提交待用户确认。

## Definition of Done

- 单测覆盖：续播卡尺寸、详情 `isCurrent` 解析、作者/次级文字合同若抽了纯函数。
- instrumentation：新增视频浏览卡片、搜索空态、剧集当前集交互；已完成 AndroidTest 编译，r4 截图矩阵因无专用设备未执行。
- Lint / 全量 JVM 通过。
- `video-ui.md` 新增并同步 `ui-consistency.md`。
- 提交计划经用户确认后一次工作提交，不 push。

## Out of Scope

- 视频播放器、7 个窗内面板、连播倒计时、PiP、横竖屏（第 5 轮）。
- WebDAV 目录/路径/收藏/文件信息（第 6 轮）。
- 设置域 16 页及弹层（第 7 轮）；本轮不改「视频播放」设置页。
- 新增业务：不造浏览页排序 UI、不改 Emby API/缓存/Resume 协议。
- 真实服务器/个人设备验收；TalkBack 完整路径。
- 更换字体/依赖、重做导航、把窗内 panel 迁到 `MediaPlayerSheet`。
- Hero overlay 白字（已记录例外）。

## Technical Notes

- 关键文件：`VideoScreen.kt`、`VideoBrowseComponents.kt`、`VideoDetailScreen.kt`、`VideoScreenLogic.kt`、`app/src/debug/.../UiCatalogSamples.kt`、新建 `VideoCatalogSamples.kt`、`app/src/test/.../VideoScreenTest.kt`。
- 前轮方法论文档：`.trellis/tasks/archive/2026-09/09-15-ui-polish-audiobook/research/implementation-plan.md`、`09-14-ui-polish-music/research/`（`run-ui-checks.py` 等可复制改造）。
- 规范：`ui-consistency.md`、`ui-catalog-verification.md`、`audiobook-ui.md`（对照）、`emby-integration.md`（不得改协议）、`quality-guidelines.md`、`build-release.md`。
- 源码已见问题清单见 `research/implementation-plan.md`。
