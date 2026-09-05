# 视频播放控制按钮布局优化（消除横向滚动）

## Goal

重新组织视频播放页底部控制栏：**移除快退/快进按钮**（后续用双击屏幕左/右半区实现，该手势 `VideoPlayerGestures.kt` 已实现），从而消除竖屏窄屏下单行按钮溢出的问题，去掉权宜的 `horizontalScroll`，并做按钮视觉层次优化。

## What I already know

* 现状：底部控制栏单行含 8 个固定宽按钮（比例 / 倍速 / 快退 / 播放 / 快进 / 下一集 / 旋转 / 全屏），竖屏可用约 328dp，总宽约 440dp → 溢出，当前用 `horizontalScroll` 滚动解决（VideoPlayerScreen.kt:982-1058）。
* 用户要求：移除快退/快进按钮；避免按钮滚动；按钮美化 + 布局优化。
* 双击手势已存在于 `VideoPlayerGestures.kt:134-143`：`onDoubleTap` 左半屏快退 10s、右半屏快进 30s，无需新增。
* 移除后剩余按钮：比例(AspectRatio) / 倍速(Speed) / 播放(Play/Pause) / 下一集(SkipNext, 有下一集时) / 旋转(ScreenRotation) / 全屏(Fullscreen)，最多 6 个。
* 布局 token：`NordicSpacing`（xs4/sm8/md12/lg16）、`NordicMotion`、`NordicShapes`。按钮用 `VideoPlayerChromeButton`（44dp 常规 / 58dp primary）。

## Requirements (evolving)

* R1 移除控制栏快退/快进按钮（`Icons.Filled.FastRewind` / `FastForward`）及其 `onSeekBack` / `onSeekForward` 传递。
* R2 移除控制行 `horizontalScroll`（滚动是当前方案，用户不要）。
* R3 重新布局为**单行三组 SpaceBetween**，左右组等宽以确保播放按钮几何居中；播放/工具按钮的常规尺寸上限为 58dp/44dp，窄屏根据扣除双层水平边距后的实际宽度等比缩小，优先保证所有按钮完整可见：
  - 左组：比例、倍速
  - 中：播放/暂停
  - 右组：下一集（若有）、旋转、全屏
* R4 保留双击屏幕左/右半区快退/快进手势（已实现，无需改动）。

## Acceptance Criteria (evolving)

* [x] 控制栏不再横向滚动，竖屏单行完整显示所有按钮。
* [x] 快退/快进按钮已移除。
* [x] 双击视频左半/右半区仍能快退 10s / 快进 30s。
* [x] 播放按钮居中突出，视觉层次清晰。
* [x] `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` 全绿。
* [ ] 真机回归：全屏进出、旋转、下一集、倍速、比例均正常。

## Definition of Done

* compile + test + lint 全绿。
* emby-integration.md 中"Player controls row ... horizontalScroll"契约改为"单行三组布局、不滚动"。
* CHANGELOG 条目。

## Out of Scope (explicit)

* 双击手势实现/调优（已存在，本次不动）。
* 顶栏工具按钮迁移（方案 B，未选）。
* 其它播放器视觉重构。

## Technical Notes

* 关键文件：`app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`（VideoPlayerControls，982-1058；VideoPlayerScreen 参数 157 起）。
* 双击手势：`app/src/main/java/com/nordic/mediahub/ui/VideoPlayerGestures.kt`（已实现，不改）。
* 布局 token：ui/theme/NordicSpacing.kt。
* 同步移除 VideoPlayerScreen 与 MainActivity 的 onSeekBack/onSeekForward 专用回调；保留 onSeekRelative 的双击手势链路。

## Decision (ADR-lite)

**Context**: 单行 8 按钮在竖屏溢出，horizontalScroll 是权宜。用户要求移除快退/快进（改走已存在的双击手势）、消除滚动、优化布局。
**Decision**: 移除快退/快进按钮与 horizontalScroll，控制栏改为单行三组 SpaceBetween 布局，播放按钮居中突出。
**Consequences**: 竖屏不再溢出无需滚动；快退/快进需靠双击手势（已存在）；按钮数最多 6 个；左右组等宽并按实际可用宽度适配，不能仅凭按钮数量推断空间充足。
## 收尾验证说明（2026-09-05）

- 上述布局验收基于代码复核和布局计算回归测试；双击快退/快进确认现有手势及 MainActivity 回调链路保留。
- 完整门禁：编译、500 项单元测试、lint（0 错误、23 警告）、debug 打包均通过。
- 本轮未安装 APK 或执行真机交互回归，相关复选框保留未完成，不把自动检查等同于真机验收。
- 详细检查、边界修正和一次既有测试挂起的重跑记录见 `review.md`。
