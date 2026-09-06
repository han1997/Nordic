# 移除视频播放页横竖屏切换按钮

## Goal

移除视频播放器控制栏中的横竖屏切换按钮（`ScreenRotation` 图标 / `onToggleOrientation`），简化方向控制模型。该按钮由上一个任务（09-05-media-crash-orientation-toggle）引入，实际使用中多余。

## What I already know

* 按钮位于 `VideoPlayerScreen.kt:1052-1058`（右侧按钮组：下一集(可选) / 旋转 / 全屏），通过 `onToggleOrientation` 回调逐层传递：`MainScreen` → `VideoPlayerScreen`（:171, :458, :951, :1010）。
* 方向状态 `orientationLockedLandscape` 在 `MainActivity.kt:340`（MainScreen 内 rememberSaveable），由三处写入：
  * 全屏切换 `:807`：`orientationLockedLandscape = !isFullscreen`
  * 旋转按钮 `:810`：swap
* 单一全屏控制器 `LaunchedEffect(isFullscreen, showVideoPlayer, orientationLockedLandscape)`（:641-655）调用 `resolveVideoOrientationRequest(showVideoPlayer, lockedLandscape)`（:124-133）。
* `resolveVideoOrientationRequest` 有 3 个单测（MainActivityTest.kt:294-330）：竖锁默认 / 横锁切换 / 关闭恢复系统控制。
* spec 契约：`.trellis/spec/backend/emby-integration.md` "Video Playback Display Modes and Fullscreen" 场景中 manual-lock 模型、`onToggleOrientation` 签名、Validation 矩阵、Good/Bad cases 均需随本任务更新（Phase 3.3）。
* 控制栏布局契约（spec :446-447）：右侧组 = 下一集(若有)/旋转/全屏；`resolveVideoPlayerControlSizing` 按钮数变化会影响 sizing 计算与对应单测。

## Assumptions

* 仅移除按钮与相关状态/回调/测试，不改变全屏进出行为（全屏仍隐藏系统栏）。

## Decision (ADR-lite)

**Context**: 移除旋转按钮后需确定方向模型。备选：双锁定（全屏横/非全屏竖）、非全屏交还系统控制、全屏 SENSOR_LANDSCAPE。
**Decision**: 保持双锁定模型（用户选定）：全屏=横锁，非全屏=竖锁，重力从不改变播放方向。仅删除按钮及 swap 写入点，`orientationLockedLandscape` 状态保留。
**Consequences**: 行为最可预测；`resolveVideoOrientationRequest` 签名可保留或简化为状态直读；spec 契约删除旋转按钮相关描述。

## Requirements

* 移除 `VideoPlayerScreen` 控制栏中的旋转按钮及 `onToggleOrientation` 参数链（:171, :458, :951, :1010, :1052-1058）。
* 移除 `MainScreen` 中旋转按钮的 swap 写入点（MainActivity.kt:810）；`orientationLockedLandscape` 状态保留，仅由全屏切换驱动（:807）。
* `resolveVideoOrientationRequest` 保留双锁定语义（全屏横锁/非全屏竖锁/关闭系统控制）；如签名简化需同步单测。
* 更新 MainActivityTest 中方向相关单测（删除"toggled"用例或改写为全屏驱动语义）。
* 更新 emby-integration.md 契约（Phase 3.3）：删除 `onToggleOrientation` 签名、manual-lock 按钮描述、Validation/Good/Bad 中按钮相关条目；右侧按钮组描述改为 下一集(若有)/全屏。

## Acceptance Criteria

* [ ] 播放器控制栏不再显示旋转按钮；右侧组为 下一集(若有)/全屏。
* [ ] 全屏=横锁，非全屏=竖锁，重力不改变播放方向；关闭播放器恢复系统控制（UNSPECIFIED）。
* [ ] 方向相关单测与双锁定语义一致。
* [ ] compile + testDebugUnitTest + lintDebug 全绿。
* [ ] spec 契约段落与实现一致。

## Definition of Done

* 单测与实现同步更新。
* compile + testDebugUnitTest + lintDebug 全绿。
* emby-integration.md 更新。
* CHANGELOG 条目（中文优先）。

## Out of Scope

* 播放器 UI 重设计、手势变更。
* 重力感应自动旋转的重新引入（用户明确选择双锁定模型）。
* 非全屏时交还系统方向控制。

## Technical Notes

* 关键文件：
  * app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt（按钮 + 参数链）
  * app/src/main/java/com/nordic/mediahub/MainActivity.kt（状态 + 控制器 + resolve 函数）
  * app/src/test/java/com/nordic/mediahub/MainActivityTest.kt（方向单测）
  * .trellis/spec/backend/emby-integration.md（契约）
* 前置任务归档：.trellis/tasks/archive/2026-09/09-05-media-crash-orientation-toggle/prd.md
