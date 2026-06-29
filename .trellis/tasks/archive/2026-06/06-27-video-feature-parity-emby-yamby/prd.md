# Video Feature Parity with Emby/Yamby

## Goal

完善 Nordic 视频播放器，对标 Emby 官方客户端和 Yamby 的主流功能，补齐 4 项关键播放体验：快进快退按钮、上一集导航、自动播放下一集、双击暂停/播放。

## Requirements

* 添加快进/快退按钮，跳进间隔可选 10s/15s/30s，在播放器内切换
* 补齐上一集导航（当前只有下一集）
* 当前集播放结束后立即无缝切换到下一集
* 双击屏幕任意位置切换暂停/播放

## Acceptance Criteria

- [ ] 播放器控制栏有快进/快退按钮，点击跳进所选间隔秒数
- [ ] 可在播放器内切换跳进间隔（10s/15s/30s），按钮标签同步更新
- [ ] 上一集按钮可用，播放上一集后 Emby 播放进度正确上报（stop 旧 + start 新）
- [ ] 当前集播放结束 → 自动无缝播放下一集（无倒计时弹窗）
- [ ] 双击视频区域切换暂停/播放，与现有拖拽手势互不冲突
- [ ] 无下一集时，上一集/下一集按钮正确隐藏或禁用
- [ ] 为 VideoEpisodeQueue 补充 backward 导航的单元测试

## Definition of Done

* Tests added/updated (unit/integration where appropriate)
* Lint / typecheck / CI green
* 所有新功能在真机上验证

## Technical Approach

### 1. 快进/快退按钮 + 间隔选择
- 在 `VideoPlayerControls` 的 play/pause 按钮两侧加 rewind/forward IconButton
- 间隔值存为 `VideoPlaybackState` 的字段，默认 10s
- 按钮 long-press 或点击按钮旁小标签弹出 10s/15s/30s 选择

### 2. 上一集导航
- `VideoEpisodeQueue` 增加 `hasPrevious`、`previousEpisode()`、`goToPrevious()`
- `VideoPlayerScreen` 新增 `onPlayPreviousEpisode` 回调 + hasPreviousEpisode 状态
- MainActivity 中实现 `playPreviousVideoEpisode()`，逻辑对标已有 `playNextVideoEpisode()`

### 3. 自动播放下一集
- `VideoPlaybackEngine` 的 `Player.Listener` 在 `onPlaybackStateChanged(STATE_ENDED)` 时触发回调
- 新增 `onPlaybackEnded: () -> Unit` 回调，由 MainActivity 接收并调用已有的 `playNextVideoEpisode()`

### 4. 双击暂停/播放
- 在视频区域 `pointerInput` 中检测双击（两次 tap 间隔 < 300ms）
- 双击触发 `onPlayPause()`
- 单击保留现有行为（显示/隐藏 overlay）
- 需用 `detectTapGestures` 的 `onDoubleTap` + `onTap` 配合，避免单击延迟

## Decision (ADR-lite)

**Context**: 需要决定 4 项功能的具体交互方式
**Decision**:
- 快进/快退: 可选间隔 10s/15s/30s
- 自动播下一集: 立即无缝切换，无倒计时
- 双击: 任意位置暂停/播放（非分区快进快退）
**Consequences**: 双击暂停/播放实现更简单，但不如分区双击灵活；未来可扩展为分区双击

## Out of Scope (explicit)

* Skip Intro（依赖 Emby Premiere 服务端功能）
* 画质/码率选择（涉及转码链路，复杂度高）
* 字幕延迟偏移（放到后续版本）
* 画中画 PiP（放到后续版本）
* 画面锁定
* Live TV / DVR
* Cinema Mode / Trailers
* 离线播放
* Chromecast 投屏

## Technical Notes

* 播放引擎：`app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt` (Media3 ExoPlayer)
* 播放器 UI：`app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`
* 剧集队列：`app/src/main/java/com/nordic/mediahub/data/VideoEpisodeQueue.kt`（只有 forward，需加 backward）
* Emby API：`app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt` / EmbyRepository.kt
* MainActivity 播放逻辑：`app/src/main/java/com/nordic/mediahub/MainActivity.kt` (playNextVideoEpisode at line 444)
