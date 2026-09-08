# 视频字幕音轨选择与播放偏好持久化

## Goal

视频功能补全第一批（对标主流 Emby 客户端）：补齐内嵌/外挂字幕渲染与字幕/音轨选择面板，倍速播放偏好持久化。后续批次：画中画（独立任务）、章节/PlaybackInfo/片头跳过（第二批）、Cast/外部播放器/下载（暂不碰）。

## What I already know

* 当前播放器完全没有字幕渲染：无 PlayerView/SubtitleView，内嵌字幕不会显示（VideoPlayerScreen 用裸 SurfaceView）。
* 播放 URL 为 `Static=true` 直连；`EmbyApi.getItems` Fields 未请求 MediaStreams，客户端不知道有哪些字幕/音轨。
* Media3 1.3.1 的 `Tracks`/`TrackSelectionParameters`/`SubtitleView`（media3-ui）可用。
* 持久化沿用 `EncryptedConfigStore` 的 key + configFlow + save 模式；`VideoPlaybackViewModel` 已持有 `configRepository`。
* 用户决策：PiP 独立任务后续做（默认开、关窗即停语义已定）；片头跳过两种方式都要（后续批次）；Cast/外部播放器/下载本轮不碰。
* 字幕默认状态：默认关闭，用户在面板手动选（已确认）。

## Requirements (confirmed)

### A1 数据层
* `EmbyApi.getItems` Fields 追加 `MediaStreams`。
* `EmbyApi.kt` 新增 `EmbyMediaStreamDto`（Type/Language/DisplayTitle/Codec/Index/IsExternal），挂到 `EmbyItemDto`。
* `VideoItem` 新增 `mediaStreams: List<VideoStreamInfo>`；`toVideoItem` 映射。

### A2 播放引擎
* 外挂字幕（IsExternal）：`toMediaItem()` 生成 `SubtitleConfiguration`（URL `/Videos/{itemId}/{streamIndex}/Subtitles?format=vtt`，认证复用 OkHttp 拦截器）。
* 内嵌轨道由 Media3 解复用；`VideoPlaybackState` 新增可用轨道与选中态（`onTracksChanged` 发布）。
* 新增 `setPreferredTextTrack(info?)` / `setPreferredAudioTrack(info?)`（TrackSelectionParameters override；null=关闭字幕）。
* 倍速：`play()` 应用持久化倍速。

### A3 UI
* `SubtitleView` 经 AndroidView 叠加在 VideoPlayerSurface 上层。
* 新面板 `VideoPlayerPanel.Tracks("字幕与音轨")`：字幕组（关闭 + 轨道）、音轨组，复用 `MediaPlayerChoiceRow`；Settings 面板加入口行；`VideoPlayerToolLayout` 容纳新入口。

### A4 倍速持久化
* `EncryptedConfigKeys.VIDEO_PLAYBACK_SPEED`；`videoPlaybackPreferences` flow + `saveVideoPlaybackSpeed`。
* ViewModel 初始化读取 → engine 应用；`setPlaybackSpeed` 写回。

### 边界与不变量
* 字幕默认关闭；切集后字幕/音轨选择不跨集保留（每集重新选择，后续可演进默认记忆）。
* 无 MediaStreams 数据（旧缓存）时面板显示「无可用轨道」并保持可关闭。
* 不改变直连播放、进度上报、关闭即停（PiP 任务另行处理）语义。

## Acceptance Criteria

* [ ] 内嵌字幕视频可显示字幕并可选/关闭；外挂 srt 可加载渲染。
* [ ] 多音轨视频可在面板切换音轨。
* [ ] 倍速重启后保持；新面板与既有选择行语言一致。
* [ ] 单测：MediaStream 映射、字幕 URL 构造、倍速 clamp 与偏好存取。
* [ ] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK。

## Out of Scope

* 画中画（独立任务）；章节/PlaybackInfo/片头跳过（第二批）；Cast/外部播放器/下载。
* 字幕样式自定义、跨集字幕记忆、转码。

## Current Progress

* 计划已确认，任务已创建。
