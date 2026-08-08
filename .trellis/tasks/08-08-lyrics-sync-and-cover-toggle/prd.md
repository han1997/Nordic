# 修复歌词滚动不同步与播放页切歌后封面无法切换歌词

## Goal

修复播放页面两个回归问题：(1) 歌词高亮滚动与播放进度不同步，导致当前行偏移或跳变；(2) 切下一首后，点击封面无法把封面切换为歌词视图。两者都发生在 `MusicPlayerScreen`，目标是让歌词按播放进度平滑高亮、且切歌后封面 ↔ 歌词切换手势始终可用。

## What I already know

* 播放页主结构在 `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`：
  - `showLyrics` 由 `rememberSaveable(song?.id) { mutableStateOf(false) }` 持有（line 96），切歌时 key 变化，状态重置为 `false`。
  - 封面 ↔ 歌词切换通过 `PlayerPrimaryDisplay` 的 `pointerInput(Unit) { detectTapGestures(onTap = onToggleDisplay) }`（line 290-292）。
  - 外层 `Box` 同时挂了 `pointerInput(Unit) { detectDragGestures(...) }`（line 159-174），用于下拉关闭手势。
* 歌词可见行选择 `selectVisibleLyricLines(...)`（line 792-824），按 `positionSeconds` 整秒粒度滚动；`remember(lyrics, positionSeconds, lineCount)`（line 387）每整秒重算一次。
* 播放进度来源：`MusicPlaybackEngine` 每 1 秒 `publishPlayerState()` 一次（`delay(1000)`, `engine.kt:516`），`positionSeconds` 是整秒。
* 歌词加载：`MusicPlaybackViewModel.kt:47-69` 用 `combine(state.currentSong, _repository).distinctUntilChanged().onEach { ... }` 触发 `runCatching { repo.getLyrics(song) }`。`_isLyricsLoading.value = false` 写在 `runCatching` 块外（line 68），在 suspend 完成前就同步置 false —— 加载状态形同虚设，切歌瞬间旧歌词清空、新歌词未到时 UI 会闪烁"暂无歌词"。

## Assumptions (temporary)

* Bug 2 不是 `showLyrics` 状态被错误重置（切歌后默认回 `false` 是预期），而是切歌后封面点击手势被父级 `detectDragGestures` 吞掉，导致 onTap 不触发。
* Bug 1 的"不同步"主要是：(a) 整秒粒度刷新 + LRC `startMillis` 比较存在最多 ~1s 滞后；(b) `selectVisibleLyricLines` 的窗口起点算法在最后一行附近可能让活动行过早滚出可视窗口。

## Open Questions

* None. 三个关键决策已锁定：(1) Bug 1 范围 = 修高亮滞后（滚窗错位不在本任务）；(2) Bug 1 修法 = 暴露 sub-second 精度位置给歌词；(3) 新字段仅限 Music 播放链路，不扩散到 Audiobook/Video。

## Technical Approach

### Bug 1 — 歌词高亮分秒级同步

在 `MusicPlaybackState` 增加一个 **附加** sub-second 字段（不移除 `positionSeconds: Int`，避免破坏现有速记/格式化/进度同步契约）：

```kotlin
data class MusicPlaybackState(
    ...,
    val positionSeconds: Int = 0,
    val positionMillis: Long = 0L,   // 新增：Media3 currentPosition 直接毫秒
    ...
)
```

- `MusicPlaybackEngine.publishPlayerState()` 里 `positionMillis = activeController.currentPosition.coerceAtLeast(0L)`、`positionSeconds = (positionMillis / 1000L).toInt()`（保持原整数秒语义不变）。
- `MusicPlaybackEngine.startPositionUpdates()` 的 `delay(1000)` **保持不变** —— 这是整个播放 UI 的统一节拍，提速会影响电量与 recomposition；改用更高频进度刷新不在本任务。**关键洞察**：1 秒节拍下，歌词高亮对 `[00:10.50]Line` 这种 0.5s 偏差行的滞后最多仍 1s（待 11s tick 才高亮），但相对本来要到 11s 才高亮的现状，本质上进度节拍不变就消除不了滞后。**因此 Bug 1 的真正修法是把 position 更新提到更高频** —— 但仅限在 `MusicPlayerScreen` 视图层内独立高频采样，不动 engine 全局节拍：

  方案：在 `MainActivity` 的 music 播放页 wrapper 中（或新增一个 `MusicPlayerViewModel`-owned sidecar），用 LaunchedEffect `while (isActive) { delay(100); refreshTick++ }` 之类高频节拍，配合 audio3 controller `currentPosition` 直接拿毫秒位置，单独喂给歌词视图。这避免改 `MusicPlaybackState` 契约与 engine 节拍。

  **简化为推荐路径（MVP）**：不增加复杂的高频采样 sidecar。改成在 `MusicPlayerScreen` 的 `PlayerLyricsDisplay` 内部用一个**独立的内嵌进度查询**：用一个 LaunchedEffect 每 100ms 调一次 `player.currentPosition`（通过新增的 callback prop 传 controller 引用，或通过 ViewModel 暴露 `currentPositionMillisFlow`）来驱动 `selectVisibleLyricLines`。MVP 选 "**ViewModel 暴露 currentPositionMillisFlow**" —— 在 `MusicPlaybackViewModel` 加一个 `val positionMillis: StateFlow<Long>`（在 engine 内部的高频 update 之外，由 VM 用 `flow { while(isActive) { emit(engine.currentPositionMillis()); delay(100) } }` 独立采样），`MusicPlayerScreen` 收集这个 flow 喂给歌词选择。**这样 engine 的 1s `publishPlayerState` 不变；高精度只供歌词路径**。

- `selectVisibleLyricLines` 签名从 `positionSeconds: Int` 改为 `positionMillis: Long`；内部 `activeIndex = lines.indexOfLast { it.startMillis != null && it.startMillis <= positionMillis }`（本来用 `positionSeconds * 1000`，现在直接拿毫秒，消除整秒截断）。
- `PlayerPrimaryDisplay`/`PlayerLyricsDisplay` 的参数从 `positionSeconds: Int` 改为 `positionMillis: Long`；进度条/console 仍用原 `positionSeconds`（整秒足够显示），不混用到歌词路径。
- 单测覆盖 `selectVisibleLyricLines`：构造 `startMillis = 10500` 行 + `positionMillis = 10500` 应 active；`positionMillis = 10499` 应前一行 active。

### Bug 2 — 切歌后点封面无法切歌词

根因：`MusicPlayerScreen.kt:156-174` 的外层 `Box { pointerInput(Unit) { detectDragGestures(...) } }` 与 `PlayerPrimaryDisplay` 的 `pointerInput(Unit) { detectTapGestures(onTap = ...) }` 同处一棵 Compose 树。`detectDragGestures` 在 pointer down 时就消费事件且不放手，导致紧跟其后的 tap（实际触屏若有任何微小位移）被拖拽 detector 吞掉，封面点击失效。切歌后体现更明显是因为切歌瞬间 Compose 重建 primary display，pointerInput 也重建首次手势识别延迟，但根因是手势优先级本身。

修法（按 Compose 官方推荐）：在**同一 `pointerInput` 块**里同时处理 tap + drag，用 `awaitPointerEventScope` / `detectDragGesturesAfterLongPress` 这类自带 tap-detection 兜底的 API，或显式在 `detectDragGestures` 的 `onDragStart` 判定起始位移阈值（位移 > 阈值才认为是拖拽，否则放给 tap）。MVP 采纳：外层 Box 用 `detectDragGesturesAfterLongPress`（只在长按后才消费拖拽，单次点击不触发拖拽消费，让 `detectTapGestures` 兜底），这是最小改动且不破坏下拉关闭手势。

  ```kotlin
  .pointerInput(Unit) {
      detectDragGesturesAfterLongPress(
          onDragStart = { ... },
          onDragEnd = { },
          onDragCancel = { }
      ) { change, dragAmount -> ... }
  }
  ```

- 单测：pointerInput 手势逻辑较难单测；通过单测 `selectVisibleLyricLines` 分秒级行 + 编译/运行自测手势可用性。若可行，新增一个 `detectDragGesturesAfterLongPress` 切换的纯逻辑 helper（如 `shouldStartSwipe(position, threshold)`）单测覆盖。否则只做编译 + 现有测试不回归。

## Decision (ADR-lite)

**Context**: Bug 1 需要让 LRC 分秒级行按真实时刻高亮，而 engine 的 1s 全局进度节拍不能单独为歌词提速（会影响整个播放 UI 与电量）。Bug 2 需要在包含下拉手势的 Compose Box 内可靠转发封面点击。

**Decision**:
1. Bug 1 — 不改 `positionSeconds: Int` 契约，不动 engine 1s 节拍；新增 `MusicPlaybackViewModel.positionMillisFlow`（100ms 独立采样），仅 `MusicPlayerScreen` 的歌词路径消费；`selectVisibleLyricLines` 改用毫秒位置。
2. Bug 2 — 外层 Box 的 `detectDragGestures` 换成 `detectDragGesturesAfterLongPress`，让 `detectTapGestures` 单击兜底；下拉关闭手势在 onDragStart 内仍按"顶部 50% 起始 + 位移阈值"判定（保持现有逻辑）。

**Consequences**:
- 新引入 100ms 高频 flow 是 music 播放页专有的；如未来 Audiobook/Video 也需要可复用同样的 sidecar 模式（记到 spec）。
- `detectDragGesturesAfterLongPress` 意味着下拉关闭手势对短促下拉的响应稍迟（需先判定非长按），但仍可用；onDragStart 的 Y 位置与位移阈值判定保留。
- 若手动点击在 100ms 内带轻微位移（< long-press 触发距离），`detectDragGesturesAfterLongPress` 不消费，tap detector 仍可识别 —— 这是修复点。

## Acceptance Criteria (evolving)

* [ ] LRC 行 `startMillis = 10500` 在播放进度 ≥ 10.5s 时即变 active（直测或单测覆盖）。
* [ ] 切下一首后，点击封面可以切到歌词；再点歌词可以切回封面（手势可靠）。
* [ ] 下拉关闭手势仍在顶部 50% 起始触发，且不影响封面点击。
* [ ] `MusicPlayerScreen` 既有测试通过；新增针对 `selectVisibleLyricLines` 分秒级行的单元测试与手势优先级回归测试（如可行）。
* [ ] compileDebugKotlin / testDebugUnitTest / lintDebug 全部通过。

## Definition of Done

* 代码改动 + 必要单测。
* 三个 Gradle 任务顺序通过。
* 若发现新的可复用规约（如 pointerInput 手势优先级），记到 `trellis-update-spec`。

## Out of Scope (explicit)

* 改动 LRC 解析（`getLyrics` 的 timestamp / offset / metadata 逻辑已稳定）。
* 改动歌词排版动画（逐行淡入/卡拉 OK 效果）是新功能，不在本任务。
* 改动 `MusicPlaybackEngine` 的 position 更新周期机制本身（除非确认需要更高频）。
* 改动歌词加载的 repository/HTTP 行为。

## Technical Notes

* 主文件：`app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`（`selectVisibleLyricLines` 在 792-824 行；`PlayerPrimaryDisplay` 276-312；外层拖拽 Box 156-240）
* 进度引擎：`app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`（`startPositionUpdates` 511-518；`publishPlayerState` 526-565）
* 歌词加载：`app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackViewModel.kt`（`combine` 47-69；`_isLyricsLoading` 时序 bug 在 61-68）
* 现有规约：`.trellis/spec/backend/navidrome-integration.md`（歌词解析 Scenario）、`.trellis/spec/backend/quality-guidelines.md`（media chrome auto-hide + pointer/i gesti、测试要求）
