# Compose 流畅度与滑动优化

## Goal

优化 Nordic Android 应用的视觉流畅度，让所有页面的切换、列表滚动、播放进度更新更顺滑、不卡顿、不掉帧。分两阶段：先解决音乐页真实帧率瓶颈（结构性拆分 + 播放进度重组合隔离），再做覆盖全页面的视觉/动效打磨。

## 版本基线（关键约束，来自 research/compose-fluidity.md）

项目锁定 `compose-bom:2024.01.00` → **Compose 1.6.0 / Material3 1.2.0 / Foundation 1.6.0**，Kotlin 1.9.20，Coil 2.5.0。以下 API **在本版本不可用**，必须采用等价替代：

- `androidx.compose.foundation.Scrollbar` → 仅 Compose Multiplatform 桌面端，**Android 无原生滚动条**。需自建滚动条（基于 `LazyListState` + `derivedStateOf`）。
- `MaterialTheme.motionScheme` → Material3 1.5.0 才有。保留现有 `NordicMotion`，不引入。
- `PullToRefreshBox` → Material3 1.3.0 才有。若需下拉刷新，用 M2 `androidx.compose.material.pullrefresh` 或保持现有 ↻ 按钮（不升 BOM）。
- `rememberOverscrollEffect()` → 1.8.0 才有。保持 Lazy 默认边缘 overscroll 即可。

**不升级 BOM**（属上一任务明确排除项）。

## Requirements

### Phase A — 性能 / 重组合重构（仅音乐页）

- **A1. 拆分 `MusicScreenV2`**：把 1000+ 行巨型 Composable 按页面（`Home` / `Albums` / `Songs` / `Artists` / `ArtistDetail` / `AlbumDetail` / `Search` / `Playlists` / `PlaylistDetail`）提取为独立 `@Composable` 子组件（可放同文件或 `MusicScreenV2Pages.kt`）。保留现有全部状态、导航、回退栈、缓存/刷新逻辑与行为，仅缩小根作用域重组合范围。
- **A2. 隔离 `positionMillis`**：`MusicPlaybackViewModel.positionMillis` 为 100ms tick 的 `StateFlow<Long>`。当前 `MainActivity` 顶层 `collectAsStateWithLifecycle` 后作为 `Long` 参数下传，导致 `MusicPlayerScreen` 整棵子树每 100ms 重组合。改为：传递 `StateFlow<Long>`（稳定引用）或 ViewModel，在真正需要它的叶子（`PlayerPrimaryDisplay` 进度条、`PlayerLyricsDisplay` 歌词列表）内部 `collectAsStateWithLifecycle`，避免祖先重组合。
- **A3. 派生值用 `derivedStateOf`**：歌词当前行 `activeIndex` 等由 `positionMillis` 派生、变化频率远低于 tick，用 `derivedStateOf` 包装，仅跨行时驱动重组合；`remember` key 不含快速变化的 `positionMillis`。
- **A4. `@Stable` 标注模型类**：编译期 1.5.4 无 strong-skipping。对 `NavidromeSong` / `MusicLyrics` / `MusicLyricsLine` 等下传的数据类标注 `@Stable`，减少不必要重组合。
- **A5（可选）**：若 100ms 精度对进度 UI 非必需，将 `POSITION_MILLIS_SAMPLE_INTERVAL_MS` 提高到 ~250ms，进一步降低重组合频率（隔离后非必须）。

### Phase B — 视觉 / 动效打磨（覆盖所有页面）

- **B1. 图片 crossfade + 占位**：`AuthedAsyncImage` 的 `ImageRequest` 增加 `.crossfade(200)` 与 `placeholder`，消除封面加载闪现（pop-in）。统一 Coil `ImageLoader` 注入 `MediaAuthHeaderInterceptor`（若封面走 header 鉴权）。
- **B2. 自建滚动条**：封装一个轻量滚动条 Composable，基于 `LazyListState` + `derivedStateOf` 仅在内容可滚动时显示，仅滚动条自身重组合。应用于长列表（歌曲列表、歌词、队列等）。
- **B3. 动效统一到 `NordicMotion`**：新过渡/入场动画复用 `NordicMotion` 时长与缓动（enter 用 `easingDecelerate`、exit 用 `easingAccelerate`）。手势驱动/连续动效（拖拽释放、展开/收起）优先用 `spring`，离散 enter/exit 用 `tween`。`AnimatedContent` 尺寸变化配合 `SizeTransform` 防跳动。
- **B4. 滚动手感（可选、低风险）**：长 `LazyColumn` 可酌情采用 `ScrollableDefaults.flingBehavior(exponentialDecay(frictionMultiplier = 1.6f))` 让 fling 更跟手；需测试 `LazyRow` 嵌套不滞涩。默认 fling 也可保持不变。
- **B5. 下拉刷新（如适用）**：若现有刷新为 accompanist `SwipeRefresh` 或不顺，迁移到 M2 `androidx.compose.material.pullrefresh`；否则保持现有 ↻ 按钮机制。

## Acceptance Criteria

- [ ] A1：`MusicScreenV2` 按页面拆分，编译通过，导航/回退/缓存/刷新行为不变（人工 + 测试验证）
- [ ] A2：`MainActivity` 不再因 `positionMillis` tick 重组合 `MusicPlayerScreen` 整树（叶子内部收集）
- [ ] A3：歌词 `activeIndex` 用 `derivedStateOf`，仅跨行时重组合
- [ ] A4：相关模型类标注 `@Stable`
- [ ] A 阶段：`.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest` 与 `:app:lintDebug` 均绿色
- [ ] B1：`AuthedAsyncImage` 带 crossfade + placeholder，封面不再 pop-in
- [ ] B2：长列表显示自建滚动条，仅滚动条自身重组合
- [ ] B3：新增过渡动效复用了 `NordicMotion`，风格一致
- [ ] B 阶段：compile + test + lint 均绿色，无回归
- [ ] 主观确认：音乐页播放时歌词/进度流畅、各页面滚动与切换顺滑

## Definition of Done

- 两阶段均通过快速验证与完整验证（含 lint）
- 无回归（现有测试/交互正常）
- 关键重组合热路径已隔离（perf 阶段）
- 若产生可复用约定（如滚动条 Composable、`positionMillis` 隔离模式），更新 `.trellis/spec/`

## Decision (ADR-lite)

**Context**：用户要求"所有页面、滑动更流畅"。上一项 PRD 已将"拆分 MusicScreenV2、隔离 positionMillis"列为下一个任务；代码证实 `MusicScreenV2` 为 1000+ 行巨型 Composable，`positionMillis` 在 `MainActivity` 顶层收集后下传导致整树每 100ms 重组合。

**Decision**：
1. 性能重构只动音乐页（风险聚焦），采用彻底拆分（按页面子组件）+ `positionMillis` flow 下沉到叶子。
2. 视觉打磨覆盖所有页面；因 BOM 锁定，全部采用版本内等价方案（自建滚动条、Coil crossfade、复用 `NordicMotion`），不升级依赖。

**Consequences**：
- 正面：真实掉帧（播放进度、整页重组合）消除；全页面视觉顺滑度提升。
- 负面/风险：A1 结构性拆分改动大，需保证行为不变（靠现有测试 + 人工验证）；自建滚动条需正确映射到 `LazyListState`；不升 BOM 意味着无法用更新更顺的官方 API。

## Out of Scope

- 升级 Gradle / AGP / Kotlin / Compose BOM 版本
- 视频页 / 有声书页的结构性拆分（其视觉打磨仍在本任务 B 阶段覆盖）
- 引入新动画框架（Lottie 等）或重架构
- lint baseline 或 lint 规则改动

## Technical Notes

- 关键文件：`ui/MusicScreenV2.kt`（拆分）、`ui/MusicPlayerScreen.kt`（叶子收集 positionMillis）、`playback/MusicPlaybackViewModel.kt`（StateFlow 源）、`MainActivity.kt`（顶层收集点）、`ui/AuthedAsyncImage.kt`（crossfade）、`ui/theme/Motion.kt`（动效令牌）、`ui/PlaybackDock.kt`（进度 UI）。
- Compose 性能要点：`derivedStateOf`、`@Stable`、状态下沉到最窄叶子、列表 `key`+`contentType`（已具备）。
- 版本约束见上"版本基线"，详细模式与代码片段见 `research/compose-fluidity.md`。

## Research References

- [`research/compose-fluidity.md`](research/compose-fluidity.md) — 版本审计 + 状态隔离 / 滚动 / Coil / 动效 / 下拉刷新 的版本内可行方案与 do-don't。
