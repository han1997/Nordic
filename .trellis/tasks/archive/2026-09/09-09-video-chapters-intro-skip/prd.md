# 视频章节跳转与片头跳过

## Goal

视频功能补全第二批 A：为视频播放器添加章节跳转与片头跳过。章节来自 Emby `Chapters` 字段，提供章节列表面板点击跳转；片头跳过基于 Emby 4.9+ intro detection 数据，提供自动跳过（每次播放跳一次）+ 浮动「跳过片头」按钮 + 设置持久化开关。PlaybackInfo 转码为独立任务 B 后排。

## What I already know

* 用户决策：按 A/B 两任务拆分；Emby 服务器为 4.9+；章节仅列表跳转（不做前后章快切）。
* 用户先前决策（09-08-video-tracks-persistence）：片头跳过两种方式都要（自动 + 手动按钮）。
* `getItems` 现有 Fields 未含 `Chapters`（`EmbyApi.kt:143`）；`VideoItem` 无章节模型（`EmbyRepository.kt:28`）。
* 官方 REST 参考（dev/betadev）无 MediaSegments 服务页，文档滞后于 4.9 服务器；片头数据端点需对真实服务器 curl 验证（候选 `/MediaSegments/{id}`）。
* PlaybackInfo / DynamicHlsService / PlaystateService 端点已确认存在（任务 B 用）。
* UI 先例：`VideoPlayerPanel` 枚举（`VideoPlayerLayout.kt:8`）、Settings 面板 `VideoPlayerSettingRow`、选择行 `MediaPlayerChoiceRow`、有声书章节面板 `AudiobookChapterListSheet`。
* 持久化先例：`EncryptedConfigStore` key + Flow + save（`videoPipEnabled`）。
* 引擎跳转先例：`seekTo(positionSeconds)`（`VideoPlaybackEngine.kt:372`）。

## Requirements (confirmed)

### A1 数据层
* `EmbyApi.getItems` Fields 加 `Chapters`；新增 `EmbyChapterDto`（Name/StartPositionTicks/MarkerType，MarkerType 可空）。
* `VideoItem` 加 `chapters: List<VideoChapterInfo>`；`VideoChapterInfo(name, startSeconds, endSeconds?)`，endSeconds 由下一章 start 推导或 null；仅 `MarkerType == null || "Chapter"` 的条目进入章节列表。
* intro 区间：同一 Chapters 数组内第一个 `MarkerType=IntroStart` 与其后第一个 `IntroEnd` 组成区间（tick→秒），挂到 `VideoItem`（introStartSeconds/introEndSeconds，nullable）；不成对则无 intro。

### A2 章节 UI
* `VideoPlayerPanel` 新增 `Chapters`；Settings 面板加「章节」入口行（仅 `chapters.isNotEmpty()` 显示，摘要显示当前章节名）。
* 章节列表面板复用 `MediaPlayerChoiceRow`：当前章节高亮，点击 `seekTo(chapter.startSeconds)`。

### A3 片头跳过
* 自动跳过：播放位置进入 intro 区间时 seek 到区间末尾，每个（item, 播放会话）只跳一次；seek 后不重复触发。
* 手动：intro 区间内浮现「跳过片头」按钮，点击 seek 到区间末尾。
* 设置面板加「自动跳过片头」开关（`EncryptedConfigStore` 持久化，默认开启），先例 `videoPipEnabled`。
* 服务器无 intro 数据时功能静默不出现（无按钮、无自动跳过）。

### A4 边界
* 章节为空/片头区间缺失时 UI 不显示对应入口。
* PiP 模式下不显示跳过按钮（纯视频小窗原则）。
* 自动跳过不与用户手动 seek 冲突：仅监听位置推进，用户 seek 出区间后不回跳。

## Acceptance Criteria

* [ ] 有章节的视频：Settings 显示章节入口，列表面板点击章节跳转到章节起点，当前章节高亮。
* [ ] 无章节视频：不显示章节入口。
* [ ] 有 intro 数据的视频：区间内显示「跳过片头」按钮，点击跳过；自动跳过开启时进入区间自动跳一次。
* [ ] 自动跳过开关可切换并持久化；关闭后仅手动按钮可用。
* [ ] 无 intro 数据的视频：无按钮、无自动跳过。
* [ ] 单测：章节映射（ticks→秒、endSeconds 推导）、intro 触发判定（进入/只跳一次/seek 出区间不回跳）、开关存取 round-trip。
* [ ] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK。

## Out of Scope

* PlaybackInfo 转码 / 清晰度选择（任务 B）。
* 前后章快切控件、章节缩略图（BIF）。
* 片尾（credits/outro）跳过。
* Cast、外部播放器、离线下载。

## Open Questions

* ~~片头数据端点~~：已真机验证（服务器 4.9.5.0）——intro 数据在 `Chapters` 数组内以 `MarkerType=IntroStart/IntroEnd` marker 形式提供，无需独立端点；详见 `research/emby-chapters-intro-api.md`。

## Technical Notes

* 关键文件：`EmbyApi.kt`、`EmbyRepository.kt`、`VideoPlaybackEngine.kt`、`VideoPlaybackViewModel.kt`、`VideoPlayerLayout.kt`、`VideoPlayerPanels.kt`、`VideoPlayerChrome.kt`、`EncryptedConfigStore`/`ConfigRepository`。
* 章节映射纯函数放 `EmbyRepository.kt` 或独立文件，便于单测。
* intro 触发判定做成纯函数（position, introStart, introEnd, alreadySkipped → shouldSkip），引擎内每秒位置更新时调用。
