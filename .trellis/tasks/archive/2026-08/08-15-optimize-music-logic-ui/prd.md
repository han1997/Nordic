# 优化音乐部分逻辑与 UI

## Goal

延续前序 UI 交互打磨任务（08-11 系列），继续提升音乐模块的**界面体感**：让浏览、播放、队列等高频路径的交互流畅度、视觉层级、动画细节、空/错/加载态以及播放器手势体验更贴近主流音乐 App 的成熟手感，减少生硬跳变与状态残留。

本次聚焦 UI 体感打磨，不做架构性重构（状态管理分层、文件拆分、播放引擎重写均在本任务 Out of Scope）。

## What I already know

* 项目是 Android Kotlin + Jetpack Compose + Material 3 客户端，音乐数据来自 Navidrome 自托管服务器。
* 设计系统 `DESIGN.md` 已固化：Listening Room 美学、Alpha-as-depth、Pill geometry、FastOutSlowInEasing 唯一缓动、press-scale 0.985x、fadeIn(300ms)/fadeOut(200ms)、列表入场 delay(index * 50ms)。
* 近期 CHANGELOG 已落地的音乐相关改进：
  - 播放页布局贴近主流 App（矢量图标、细线进度条 + 小 thumb、随机/上一首/播放暂停/下一首/循环，单曲循环显示 "1" 角标，标题艺人移到封面下方，顶栏下拉箭头 + "正在播放"，♥ 收藏乐观更新，下滑关闭，封面背景毛玻璃加深）。
  - 进度条拖动按手指绝对位置稳定跟随，取消不误触发 seek；新增 10 秒后退 / 30 秒前进控制。
  - 队列 Sheet 上移/下移/拖动排序真实更新播放队列。
  - 搜索框一键清除、空查询取消待执行搜索并恢复搜索落地页。
  - 详情页返回尽量回到来源页；刷新专辑/歌手/歌单后修正已打开详情；配置切换回首页有轻量说明。
  - 共享加载卡片新增轻量进度线。
  - 底部 Dock 滚动/惯性停止后不再自动出现，改为隐藏时显示低干扰小把手，点击恢复；切 tab / 关播放器仍自动恢复。
* 代码结构现状（本任务只读、不重构，仅按需在 UI 内做见机调整）：
  - `MusicScreenV2.kt` ≈ 1672 行，承载音乐库浏览（Home/Albums/Songs/Artists/Playlists/Search）。
  - `MusicPlayerScreen.kt` ≈ 875 行，全屏播放页（封面、控件、进度、歌词、手势关闭）。
  - `MusicBrowseComponents.kt` ≈ 729 行，专辑/歌手/歌单/歌曲行等浏览组件。
  - `MusicHomeSections.kt` ≈ 424 行，首页分区（最近添加、最近播放、推荐）。
  - `MusicQueueSheet.kt` ≈ 489 行，播放队列底部 Sheet。
  - `MusicEqualizerSheet.kt` ≈ 256 行，均衡器 Sheet。
  - `PlaybackDock.kt` ≈ 347 行，底部持久 Now Playing + 导航 Dock。
  - `MusicScreenLogic.kt` ≈ 200 行，纯函数化的导航/筛选/排序/刷新解析逻辑。
  - 播放层 `MusicPlaybackEngine.kt` ≈ 644 行、`MusicPlaybackViewModel.kt` ≈ 126 行（本任务不重写，仅必要时在 UI 侧补正反馈）。

## Assumptions (temporary)

* "UI 体感打磨"指交互手感与视觉一致性的细节优化，不涉及状态管理架构重构与播放引擎重写。
* 不新增大功能（如新页面、新服务接入），仅打磨既有路径。
* 改动应遵循 `DESIGN.md` 的 Do/Don't（不得引入阴影卡片、弹性动画、glassmorphism 滥用、渐变文字等禁止项）。
* UI 改动以现有测试（`MusicScreenV2Test` / `MusicPlayerScreenTest`）不回归为底线，必要时补充行为级测试。

## Open Questions

* 见下方提问（一次一个）。

## Requirements (evolving)

* 本轮覆盖三个子方向：
  1. **播放页手势与控件细节**（`MusicPlayerScreen.kt`、`MusicQueueSheet.kt` 边界）：跟手性、跳转按钮可发现性、收藏失败回退 UX、单曲循环角标细节、歌词面板对齐与高亮稳定度。
  2. **队列 Sheet 交互**（`MusicQueueSheet.kt`）：拖动热区/阻尼、当前项高亮+滚动定位动画、删除入口的存在性、展开高度的两段式接驳。
  3. **音乐库浏览视觉层级**（`MusicScreenV2.kt`、`MusicHomeSections.kt`、`MusicBrowseComponents.kt`）：分区间距/节标题、卡片圆角留白一致性、筛选 chip 视觉/选中态、空/错/加载态统一度。

### 子方向 1 — 播放页手势与控件细节（确定范围：1.1 + 1.2 + 1.3）

* **1.1 下滑关闭手势加渐进跟随与取消回弹**（替换当前 `MusicPlayerScreen.kt` 中 6px 即触发 `onClose()` 的粗暴实现）：
  - 拖动过程中封面/内容随手指 `offset.y` 平移 + 轻微缩放（如 0.96x），背景 alpha 随进度衰减。
  - 松手时越过阈值（约 `maxHeight * 0.25`）才以 `tween` 安全区回弹到关闭，否则用 spring 回到原位。
  - 缓动统一用 `FastOutSlowInEasing`；spring 仅用于回弹物理，非装饰性弹性（不违反 DESIGN.md Don't 的"bounce/elastic/spring 动画曲线"——该 Don't 针对装饰性弹跳，回弹物理是手势反馈例外）。
  - 起手区不再限制"上半屏"，改为整屏可起手（更符合主流音乐 App 习惯），但保留对内部可滚动区域（如有）的让位语义。

* **1.2 收藏失败回退加可视提示**（当前 `MusicPlaybackViewModel.toggleFavorite` 失败已 `setCurrentSongStarred(!starred)` 静默回滚，用户体感"跳回去"）：
  - 回滚失败时在播放页顶层叠一个短促提示（2s 自动消失），文案如"收藏操作失败，已恢复"。
  - 不引入新组件库；用 `AnimatedVisibility` + `Surface`(pill) 自绘，遵循 DESIGN.md chip/Surface alpha 规则。
  - 失败信号需从 ViewModel 暴露一次性事件（如 `SharedFlow<Unit>` 或 `finishFavoriteError` 旗标），UI 收到后显示提示并清旗标。

* **1.3 单曲循环 "1" 角标像素级校正**（确认定位/字号/底色/与 primary 一致，遵循 Accent Scarcity）：
  - 仅像素级校正，不改语义、不改角标组件结构。

### 子方向 2 — 队列 Sheet 交互（确定范围：2.1 = 平滑对齐；2.2 = 抬起态高亮反馈，不做实时让位；2.3 = 退场动画；2.4 不做）

* **2.1 当前播放定位平滑对齐**（替换 `MusicQueueSheet.kt` 第 77–81 行 `LaunchedEffect` 中 `listState.scrollToItem(resolvedCurrentIndex)` 的硬跳）：
  - 首次打开 Sheet 时用静默 `scrollToItem` 定位（避免一开场播放动画），切歌后再用 `listState.animateScrollToItem(resolvedCurrentIndex)`。
  - 实现方式：引入一个 `hasInitialScrolled` 旗标，或比较 `LazyListState.firstVisibleItemIndex` 与目标是否已相邻，决定是否走动画。
  - 缓动用 `NordicMotion` 现有 spec，不引入新曲线。

* **2.2 拖动重排改"抬起态高亮反馈"**（不做实时让位，与 Compose BOM 2024.01.00 / Foundation 1.6.x 对齐；`animateItem()` modifier 在 1.7+ 才稳定，本项目不升级 BOM）：
  - 长按起拖时给被拖行加 `graphicsLayer { alpha/scaleY = 0.98~1.0 抬起态 }`（不引入阴影，遵循 Flat-at-Rest；这是交互态反馈，不是装饰阴影）。
  - 拖动期累计 `dragAmountY` 仅用于视觉上微幅抬起（不跟手平移，不实时重排）。
  - 松手时清除抬起态并以 `tween` 短促归位后提交 `onMoveQueueItem(index, targetIndex)`。
  - 不做 `Modifier.animateItem()` 实时让位。

* **2.3 删除行带退场动画**（替换当前 `onRemoveFromQueue(index)` 直接触发 LazyColumn 默认 diff 硬跳）：
  - 方案 A：给被删行包 `AnimatedVisibility`，`fadeOut` + `shrinkVertically`（FastOutSlowInEasing）退场，动画完成后由 LazyColumn 自然重排。
  - 不做撤销 Toast（不引入新事件通道与快照延时提交）。

* **2.4 不做**：Sheet 展开两段式（`skipPartiallyExpanded`）。原因：均衡器 Sheet `MusicEqualizerSheet.kt` 同样用默认 `ModalBottomSheet`，单独给队列 Sheet 引入两段式会破坏 Sheet 间一致性；若未来要做应作为单独的"Sheet 展开行为 unify"任务统一处理。

### 子方向 3 — 音乐库浏览视觉层级（确定范围：3.1 = sort 控件视觉 unify；3.2 = 卡片圆角留白一致性核对；3.3 = 空/错/加载态统一度核对）

* **3.1 sort 控件视觉 unify**（当前 `SongSortSegmentedControl` 用 `LazyRow` + pill 单项、`AlbumSortSegmentedControl` 用 `Row` + 自绘分段 weight 平均填充——视觉与交互对偶不一致）：
  - 目标：让两种 sort 控件视觉语言一致。需先在子 agent 实现 phase 决策具体 unify 方向（两种主流选择，由实现者根据 `DESIGN.md` 分段 tab 规则裁决，避免现在锁死）：
    - 选项 A：专辑 sort 也改为 `LazyRow` pill 单项（与歌曲 sort 一致）。
    - 选项 B：歌曲 sort 也改为 `Row` 自绘分段（与专辑 sort 一致）。
  - 决策依据：`DESIGN.md` `tab-segmented` 组件定义（外容器 surfaceVariant 0.56 alpha、RoundedCornerShape(18dp)、height 48dp、选中 tab surface 0.96 alpha + RoundedCornerShape(14dp)），优先把两种 sort 都对齐到这一规范。
  - 改动需同步保留排序触发的现有行为测试。

* **3.2 卡片圆角留白一致性核对**（`DESIGN.md` 指定 list row 12dp、album list row 14dp、hero banner 20dp）：
  - 通扫 `MusicHomeSections.kt` 与 `MusicBrowseComponents.kt` 中的所有卡片 `RoundedCornerShape(...)`，确认与 `DESIGN.md` alpha band / 圆角定义一致；任何越界值校正回规范。
  - 同时核对封面 `ArtworkSize` 是否符合规范（如歌曲行 52dp@12dp、专辑列表行 14dp 圆角等）。
  - 仅做校正，不重新设计卡片结构。

* **3.3 空/错/加载态统一度核对**（`MediaLoadingCard` 已是共享组件，三媒体统一在用）：
  - 核对音乐模块各页是否一致使用 `MediaLoadingCard`、是否一致使用 `MusicDetailEmptyState`（见 `MusicBrowseComponents.kt:346`），空态是否带可操作引导（如未登录引导去配置 tab，与 `CHANGELOG.md` "配置切换导致回首页显示轻量说明"延续）。
  - 仅做统一度校正，不新增大型空态插画或新组件。

## Acceptance Criteria (evolving)

### 子方向 1 — 播放页手势与控件
* [ ] 1.1 下滑手势：从整屏起手，拖动过程中封面/内容随手指平移 + 轻微缩放、背景 alpha 衰减；松手越过阈值（约 maxHeight * 0.25）才关闭并以 tween 平滑退出，否则 spring 回位；不再有"6px 即触发"。
* [ ] 1.1 缓动统一 `FastOutSlowInEasing`；回弹 spring 仅用于手势回位物理，不出现装饰性弹跳。
* [ ] 1.2 收藏失败回滚时，播放页顶层显示 2s 自动消失的 pill 提示（"收藏操作失败，已恢复"），用 `AnimatedVisibility` + `Surface` 自绘，不引入新组件库。
* [ ] 1.2 ViewModel 暴露一次性失败事件（`SharedFlow<Unit>` 或清旗标），UI 收到后显示提示并清旗标；不重复显示、不残留。
* [ ] 1.3 单曲循环 "1" 角标定位/字号/底色/与 primary 色调一致，遵循 Accent Scarcity；无语义改动。

### 子方向 2 — 队列 Sheet 交互
* [ ] 2.1 首次打开 Sheet 用静默 `scrollToItem` 定位当前播放；切歌后用 `animateScrollToItem` 平滑对齐；不再硬跳。
* [ ] 2.2 长按起拖时被拖行进入"抬起态"反馈（alpha/scaleY 微变，无阴影）；松手时清除抬起态、`tween` 短促归位后提交 `onMoveQueueItem`。
* [ ] 2.2 不引入 `animateItem()` 实时让位（与 BOM 2024.01.00 / Foundation 1.6.x 对齐）。
* [ ] 2.3 删除行用 `AnimatedVisibility`（`fadeOut` + `shrinkVertically`，FastOutSlowInEasing）退场，动画完成后 LazyColumn 自然重排；无撤销 Toast。
* [ ] 2.4 不改动 Sheet 展开行为（与 `MusicEqualizerSheet` 保持一致）。

### 子方向 3 — 浏览视觉层级
* [ ] 3.1 两种 sort 控件（`SongSortSegmentedControl` / `AlbumSortSegmentedControl`）视觉语言一致，对齐 `DESIGN.md` `tab-segmented` 规范（外容器 surfaceVariant 0.56 alpha、18dp 圆角、48dp 高、选中 surface 0.96 + 14dp 圆角）。
* [ ] 3.1 排序触发行为的现有测试保持绿色。
* [ ] 3.2 `MusicHomeSections.kt` / `MusicBrowseComponents.kt` 中所有卡片圆角与封面尺寸与 `DESIGN.md` 规范一致（list row 12dp、album list row 14dp、hero 20dp、歌曲行 52dp 封面等）。
* [ ] 3.3 各页加载态一致使用 `MediaLoadingCard`，空态一致使用 `MusicDetailEmptyState` 并带可操作引导；错误态文案与 `MusicScreenLogic.kt` 中 `musicAlbumDetailLoadErrorMessage` / `musicArtistDetailLoadErrorMessage` 一致。

### 全局
* [ ] `MusicPlayerScreenTest` / `MusicScreenV2Test` 等既有测试保持绿色；新增改动点补对应行为测试。
* [ ] 遵循 `DESIGN.md` 全部 Named Rules（Flat-at-Rest、Accent Scarcity、Translucency、Native Font、FastOutSlowInEasing-only）。
* [ ] lint / typecheck / 构建通过。
* [ ] `CHANGELOG.md` `[未发布]` 段补充本次打磨条目。

## Definition of Done (team quality bar)

* Tests added/updated（unit/instrumentation where appropriate）
* Lint / typecheck / 构建绿
* Docs/notes 更新（行为变化时）
* 风险点：手势与播放器状态变化在低端机上的表现需人工回归

## Out of Scope (explicit)

* 状态管理分层重构（抽 ViewModel / state holder）— 属上一方向 1。
* `MusicScreenV2.kt` / `MusicPlayerScreen.kt` 等大文件按页面拆分 — 属上一方向 2。
* 播放引擎重写（`MusicPlaybackEngine.kt`）— 属上一方向 4。
* 新增服务、新页面、新大功能。
* 跨媒体类型联动（音乐 ↔ 有声书/视频）。
* 网络层 / 缓存层重构。
* **子方向 1 内部**：歌词面板滚动对齐与高亮稳定度（边界条件多，留下次 polish）；10s/30s 跳转按钮可发现性调整（现状已可用）；播放页整体布局重构。
* **子方向 2 内部**：拖动重排实时让位（依赖 Compose Foundation 1.7+ `animateItem()`，本项目 BOM 2024.01.00 / 1.6.x 不升级）；删除撤销 Toast（引入新事件通道，超出本轮范围）；Sheet 展开两段式（需与均衡器 Sheet 一致 unify，留独立任务）。
* **子方向 3 内部**：新增大型空态插画或新组件；卡片结构重新设计。

## Technical Notes

* 所有缓动必须用 `FastOutSlowInEasing`；过渡用 fadeIn(300)/fadeOut(200)。
* 卡片/行无阴影；阴影仅用于 Dock、全屏播放封面、歌词面板、播放按钮。
* 按压反馈 0.985x@150ms（图标/头部按钮 0.94x）。
* pill(999dp) 是按钮、chip、nav、meta 的统一形状。
* 列表入场延迟 delay(index * 50ms)。
* `IMPLEMENT_REFERENCES`：详见 `DESIGN.md`、`CHANGELOG.md`、上述源文件清单。

## Technical Approach（关键决策）

* **下滑手势**：用 `Modifier.pointerInput` + `detectVerticalDragGestures`，把 `offset.y` 累积到 `mutableFloatStateOf`，传给 `Box` 的 `graphicsLayer { translationY / scaleX/Y / alpha }`；松手时根据累积值与阈值比较，决定 `animate*` 关闭或回位。
* **收藏失败可视提示**：`MusicPlaybackViewModel` 增加 `favoriteError: SharedFlow<Unit>`（replay 0），`toggleFavorite` 的 catch 块 `emit(Unit)`；播放页用 `LaunchedEffect` 收集并临时显示自绘 pill。
* **队列定位动画**：`val hasInitial = remember { mutableStateOf(false) }`，首次走静默、之后走 `animateScrollToItem`。
* **删除退场**：用 `AnimatedVisibility(visible = !removingIds.contains(index))` 包 `QueueRow`，`removingIds` 是 `mutableStateMapOf`；动画完再做真实 `onRemoveFromQueue`。
* **sort unify**：实现者优先以 `AlbumSortSegmentedControl` 自绘分段为基准（已贴合 DESIGN `tab-segmented`），把 `SongSortSegmentedControl` 从 LazyRow pill 改成同款自绘分段；若歌曲数过多仍可用 LazyRow 分段，但视觉规则一致。
* **不升级 BOM**：本轮不动 `app/build.gradle.kts` 的 Compose BOM 版本，所有手势/动画/视觉改动在 BOM 2024.01.00 / Foundation 1.6.x 内实现。

## Implementation Plan (small PRs / 分批落地)

* **批次 1 — 播放手势与控件（子方向 1）**：1.1 下滑手势渐进跟随回弹；1.2 收藏失败 pill 提示 + ViewModel 事件；1.3 单曲循环角标像素校正。
* **批次 2 — 队列 Sheet（子方向 2）**：2.1 定位平滑对齐（静默首屏 + 动画切歌）；2.2 拖动抬起态反馈；2.3 删除退场动画。
* **批次 3 — 浏览视觉层级（子方向 3）**：3.1 sort 控件 unify 到 DESIGN `tab-segmented`；3.2 卡片圆角/封面尺寸核对校正；3.3 空/错/加载态统一度核对。
* **批次 4 — 验收收尾**：补/改测试；`CHANGELOG.md` `[未发布]` 段补打磨条目；最终人工手感回归。
