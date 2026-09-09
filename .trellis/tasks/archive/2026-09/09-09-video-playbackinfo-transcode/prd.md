# 视频 PlaybackInfo 转码与清晰度选择

## Goal

视频功能补全第二批 B：为视频播放引入 Emby PlaybackInfo 握手与转码链路。当前播放只有 `Static=true` 直连流，无法播放直连不支持的格式（如部分音轨编码/高码率 4K）。本任务实现：PlaybackInfo 请求（带 DeviceProfile）→ 判断可直连 → 直连失败/不支持时回退 HLS 转码流 → 设置面板提供清晰度/码率选择（自动/原始/限码率档位）并持久化。

## What I already know

* 用户决策（09-08-video-tracks-persistence）：转码为第二批任务；Cast/外部播放器/下载不碰。
* 现状：`EmbyRepository.streamUrl()` 只生成 `/Videos/{id}/stream?Static=true`；无 PlaybackInfo 调用、无 DeviceProfile、无 PlaySessionId。
* 上报协议：`Sessions/Playing/Progress|Stopped` 只带 ItemId/PositionTicks/IsPaused，无 PlaySessionId。
* 端点已确认存在（上一任务 openapi 验证）：`GET/POST /Items/{Id}/PlaybackInfo`（MediaInfoService）、`GET /Videos/{Id}/master.m3u8|live.m3u8`（DynamicHlsService）、`/Playback/BitrateTest`。
* 引擎：`VideoPlaybackEngine.play(video)` 以 `shouldReplaceCurrentVideoItem`（id+streamUrl）决定是否重建媒体项；`VideoItem.streamUrl` 是唯一播放源。
* 持久化先例：`EncryptedConfigStore` key + Flow + save（`videoPlaybackSpeed`/`videoPipEnabled`/`videoAutoSkipIntro`）。
* UI 先例：Settings 面板 `VideoPlayerSettingRow` + `MediaPlayerChoiceRow` 选择行；`VideoPlayerPanel` 枚举。
* 服务器：Emby 4.9.5.0（用户真机，可 curl 验证）。

## Assumptions (temporary)

* DeviceProfile 可以用最小可用集（容器/编解码白名单），不必完整复刻官方客户端 profile。
* 转码决策放在播放启动时（每次 play 前请求 PlaybackInfo），不在播放中动态切换码率（切换 = 重新 play，保留进度）。

## Open Questions

* （已全部解决）

## Research References

* [`research/playbackinfo-transcode-api.md`](research/playbackinfo-transcode-api.md) — PlaybackInfo/Master.m3u8 真机验证：转码决策在客户端做，URL 客户端构造，PlaySessionId 由服务器返回。

## Requirements (confirmed)

* **清晰度档位（VideoQualityMode，6 档）**：`AUTO`（自动，直连优先）、`ORIGINAL`（原始，强制 Static 直连）、`BITRATE_2M`、`BITRATE_4M`、`BITRATE_8M`、`BITRATE_20M`（限码率转码），持久化默认 AUTO。
* 档位 ≠ AUTO/ORIGINAL 时在播放启动时改写播放源：构造 `/Videos/{id}/master.m3u8` 转码 URL（MediaSourceId/VideoCodec=h264/AudioCodec=aac/VideoBitrate=n）。
* PlaybackInfo POST 握手（带最小 DeviceProfile + UserId）获取 PlaySessionId 与 MediaSourceId，供转码 URL 与上报使用；握手失败回退 Static 直连。
* **转码播放失败自动回退**：转码流触发 `onPlayerError` 时，若该视频存在直连流且尚未回退过，自动改用 Static 直连流重试一次（保留当前进度）；仍失败才走现有错误链路。
* 转码会话的 Progress/Stopped 上报附 `PlaySessionId`（DTO 扩展，向后兼容）。
* 设置面板「清晰度」行（仅当视频可播放时显示），选择即时持久化，重新播放生效并保留进度（通过现有 play 路径 seek 恢复）。
* 转码播放出错且回退也失败时，错误文案提示可尝试切换清晰度。

## Acceptance Criteria (confirmed)

* [ ] 直连可播的视频行为不回归（AUTO/ORIGINAL 仍走 Static 流）。
* [ ] 选择限码率档位后播放走 master.m3u8 转码流（真机验收确认）。
* [ ] 转码流播放失败自动回退直连重试一次（保留进度），仍失败才报错。
* [ ] 清晰度档位可切换并持久化；重启应用后保持；重新播放生效且保留进度。
* [ ] 转码会话上报带 PlaySessionId（向后兼容，字段可空）。
* [ ] 单测：转码 URL 构造、播放源档位解析、回退判定、配置 round-trip、DeviceProfile 请求体。
* [ ] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK。

## Decision (ADR-lite)

**Context**：转码决策放服务器（PlaybackInfo Supports* 字段）还是客户端（档位驱动）。
**Decision**：客户端档位驱动。真机验证 4.9.5 对 mkv/H.265 始终返回 SupportsDirectPlay=true（判定宽松），且 TranscodingUrl 不返回；服务器字段不可依赖。
**Consequences**：AUTO 档 MVP 行为与 ORIGINAL 等价（直连），后续可在此判定点扩展本地格式能力检测；转码 URL 参数（h264/aac/ts）固定最小集，复杂格式支持留待反馈。

## Current Progress

* PRD 完成，research 真机验证完成，jsonl curate 完成。

## Out of Scope

* Cast、外部播放器、离线下载。
* 播放中动态码率切换（ABR 由 HLS 自适应或重播实现）。
* 字幕烧录选项、转码进度百分比展示。
* 音频/有声书域。

## Technical Notes

* 关键文件：`EmbyApi.kt`、`EmbyRepository.kt`、`VideoPlaybackEngine.kt`、`VideoPlaybackViewModel.kt`、`VideoPlayerPanels.kt`、`EncryptedConfigStore.kt`。
* DeviceProfile DTO 按最小集建模（MaxStreamingBitrate + DirectPlayProfiles + TranscodingProfiles，真机验证已接受）。
* PlaybackInfo 响应形状已真机验证（见 research），DTO 只取需要的字段（PlaySessionId/MediaSources[0].Id/Supports*）。
* 转码 URL 认证走现有 `MediaAuthHeaderRegistry`（X-Emby-Token header），与字幕 VTT 同机制。
