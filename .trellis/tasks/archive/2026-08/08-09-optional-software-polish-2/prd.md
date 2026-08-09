# 任选方向打磨软件

## Goal

选择一个低风险但用户可直接感知的软件完成度缺口并落地。本轮打磨音乐播放页进度控制体验：让自定义细线进度条在拖动时按手指位置稳定更新，松手时准确 seek，取消拖动时不误提交位置；同时在音乐播放页补齐 10 秒后退 / 30 秒前进能力，和视频、有声书播放体验保持一致。

## What I Already Know

* 用户要求“任选方向打磨软件”，未指定模块，允许代理自行选择方向。
* 项目是 Kotlin/Jetpack Compose Android 媒体中心，音乐播放页由 `MusicPlayerScreen` 呈现，播放位置通过 `MusicPlaybackViewModel.seekTo` 接到 engine。
* `MainActivity` 已将 `MusicPlayerScreen.onSeek` 接到 `musicVM::seekTo`，底层 seek 能力已存在。
* `MusicPlayerScreen.PlayerThinSlider` 是近期新增的自定义细线进度条，用于替代 Material3 Slider。
* 当前 slider 的拖动逻辑在 `onDragStart` 按触点位置更新一次，但后续 `onDrag` 使用参数 `position + dragAmount.x / width * duration` 计算。`position` 是进入本次组合/手势时的外部值，拖动过程中可能是旧值，导致连续拖动不按手指绝对位置稳定跟随。
* 当前 `onDragCancel` 与 `onDragEnd` 一样会调用 `onPositionChangeFinished()`，取消拖动时也可能提交 seek。
* 视频和有声书播放页已经暴露后退/前进 seek 控制，音乐播放页目前只有上一首/下一首，缺少短距离校准进度的控制。
* 上一个同名打磨任务已经完成音乐播放队列排序链路，本轮不重复该方向。

## Assumptions

* 本轮目标是修正已有音乐播放页进度条交互，不重设计播放页布局。
* seek 行为保持“拖动中只更新本地显示，松手后提交真实 seek”的现有设计。
* 取消拖动应恢复到播放器真实位置或等待下一次状态流刷新，不应提交一次用户未完成的 seek。

## Requirements

* 修正 `PlayerThinSlider` 的拖动计算，使拖动中显示位置按手指在 track 上的绝对横向位置更新，而不是基于旧的 `position` 加增量漂移。
* 保持 `onPositionChangeFinished` 只在正常释放时触发真实 `onSeek`。
* 拖动取消时清理本地 scrub 状态，但不触发真实 seek。
* 在音乐播放页增加短距离后退 10 秒、前进 30 秒的用户入口。
* 在音乐播放 ViewModel/Engine 中补齐或复用相应 seek-by 能力，边界应 clamp 到 0 和歌曲时长范围内。
* 保持禁用态、无歌曲态、duration 为 0 或未知时不崩溃。
* 不改变播放/暂停、上一首、下一首、随机、循环、歌词切换和队列入口行为。
* 更新 `CHANGELOG.md` 记录用户可见的播放进度拖动打磨。

## Acceptance Criteria

* [ ] 用户从进度条任意位置开始拖动时，进度显示跟随手指所在位置，而不是只移动很小一段或跳回旧位置。
* [ ] 用户松手后只提交一次 seek 到最终显示位置。
* [ ] 拖动被取消时不提交 seek，播放状态不会被误跳转。
* [ ] 用户可在音乐播放页执行 10 秒后退和 30 秒前进，行为与当前播放位置和歌曲时长边界兼容。
* [ ] 无歌曲、禁用态或 duration 异常时进度条不崩溃。
* [ ] 现有音乐播放页其他控制按钮行为不回归。
* [ ] 相关单元测试通过，至少运行 `testDebugUnitTest`；能运行时补充 `compileDebugKotlin` / `lintDebug`。

## Definition Of Done

* 代码改动聚焦音乐播放页进度条交互和必要测试。
* 不引入新的播放架构、不替换 UI 框架、不大改播放器布局。
* 用户可见变更记录到 `CHANGELOG.md`。

## Technical Approach

将 `PlayerThinSlider` 的 drag 处理改为基于 pointer 当前绝对 x 坐标计算目标进度。为避免取消拖动误 seek，给 slider 增加独立的取消回调，播放页在取消时只清空 `scrubPosition`。音乐播放页补充短距离 seek 控制，优先复用或新增 ViewModel/Engine 的 seek-by 方法，并用纯函数或 playback 测试覆盖边界。如现有私有 composable 难以直接单测，则抽出小型纯函数计算 slider 位置，并在 UI 测试或单元测试中覆盖边界。

## Decision (ADR-lite)

**Context**: 音乐播放页已经有自定义进度条和 seek 链路，但拖动实现仍有手势状态缺口；同时缺少视频/有声书已有的短距离 seek 控制，影响核心播放体验。

**Decision**: 选择进度控制线上的小范围打磨：修正 slider 拖动，增加 10 秒后退 / 30 秒前进；不回退到 Material3 Slider，也不重构播放架构。

**Consequences**: 改动范围集中在音乐播放 UI 和必要 playback 转发；如果为了测试抽出纯函数，会增加一个很小的可复用计算点，但避免为私有 composable 做脆弱测试。

## Out of Scope

* 不新增章节跳转。
* 不实现长按连续 seek。
* 不改变歌词同步、收藏、队列、随机/循环逻辑。
* 不做播放进度持久化策略调整。
* 不重做播放器整体视觉设计。

## Technical Notes

* 任务目录： `.trellis/tasks/08-09-optional-software-polish-2`
* 相关文件： `MusicPlayerScreen.kt`、`MusicPlayerScreenTest.kt` 或相邻 UI 测试、`CHANGELOG.md`
* 相关规格： `.trellis/spec/backend/index.md` 及其 Pre-Development Checklist 中的质量/目录/测试规范。
