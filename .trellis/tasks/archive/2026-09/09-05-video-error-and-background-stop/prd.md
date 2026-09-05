# 视频播放异常与后台不停播修复

## User Report

1. **播放异常，同步 Emby 视频进度失败** — 播放器报错/进度同步失败提示
2. **视频返回不自动停止** — 关闭播放页后视频仍在后台播放（用户不需要画中画行为）

## Root Cause 分析

### 问题 1：进度同步失败
`VideoPlaybackViewModel.closeVideoPlaybackInternal` / 周期同步 / 暂停同步在失败时把异常 message 写进 `_error`，`VideoPlayerLayer` 的 `externalError` 会让 `VideoPlayerScreen` 显示"播放异常"中央消息——**播放器错误与进度同步错误混用同一个错误通道**。同步失败（网络抖动、token 过期）不该把播放器标为"播放异常"。
另外 `runPeriodicProgressSync` 的 `maxOf(step.positionSeconds, lastSyncedPosition)` 用的是 `video.playbackPositionSeconds`（服务器旧进度）做 baseline（`resolveVideoProgressSyncBaselineSeconds` 取 max），当服务器进度比当前播放位置新（在其他设备看过）时会**回跳**报告错误进度。

### 问题 2：后台不停播
`closeCurrentVideoPlayback`（MainActivity.kt:414-422）是有意为之的最小化行为：隐藏播放器但保留播放、可从 dock now-playing bar 恢复。用户明确表示**不需要**这个行为（无画中画 UI，视频后台播没有意义且耗电）。返回键/关闭按钮应**停止播放并同步进度**。

## Requirements

1. **错误通道分离**：`VideoPlaybackViewModel` 新增 `syncError: StateFlow<String?>`（进度同步失败专用），`error` 只保留真正的播放器错误。同步失败写入 `syncError`，UI 不再弹"播放异常"（可静默或在 dock/status 显示轻提示——本轮静默，仅 Log）。
2. **进度 baseline 修正**：视频进度 baseline 不再取 `max(server, local)`——改为直接用 `state.positionSeconds`（本地播放器位置是权威值），避免服务器旧/新进度覆盖本地。`resolveVideoProgressSyncBaselineSeconds` 语义改为"本地位置优先，位置<=0 时回退服务器记录"。
3. **返回即停止**：`closeCurrentVideoPlayback` 改为调用 `videoVM.closeVideoPlayback`（同步进度后 `engine.stop()`），同步失败走"仍要关闭"路径（`closeVideoPlaybackAnyway`，后台重试 stopped 上报）。dock 的视频 now-playing 入口保留（会话还在时仍可恢复）——但正常关闭路径必须停止引擎。
   - 具体：`BackHandler`/顶部关闭按钮触发的 `closeVideoPlayback` 已经是"同步后停止"路径 ✅（onClosed 里 `engine.stop()` 已调用）。真正的问题是最小化路径 `closeCurrentVideoPlayback` 没有停止。将最小化语义移除：`onClose` 直接走完整关闭。

## Acceptance Criteria

* [ ] 同步 Emby 进度失败不再让播放器显示"播放异常"
* [ ] 播放中按返回/关闭，视频停止播放并上报进度；失败时仍能关闭（后台重试）
* [ ] 进度上报位置与播放器实际位置一致（不再被服务器旧进度顶高）
* [ ] compile + test + lint 通过

## Out of Scope

* 画中画（明确不需要）
* dock 视频 now-playing（保留现状，会话存在时仍可回打开）
