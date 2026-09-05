# 视频全屏功能失效修复

## User Report

视频播放的全屏功能无效（点击全屏按钮无效果 / 全屏状态异常）。

## Root Cause 分析

全屏由两套控制器同时驱动，存在冲突与状态残留：

1. **重复的横竖屏控制器**：
   - `MainScreen.LaunchedEffect(isFullscreen)`（MainActivity:611）— 隐藏系统栏 + `requestedOrientation`
   - `VideoPlayerLayer.DisposableEffect(isFullscreen)`（e094137 新增，MainActivity:920）— 再次设置 `requestedOrientation`
   - 两者都对同一属性写入；`VideoPlayerLayer` **始终组合**（即使在播放器隐藏时），`configChanges` 旋转引发的重组/时序竞争会造成行为不确定。
2. **全屏状态与播放器可见性解耦**：`isFullscreen` 是 `rememberSaveable`，`showVideoPlayer` 也是。进程重建后两者都恢复 `true`，但视频引擎已空 → App 直接以"横屏 + 隐藏系统栏 + 暂无视频"打开，且无自愈路径，用户感知为"全屏坏了"。
3. **重复的返回键处理器**：`VideoPlayerLayer.BackHandler(isFullscreen)`（:948）与 `VideoPlayerScreen.BackHandler(isFullscreen)`（:263）同时注册，依赖"最后组合者获胜"的隐式顺序。

## Requirements

1. **单一全屏控制器**：删除 `VideoPlayerLayer.DisposableEffect(isFullscreen)`；`MainScreen` 的 `LaunchedEffect` 改为 keyed on `(isFullscreen, showVideoPlayer)`，只有 `isFullscreen && showVideoPlayer` 才进入全屏（隐藏栏 + 横屏），否则一律恢复竖屏 + 显示系统栏。
2. **自愈**：播放器关闭（`showVideoPlayer=false`）时若 `isFullscreen` 残留为 true，自动复位（防进程重建后的卡死状态）。
3. 移除 `VideoPlayerLayer` 中冗余的 `BackHandler(isFullscreen)`（`VideoPlayerScreen` 内已有同功能处理器，且在组合顺序中优先）。

## Acceptance Criteria

* [ ] 点击全屏按钮：系统栏隐藏 + 横屏旋转（单一路径驱动）
* [ ] 退出全屏 / 关闭播放器：竖屏 + 系统栏恢复
* [ ] 进程重建后不会出现无视频的横屏卡死状态
* [ ] compile + test + lint 通过

## Out of Scope

* 手势锁/信息面板等全屏内布局调整
