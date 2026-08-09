# 任选方向打磨软件

## Goal

选择一个低风险但用户可直接感知的软件完成度缺口并落地。本轮打磨音乐播放队列：让队列面板中已经存在的上移、下移和拖动排序控件真正改变播放队列，而不是停留在 UI 展示层。

## What I already know

* 用户要求“任选方向打磨软件”，未指定模块，允许代理自行选择方向。
* 项目是 Kotlin/Jetpack Compose Android 媒体中心，音乐播放队列由 `MusicQueueSheet` 呈现，由 `MusicPlaybackEngine` / `MusicPlaybackViewModel` 维护状态。
* `MusicQueueSheet` 已有 `onMoveQueueItem: (fromIndex, toIndex) -> Unit` 参数，队列行内也已经接了上移、下移和拖动 targetIndex。
* `MainActivity.MusicQueueLayer` 当前只传入 `onPlayNext`、`onRemoveFromQueue`、`onClearUpcoming`，没有传 `onMoveQueueItem`，导致排序控件默认 no-op。
* Playback 层已有 `moveItemToIndex(...)` 和 `resolveCurrentIndexAfterMove(...)` 纯函数及测试，可复用来实现通用队列重排。
* `MusicPlaybackEngine.moveQueueItemToPlayNext(...)` 已覆盖“移到下一首”，但不是通用的任意 from/to 排序。

## Assumptions

* 本轮目标是接通已有 UI 控件，不新增新的队列界面或重设计。
* 队列重排应同时支持 Media3 controller 已连接和 pending queue 两种状态，与现有移除/清空逻辑保持一致。
* 重排当前正在播放的歌曲时，应保持这首歌仍作为当前播放项，只更新它在队列中的 index。

## Requirements

* 在 `MusicPlaybackEngine` 增加通用 `moveQueueItem(fromIndex, targetIndex)` 能力。
* 在 `MusicPlaybackViewModel` 暴露对应方法。
* 在 `MainActivity.MusicQueueLayer` 将 `MusicQueueSheet.onMoveQueueItem` 接到 ViewModel。
* Media3 controller 已连接时，调用 controller 的 queue move API 并刷新发布状态。
* controller 尚未连接或处于 pending queue 时，使用现有纯函数更新 pending/current queue 和 queueIndex。
* 无效 index、单项队列、fromIndex == targetIndex 时应安全 no-op。
* 更新 CHANGELOG 记录用户可见的队列排序打磨。

## Acceptance Criteria

* [ ] 队列面板的上移、下移、拖动排序会实际改变播放队列顺序。
* [ ] 当前播放项被移动或其他项跨过当前播放项时，`queueIndex` 仍指向同一首当前歌曲。
* [ ] 无效 index 不崩溃、不改变队列。
* [ ] 已有“移到下一首”“移除”“清空后续”行为不回归。
* [ ] `MusicPlaybackEngineTest` 覆盖通用重排所需的纯逻辑。
* [ ] `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug` 通过。

## Definition of Done

* 代码改动聚焦队列重排接线与 playback 状态更新。
* 不新增大范围 UI 结构，不改变音乐播放、收藏、歌词、下载等无关逻辑。
* 用户可见变更记录到 `CHANGELOG.md`。

## Technical Approach

复用 `moveItemToIndex(...)` 和 `resolveCurrentIndexAfterMove(...)`：controller 可用时委托 Media3 `moveMediaItem(fromIndex, targetIndex)` 并 `publishPlayerState()`；controller 不可用时更新 pending/current queue state。ViewModel 只做薄转发，MainActivity 负责把已有 sheet 回调接上。

## Decision (ADR-lite)

**Context**: 队列 UI 已经暴露排序控件，但主入口未传回调，用户操作没有效果。

**Decision**: 不新增 UI，不重构队列面板；只补齐 playback engine / viewmodel / app-shell 的最小链路。

**Consequences**: 改动横跨 UI shell、ViewModel、Playback engine 和测试，但行为集中在音乐队列排序，风险可通过纯函数测试和现有 Gradle 检查控制。

## Out of Scope

* 不做新的队列编辑界面。
* 不实现持久化播放队列。
* 不改变随机/循环/歌词/收藏行为。
* 不改变 Media3 service 架构。

## Technical Notes

* 任务目录： `.trellis/tasks/08-09-optional-software-polish`
* 相关文件： `MainActivity.kt`、`MusicQueueSheet.kt`、`MusicPlaybackViewModel.kt`、`MusicPlaybackEngine.kt`、`MusicPlaybackEngineTest.kt`
* 相关规格： `.trellis/spec/backend/directory-structure.md`、`.trellis/spec/backend/quality-guidelines.md`
