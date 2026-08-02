# 优化滑动卡顿 (Optimize scroll jank)

## Goal

优化 Nordic MediaHub Android app 中用户感知到的滑动卡顿。需要先定位卡顿出现的具体界面与症状，再针对性优化。

## What I already know

- App 主滚动表面（均为 Jetpack Compose）:
  - `MusicScreenV2.kt` (1208 行) — 主 `LazyColumn` (line 543) 内 `when(libraryPage)` 9 个分支: Home(多个 LazyRow 货架)、Albums 列表、Songs 列表、Artists 列表、AlbumDetail 歌曲、ArtistDetail 专辑、Playlists 列表、PlaylistDetail 歌曲、Search。所有列表已带 `key` + `contentType`（T6 已修）。
  - `VideoScreen.kt` (374 行) — `LazyVerticalGrid` (line 189) 浏览网格；`VideoDetailScreen.kt` LazyColumn 详情。已带 key+contentType。
  - `AudiobookScreen.kt` (644 行) — `LazyColumn` (line 233) 有声书列表；`LazyRow` (line 460) 库选择器。已带 key+contentType。
  - `MusicQueueSheet.kt` — 队列 LazyColumn。已带 key+contentType。
- T6 (已完成) 修复的 Compose 性能问题: searchJob 重组、有声书章节每 tick 排序、VideoStatusTone 字符串相等、保存/Effect 竞态、均衡器 LazyRow key。
- T7 (已完成) 拆分了 MusicScreenV2/VideoScreen 巨型文件、提取共享组件。
- 代码审查中**尚未修复**的 Compose 性能 finding:
  - **M-visiblePosition 顶层读取**: `MusicPlayerScreen:76,157` / `AudiobookPlayerScreen:62,71` / `VideoPlayerScreen:78,156` — 播放位置在屏顶读取 → 整个播放器屏每 tick 重组（顶栏/封面无需位置）。这是**播放器**重组问题，非列表滚动问题，但用户感知可能是"卡顿"。
  - **minor 重组**: `metaText()` / `detailChips()` 每组合 `buildList` 分配 (`VideoScreenLogic` / `VideoPlayerScreen:135`)。
  - **频段滑块全行重组**: `MusicEqualizerSheet` — 部分已修。
- 图片加载: 列表行用 `CoverArt`（T7 提取）→ `AuthedAsyncImage`（Coil + 自定义拦截器注入认证头）。图片可能未按行尺寸下采样。

## Assumptions (temporary)

- "滑动卡顿"指列表/网格滚动时的掉帧，而非播放器进度条拖动卡顿（待确认）。
- 卡顿可能与图片加载、重组范围过大、或主线程阻塞有关。

## Diagnosis (root cause)

用户确认"全部都卡" → 系统性问题，非单屏问题。代码审查定位到**根因**:

### 根因 1: `MainScreen` 每秒重组 → 级联到全部 Tab 内容
`MainActivity.kt:246-248` 在 `MainScreen` 顶层 collect 三个播放 StateFlow:
```kotlin
val playbackState by musicVM.state.collectAsStateWithLifecycle()        // 每秒 emit (position)
val audiobookPlaybackState by audiobookVM.state.collectAsStateWithLifecycle()  // 每秒 emit
val videoPlaybackState by videoVM.state.collectAsStateWithLifecycle()   // 每秒 emit
```
- 任一引擎播放时，`MusicPlaybackState.positionSeconds` / `AudiobookPlaybackState.positionSeconds` / `VideoPlaybackState.positionSeconds` 每秒更新 → `MainScreen` 每秒重组
- `MainScreen` 重组时重新创建 `onSongSelected` / `onPlayAudiobook` / `onPlayVideo` lambda（捕获了局部 `fun closeAudiobookPlayback(...)` 等每次新建的局部函数）→ 传入 `MusicScreenV2` / `AudiobookScreen` / `VideoScreen` 的参数"变了" → 整个 Tab 屏重组 → 其 `LazyColumn`/`LazyVerticalGrid` 全部可见行重组
- 滚动时叠加每秒重组 → 掉帧

### 根因 2: 无 `crossfade` + 无占位 → 图片加载视觉卡顿
`MainActivity.kt:198-209` 的 `ImageLoader` 未启用 `crossfade(true)`；`AuthedAsyncImage` 无 placeholder。滚动时图片瞬间弹出 + 布局位移 → 感知卡顿。

### 根因 3: 每行派生字符串每次组合重新分配
`VideoItem.metaText()` / `detailChips()`（VideoScreenLogic）每组合 `buildList` 分配；`formatDuration` 调用未 `remember`。快速滚动时 GC 压力。

## Open Questions

(已收敛)

## Requirements

- 隔离播放状态收集：`MainScreen` 顶层不再 collect 三个播放 StateFlow；改为在 dock/player 子组合层收集，使 Tab 内容不受每秒 position emit 影响
- 稳定化 Tab 内容 lambda：`onSongSelected`/`onPlayAudiobook`/`onPlayVideo` 不在每次 `MainScreen` 重组时新建实例
- 启用 Coil `crossfade` 过渡 + `CoverArt` 占位背景，缓解图片加载视觉弹跳
- 行级派生字符串 `remember` 化：`VideoItem.metaText()`/`detailChips()` 等在行组合内 `remember(video)`

## Acceptance Criteria

- [ ] 播放期间滚动任一 Tab 不再每秒重组（Tab 屏不读取播放 StateFlow）
- [ ] 图片加载有 crossfade 过渡，无瞬间弹出
- [ ] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过
- [ ] 用户主观验证滚动流畅度提升

## Technical Approach

**Phase 1 (根因, 最高影响)**: 在 `MainScreen` 中抽取 `PlaybackLayer` 子组合函数，将 `musicVM.state`/`audiobookVM.state`/`videoVM.state`/`error`/`lyrics` 等 `collectAsStateWithLifecycle` 全部下移到 `PlaybackLayer` 内。Tab 内容（`AnimatedContent` + `when(tab)`）不再读取任何播放状态，`MainScreen` 播放时不再每秒重组。跨域 handoff 用 `vm.state.value`（一次性读，不订阅）。

**Phase 2 (防御)**: Tab 内容的 `onSongSelected`/`onPlayAudiobook`/`onPlayVideo` lambda 用 `remember` 稳定化或提取为不捕获局部 `fun` 的稳定引用。

**Phase 3 (视觉)**: `ImageLoader.Builder` 加 `.crossfade(160)`；`CoverArt` 的 Box 背景作为占位（已有渐变背景，图片加载前即显示）。

**Phase 4 (GC)**: `VideoItem.metaText()`/`detailChips()` 在 `VideoCard`/`VideoDetailScreen` 行内 `remember(video) { ... }`。其他行级 `formatDuration`/`buildList` 同理 `remember` 化。

## Out of Scope

- 播放器屏内 `visiblePosition` 顶层读取优化（M-Compose finding，独立任务）
- 新增插桩 UI 测试
- Coil 升级到 3.x

## Decision (ADR-lite)

**Context**: 全部滚动表面卡顿，根因为 `MainScreen` 每秒重组级联到 Tab 内容。
**Decision**: 全面优化（Phase 1-4），根因隔离 + 防御 + 视觉 + GC。
**Consequences**: `MainScreen` 结构调整，`PlaybackLayer` 抽取；公共 API 不变；播放器屏重组优化留后续。

## Definition of Done

- 单元/插桩测试覆盖关键纯逻辑变更（如有）
- `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug --no-daemon` 通过
- 用户主观验证卡顿缓解

## Out of Scope (explicit)

- (待确认后填充)

## Technical Notes

- 相关文件: `MusicScreenV2.kt`、`MusicHomeSections.kt`、`MusicBrowseComponents.kt`、`VideoScreen.kt`、`VideoBrowseComponents.kt`、`VideoDetailScreen.kt`、`AudiobookScreen.kt`、`MusicPlayerScreen.kt`、`AudiobookPlayerScreen.kt`、`VideoPlayerScreen.kt`、`SharedComponents.kt`、`AuthedAsyncImage.kt`
- 已完成相关任务: T6 (Compose 性能快赢)、T7 (UI 拆分 + 共享组件)
- 代码审查参考: `.trellis/workspace/hhy/code-review-2026-08-02.md` M-Compose 性能 + minor 重组 段落
