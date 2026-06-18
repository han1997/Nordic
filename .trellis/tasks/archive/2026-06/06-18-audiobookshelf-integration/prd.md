# AudiobookShelf 接入

## Goal

把 Nordic 的 AudiobookShelf 页从“仅能保存配置”推进到可用的真实集成，先明确 MVP 的接口范围、登录方式、字段映射和播放架构，再进入实现。

## What I already know

* 当前代码已经有 `AudiobookShelfConfig` 和 `ConfigRepository.saveAudiobookConfig()`，但没有真实 API 接入。
* `AudiobookScreen.kt` 目前只有配置 UI，没有书库列表、详情、播放、进度同步。
* 音乐链路已经形成完整模式：`Repository -> Compose screen state -> MusicPlaybackEngine -> MusicPlaybackService -> MediaSession/ExoPlayer`。
* AudiobookShelf 官方源码确认了以下能力：
  * 本地登录：`POST /login`
  * OpenID 登录：`GET /auth/openid`
  * 图书馆/条目：`/api/libraries`、`/api/libraries/:id/items`、`/api/items/:id`
  * 播放会话：`POST /api/items/:id/play`
  * 进度/会话同步：`/api/me/progress/*`、`/api/session/:id/sync`、`/api/session/:id/close`

## Assumptions (temporary)

* MVP 优先面向 AudiobookShelf 的 audiobook library，不先做 podcast/ebook 全覆盖。
* Android 客户端会直接请求 AudiobookShelf 官方 HTTP API。
* 如果播放进入 MVP，应该按 AudiobookShelf 的 playback session/progress contract 接，而不是只拿一个裸流地址。

## Open Questions

* 无阻塞性开放问题。实现前可继续细化字段名和页面结构，但核心范围与架构决策已确定。

## Requirements (evolving)

* 用户可以保存并使用 AudiobookShelf 服务端配置。
* 系统应能拉取至少一个真实 AudiobookShelf 图书馆并展示条目。
* PRD 必须先明确：
  * 哪些接口进 MVP
  * 哪种登录进 MVP
  * 条目/章节/进度的字段映射
  * 播放是否复用现有 PlaybackEngine
* 首版登录固定为用户名/密码登录：
  * 使用当前已存在的 `serverUrl + username + password` 本地配置模型
  * 调用 `POST /login`
  * 当前不纳入 OpenID、浏览器跳转或 callback 处理
* MVP 范围选择为“可听 MVP”：
  * 列出可访问的 audiobook libraries
  * 展示 library items 列表与 item 详情
  * 启动真实 playback session
  * 读取并回写听书进度
  * 在退出播放时关闭或同步 session
* MVP 暂不包含：
  * OpenID 登录
  * 书签管理
  * continue listening 首页编排
  * podcast/ebook 扩展能力
* 字段映射采用 `Summary + Detail + Playback` 三套领域模型：
  * `Summary` 用于书库列表卡片
  * `Detail` 用于条目详情与章节信息
  * `Playback` 用于播放 session、audio tracks、resume/progress 同步
* 不强行复用 `NavidromeSong` 或当前音乐模型。
* 播放层方案固定为：
  * 复用现有 Media3 / MediaSession / ExoPlayer / cache 基础设施
  * 新建 `AudiobookPlaybackEngine`
  * 不直接复用 `MusicPlaybackEngine` 的音乐领域状态与 `NavidromeSong` 队列模型

## Acceptance Criteria (evolving)

* [x] MVP 接口范围明确到具体 endpoint 级别。
* [x] 登录方案明确，并与当前 Android 客户端能力相匹配。
* [x] 字段映射策略明确为 Summary/Detail/Playback 三层模型。
* [x] 字段映射覆盖列表页、详情页、封面、作者/播讲者、章节、进度等核心展示字段。
* [x] 播放架构有明确选择，并说明为什么。
* [x] 可听 MVP endpoint 清单固定并标注 in/out of scope。

## Research References

* [`research/external-api-auth-playback.md`](research/external-api-auth-playback.md) - 官方源码确认了认证、图书馆、播放会话、进度同步接口。
* [`research/current-app-architecture.md`](research/current-app-architecture.md) - 当前 Nordic 已有音乐链路与 Audiobook 配置骨架的可复用点。

## Technical Approach

MVP 已选“可听 MVP”。当前候选技术路径需要继续收敛登录方式、字段模型和播放抽象。

### In-scope endpoints

* `POST /login`
* `GET /api/libraries`
* `GET /api/libraries/:id/items`
* `GET /api/items/:id`
* `GET /api/items/:id/cover`
* `POST /api/items/:id/play`
* `GET /api/me/progress/:id/:episodeId?`
* `PATCH /api/me/progress/:libraryItemId/:episodeId?`
* `POST /api/session/:id/sync`
* `POST /api/session/:id/close`

### Planned field mapping direction

* `Summary`
  * source: `libraryItem.toOldJSONMinified()`
  * fields: `id`, `libraryId`, `media.metadata.title`, `media.metadata.authorName`, `media.metadata.narratorName`, `media.metadata.seriesName`, `media.coverPath`, `media.duration`, `media.numChapters`
* `Detail`
  * source: `GET /api/items/:id?expanded=1&include=progress`
  * fields: `metadata.authors`, `metadata.narrators`, `metadata.series`, `metadata.description`, `chapters`, `duration`, `coverPath`, `userMediaProgress`
* `Playback`
  * source: `POST /api/items/:id/play`, `GET/PATCH /api/me/progress/*`, `POST /api/session/:id/sync|close`
  * fields: `sessionId`, `libraryItemId`, `displayTitle`, `displayAuthor`, `coverPath`, `duration`, `audioTracks`, `chapters`, `startTime`, `currentTime`, `progress`

### Playback architecture

* Reuse:
  * `MusicPlaybackService` 背后的 Media3 / ExoPlayer / cache / MediaSession 这类基础设施思路
  * 现有 app 内 `MediaController` 驱动方式
* New audiobook-specific pieces:
  * `AudiobookPlaybackEngine`
  * audiobook media-item adapter
  * audiobook playback state model
  * AudiobookShelf session/progress sync coordinator
* Explicitly not reused as-is:
  * `NavidromeSong`
  * music queue state
  * lyrics/repeat/music-specific player semantics

### Out-of-scope endpoints for MVP

* `GET /auth/openid`
* `GET /auth/openid/callback`
* bookmark 相关接口
* continue listening / stats / personalized shelves
* podcast/ebook 专用接口

## Decision (ADR-lite)

### Context

AudiobookShelf 官方接口能力比当前 Nordic 的 Audiobook 页成熟得多，但现有播放链路明显偏音乐模型。需要先决定 MVP 的边界，避免一开始就把播放、认证、同步、UI 全部绑死在错误抽象上。

### Decision

MVP 采用“可听 MVP”，覆盖列表、详情、播放会话、进度读取/回写与 session sync/close；登录方式固定为用户名/密码 + `POST /login`；字段模型采用 `Summary + Detail + Playback` 三层设计；播放层复用 Media3/MediaSession 基础设施但拆出独立 `AudiobookPlaybackEngine`；暂不纳入 OpenID、书签与更广的用户态能力。

### Consequences

* 能形成“登录 -> 浏览 -> 播放 -> 续播”的最小完整闭环。
* 需要较早考虑播放模型和进度同步 contract，不能只做静态列表。
* 认证复杂度仍可控，因为直接沿用当前 `AudiobookShelfConfig` 配置模型即可。
* 字段模型将与音乐域保持边界，避免把章节、播讲者、session 等语义压扁进 `NavidromeSong`。
* 通过独立 `AudiobookPlaybackEngine`，可以复用底层播放器能力而不把音乐域状态继续扩散到听书域。

## Implementation Plan (small PRs)

* PR1: API client + auth + AudiobookShelf summary/detail models + repository skeleton
* PR2: audiobook library list + detail screen + cover/progress rendering
* PR3: `AudiobookPlaybackEngine` + play/session/progress sync + playback UI integration

## Out of Scope

* 服务器管理/admin 能力
* 分享/RSS/下载队列等非核心听书能力
* 在 PRD 未确认前直接开始实现

## Technical Notes

* 当前本地配置模型只有 `serverUrl + username + password`。
* 如果要复用现有播放层，优先考虑复用 `MediaSession`/`ExoPlayer` 基础设施，而不是直接复用 `NavidromeSong` 模型。
