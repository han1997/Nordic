# 继续打磨音乐模块遗留项

## Goal

延续 08-15 任务，处理上一轮标记为 Out of Scope 的三个遗留打磨项，让音乐模块的歌词面板、队列拖动重排、Sheet 展开行为更贴近主流音乐 App 的成熟手感。

## What I already know

* 上一轮（08-15）已完成三批次 UI 体感打磨（播放页手势/收藏提示/队列交互/浏览视觉层级），并归档。
* 上一轮 Out of Scope 中明确遗留的三个方向：
  1. **歌词面板滚动对齐与高亮稳定度**（`MusicPlayerScreen.kt` `PlayerLyricsDisplay` + `selectVisibleLyricLines`）：当前歌词面板用 `Column` + `forEach` 静态渲染可见行，无滚动、无平滑高亮过渡；`selectVisibleLyricLines` 以 `drop(startIndex).take(maxLineCount)` 硬切窗口，行切换时无动画。
  2. **拖动重排实时让位**（`MusicQueueSheet.kt` `QueueRow`）：当前拖动仅做"抬起态"视觉反馈（scaleY/alpha 微变 + 跟手平移），松手后才提交 `onMoveQueueItem`；不做实时让位。上一轮 PRD 标注依赖 Compose Foundation 1.7+ `animateItem()`，本项目 BOM 2024.01.00 / 1.6.x 不升级。
  3. **Sheet 展开两段式**（`MusicQueueSheet.kt` + `MusicEqualizerSheet.kt`）：两个 Sheet 均用默认 `ModalBottomSheet`，无 `skipPartiallyExpanded`；上一轮标注需统一处理，避免破坏 Sheet 间一致性。
* 代码结构：
  - `MusicPlayerScreen.kt` ≈ 1092 行，`PlayerLyricsDisplay` 在 525 行，`selectVisibleLyricLines` 在 993 行。
  - `MusicQueueSheet.kt` ≈ 602 行，`QueueRow` 在 336 行，拖动手势在 352 行。
  - `MusicEqualizerSheet.kt` ≈ 264 行，`ModalBottomSheet` 在 86 行。
* 设计系统 `DESIGN.md`：Panel Shadow（`shadowElevation: 6dp`）用于歌词面板；Flat-at-Rest（卡片/行无阴影）；FastOutSlowInEasing 唯一缓动；fadeIn(300)/fadeOut(200)。
* Compose BOM 2024.01.00 / Foundation 1.6.x，不升级。

## Assumptions (temporary)

* 三个遗留项可独立实现，无强依赖关系。
* 拖动实时让位在不升级 BOM 的前提下，用手动 `graphicsLayer { translationY }` 给被拖行和受影响行加偏移动画实现。
* Sheet 两段式需同时改 `MusicQueueSheet` 和 `MusicEqualizerSheet` 以保持一致性。

## Requirements

### 子方向 1 — 歌词面板滚动对齐与高亮过渡

* **1.1 歌词面板改为 LazyColumn 自动滚动**（替换当前 `Column` + `forEach` 静态渲染）：
  - 用 `LazyColumn` 渲染歌词行，固定高度容器（当前 `Box` 已有固定高度语义）。
  - 当前行变化时用 `listState.animateScrollToItem(activeIndex)` 平滑滚动对齐，使高亮行居中。
  - 首次加载歌词时用静默 `scrollToItem` 定位（避免一开场播放动画），与队列 Sheet 首屏定位逻辑一致。
  - 非同步歌词（普通歌词）不滚动，仅显示前 N 行（保持当前行为）。
  - 缓动用 `NordicMotion` 现有 spec。

* **1.2 高亮行颜色 + 字重平滑过渡**（替换当前硬跳）：
  - 高亮行和非高亮行字号固定统一（compact 16sp / 非 compact 18sp），不再用字号区分。
  - 高亮行：`onSurface` 全色 + `FontWeight.Bold`；非高亮行：`onSurface.copy(alpha = NordicAlpha.subtle)` + `FontWeight.Medium`。
  - 颜色和字重用 `animateColorAsState` / `animateFloatAsState`（字重通过 `FontWeight.weight` float 值插值）平滑过渡，`tween(NordicMotion.durationShort, easingStandard)`。
  - 不用字号变化区分高亮，避免行布局抖动。

### 子方向 2 — 拖动重排实时让位

* **2.1 拖动过程中实时让位**（替换当前"仅抬起态 + 松手后提交"）：
  - 被拖行跟手平移（`graphicsLayer { translationY }`），保持当前抬起态视觉（scaleY/alpha 微变）。
  - 拖动过程中根据被拖行的累积位移计算目标插入位置，给受影响的其他行加 `graphicsLayer { translationY }` 偏移动画，实时让出空间。
  - 松手时以 `tween` 平滑归位后提交 `onMoveQueueItem`。
  - 不引入阴影（遵循 Flat-at-Rest；抬起态是交互反馈，不是装饰阴影）。
  - 不使用 `animateItem()` / `animateItemPlacement()`（1.7+ API，本项目不升级 BOM）。
  - 实现方式：在 `MusicQueueSheet` 的 `LazyColumn` 外层维护一个 `dragState`（被拖行 index + 累积位移），各 `QueueRow` 读取 `dragState` 计算自身偏移。

* **2.2 保持现有抬起态反馈**：
  - 长按起拖时被拖行进入抬起态（scaleY/alpha 微变），与上一轮一致。
  - 松手时清除抬起态。

### 子方向 3 — Sheet 两段式展开

* **3.1 队列 Sheet 和均衡器 Sheet 都启用两段式**：
  - `ModalBottomSheet(skipPartiallyExpanded = false)`，Sheet 先展开到中间态（部分可见），用户上拉可完全展开。
  - 队列 Sheet 中间态显示前几行 + 当前播放高亮。
  - 均衡器 Sheet 中间态显示预设选择区域。
  - 两个 Sheet 行为一致，保持 Sheet 间一致性。
  - `sheetMaxHeight` 统一设置（如 `1f` 即全屏高度，或 `0.9f` 留顶部空间）。

## Acceptance Criteria

### 子方向 1 — 歌词面板
* [ ] 1.1 歌词面板用 `LazyColumn` 渲染，当前行变化时 `animateScrollToItem` 平滑滚动对齐；首次加载用静默 `scrollToItem`。
* [ ] 1.1 非同步歌词不滚动，仅显示前 N 行（保持当前行为）。
* [ ] 1.2 高亮行颜色 + 字重用 `animateColorAsState` / `animateFloatAsState` 平滑过渡；字号固定统一，不再用字号区分高亮。
* [ ] 1.2 缓动用 `tween(NordicMotion.durationShort, easingStandard)`；不出现装饰性弹跳。

### 子方向 2 — 拖动重排实时让位
* [ ] 2.1 拖动过程中被拖行跟手平移，其他行根据被拖行位置实时动画位移让出空间。
* [ ] 2.1 松手时以 `tween` 平滑归位后提交 `onMoveQueueItem`。
* [ ] 2.1 不使用 `animateItem()` / `animateItemPlacement()`（与 BOM 2024.01.00 / Foundation 1.6.x 对齐）。
* [ ] 2.2 保持现有抬起态反馈（scaleY/alpha 微变，无阴影）。

### 子方向 3 — Sheet 两段式
* [ ] 3.1 队列 Sheet 和均衡器 Sheet 都启用 `skipPartiallyExpanded = false`。
* [ ] 3.1 两个 Sheet 行为一致，中间态可部分可见，上拉完全展开。

### 全局
* [ ] `MusicPlayerScreenTest` / `MusicScreenV2Test` 等既有测试保持绿色；新增改动点补对应行为测试。
* [ ] 遵循 `DESIGN.md` 全部 Named Rules（Flat-at-Rest、Accent Scarcity、Translucency、Native Font、FastOutSlowInEasing-only）。
* [ ] lint / typecheck / 构建通过。
* [ ] `CHANGELOG.md` `[未发布]` 段补充本次打磨条目。

## Definition of Done (team quality bar)

* Tests added/updated（unit/instrumentation where appropriate）
* Lint / typecheck / 构建绿
* Docs/notes 更新（行为变化时）
* 风险点：歌词滚动与拖动实时让位在低端机上的表现需人工回归

## Out of Scope (explicit)

* 状态管理分层重构、大文件拆分、播放引擎重写（同上一轮）。
* 新增服务、新页面、新大功能。
* 跨媒体类型联动。
* 网络层 / 缓存层重构。
* 升级 Compose BOM（保持 2024.01.00 / Foundation 1.6.x）。
* 删除撤销 Toast（引入新事件通道，超出本轮范围）。
* 歌词面板手动滚动/拖动定位（用户不可手动滚动歌词，仅自动跟随播放进度）。
* 歌词面板卡拉 OK 逐字高亮（逐字级别高亮超出本轮范围）。

## Technical Approach（关键决策）

* **歌词面板 LazyColumn 滚动**：把 `PlayerLyricsDisplay` 内部从 `Column` + `forEach` 改为 `LazyColumn` + `itemsIndexed`；引入 `rememberLazyListState()`，在 `LaunchedEffect(activeIndex)` 中首次 `scrollToItem`、之后 `animateScrollToItem`。高亮过渡用 `animateColorAsState` + `animateFloatAsState`（字重 weight float 插值）。
* **拖动实时让位**：在 `MusicQueueSheet` 的 `LazyColumn` 外层维护 `dragState: DragState`（`data class DragState(val draggedIndex: Int, val accumulatedPx: Float)`），各 `QueueRow` 读取 `dragState` 计算自身 `translationY`：被拖行跟手平移 + 抬起态；其他行根据与被拖行的相对位置和累积位移计算让位偏移（`animateFloatAsState` 平滑过渡）。松手时 `tween` 归位后提交 `onMoveQueueItem`。
* **Sheet 两段式**：`ModalBottomSheet(skipPartiallyExpanded = false)`，两个 Sheet 统一设置。

## Implementation Plan (small PRs / 分批落地)

* **批次 1 — 歌词面板（子方向 1）**：1.1 LazyColumn 自动滚动；1.2 高亮颜色 + 字重平滑过渡。
* **批次 2 — 拖动实时让位（子方向 2）**：2.1 实时让位 + 抬起态保持；2.2 松手平滑归位。
* **批次 3 — Sheet 两段式（子方向 3）**：3.1 队列 Sheet + 均衡器 Sheet 启用两段式。
* **批次 4 — 验收收尾**：补/改测试；`CHANGELOG.md` `[未发布]` 段补打磨条目；最终人工手感回归。

## Technical Notes

* 所有缓动必须用 `FastOutSlowInEasing`；过渡用 fadeIn(300)/fadeOut(200)。
* 卡片/行无阴影；阴影仅用于 Dock、全屏播放封面、歌词面板、播放按钮。
* 按压反馈 0.985x@150ms（图标/头部按钮 0.94x）。
* pill(999dp) 是按钮、chip、nav、meta 的统一形状。
* 列表入场延迟 delay(index * 50ms)。
* `IMPLEMENT_REFERENCES`：详见 `DESIGN.md`、`CHANGELOG.md`、上述源文件清单。
