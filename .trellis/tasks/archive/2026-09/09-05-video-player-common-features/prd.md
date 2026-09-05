# 视频播放页常用功能增强

## Goal

依据此前调研（archive/2026-09/08-30-ui/research/player-pages-batch-f.md，Hills/Yamby/Infuse/Jellyfin 范式），为视频播放页补齐剩余的常用功能。Batch F 已落地：亮度/音量手势、手势锁、播放速度、双击 seek、缓冲指示、剩余时间、宽高比、信息面板。

## 本轮新增（按调研优先级）

### V1 下一集入口（Hills/Yamby/Infuse 核心特性）
- **数据**：`VideoItem` 已有 `seriesId/seriesName/seasonNumber/episodeNumber`；`VideoScreen` 已有全量 `videos` 列表与 `relatedEpisodesFor` 排序基建。无需新 server API。
- **纯函数**：`resolveNextVideoEpisode(current: VideoItem, videos: List<VideoItem>): VideoItem?` — 同 `seriesId`（或 seriesName 回退）的 Episode 中，按 (seasonNumber, episodeNumber, title) 排序后取当前集的下一个；无下一集返回 null。
- **UI**：`VideoPlayerScreen` 新增 `nextEpisode: VideoItem?` 与 `onPlayNextEpisode: () -> Unit` 参数；控制行（宽高比按钮旁）加"下一集"按钮（`SkipNext` 图标，无下一集时隐藏）；播放到结尾（`positionSeconds >= durationSeconds - 5` 且非 buffering）时中央显示"下一集"浮层按钮（自动隐藏控制层时仍可见，点击切换）。
- **接线**：`MainActivity.VideoPlayerLayer` 增加 `videos: List<VideoItem>` 来源——从 `VideoScreen` 的列表传入不便（状态在 composable 内），改为 `VideoPlaybackViewModel` 暴露 `catalogVideos: StateFlow<List<VideoItem>>`？——否，保持简单：`MainActivity` 已有 `videoVM`；在 `VideoScreen` 打开播放时把当前库列表存入 `videoVM.setEpisodeContext(videos)`（内存态，仅用于下一集解析）。

### V2 全屏自动横屏（主流播放器标配）
- 进入全屏（`isFullscreen=true`）时 `activity.requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，退出恢复 `SCREEN_ORIENTATION_UNSPECIFIED`；在 `VideoPlayerLayer` 的 `LaunchedEffect(isFullscreen)` 中实现（MainActivity 已有 DisposableEffect 恢复逻辑可复用）。

### V3 长按 2x 倍速（Hills/Yamby/YouTube 范式）
- 长按播放区（非手势锁、非 scrub 中）临时 2x 播放，松开恢复原速；与现有手势共存：长按期间不触发 axis 判定（`detectTapGestures` 的 `onLongPress` 独立 pointerInput，长按触发时置 `isTempSpeeding=true` 并调用 `onSetPlaybackSpeed(2f)`，松开恢复 `state.playbackSpeed` 原值）。
- 长按期间中央显示 "2x ▶▶" 指示 chip。

## Acceptance Criteria

* [ ] 剧集播放时控制行有"下一集"按钮，临近结尾有中央浮层，点击切换到下一集并开始播放
* [ ] 电影（无下一集）不显示相关入口
* [ ] 进入全屏自动横屏，退出恢复
* [ ] 长按临时 2x、松开恢复，指示 chip 显示
* [ ] `resolveNextVideoEpisode` 单元测试覆盖：有/无下一集、跨季、seriesName 回退、乱序输入
* [ ] compile + test + lint 通过

## Out of Scope

* 字幕/音轨选择（Emby 直连流无多轨信息，需转码 API，超出本轮）
* 投屏 / 画中画
* 自动连播设置项（下一集浮层手动点击即可）
