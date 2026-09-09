# 继续观看对齐服务器 Resume 列表

## Goal

视频首页「继续观看」当前从本地已加载目录推导（position > 0 且未看完），与 Emby 服务器官方 Resume 列表在候选规则、新鲜度（30 分钟目录缓存 TTL）、范围（仅当前媒体库）上均不一致，用户看到的内容与服务器网页端不同。改为优先请求服务器 `/Users/{UserId}/Items/Resume`，失败或为空时回退现有本地推导。

## What I already know

* 现状：`continueWatchingShelf(videos)`（`VideoScreenLogic.kt:69`）本地推导，spec 记录为 "view state only, derived from already-loaded list"。
* 服务器端点：`GET /Users/{UserId}/Items/Resume`（openapi 已确认存在，上一任务验证过 ItemsService 端点族）。
* `EmbyRepository` 已有 `session()`（userId+token）、`getItems` 分页模式、`EmbyItemDto.toVideoItem` 映射、`requireResponseBody` 错误处理——Resume 端点返回同形状 `EmbyItemsResponse`，映射可完全复用。
* UI 消费点：`VideoScreen.kt:87` `continueWatchingShelf(videos)` → `VideoBrowseComponents` 的「继续观看」shelf。
* 缓存合同：浏览目录缓存 30 分钟 TTL（`EmbyVideoCacheRepository` + `CacheTtl`）；Resume 列表是实时视图，不进缓存（每次刷新目录时顺带拉取即可，或随目录刷新节奏）。
* spec 合同（emby-integration.md）：continue-watching 本地推导规则（position > 0、未看完、position < duration、lastPlayedDate 倒序）——本任务将其降级为回退路径。

## Requirements (confirmed)

### R1 数据层
* `EmbyApi` 加 `GET Users/{userId}/Items/Resume`（复用 `EmbyItemsResponse`；Fields 与 getItems 一致含 MediaStreams/Chapters/UserData；`MediaTypes=Video`，`Recursive=true`，limit 12）。
* `EmbyRepository.getResumeItems(limit: Int = 12): List<VideoItem>`：session → 请求 → `toVideoItem` 映射（libraryId 用返回条目的 parent 或空串兜底）→ 复用 `deduplicatedById()`。
* 失败抛 `EmbyApiException`（catch-rethrow 模式），由调用方决定回退。

### R2 UI/状态层
* `VideoScreen` 刷新目录时（现有 `LaunchedEffect(savedConfig)` 链路）并行请求 Resume 列表；成功 → 用服务器列表渲染「继续观看」；失败或为空 → 回退 `continueWatchingShelf(videos)` 本地推导。
* Resume 列表为 UI 状态（`remember`/state），不写入 DataStore 缓存（实时视图，避免与 TTL 语义混淆）。
* 服务器列表条目与本地目录条目按 id 对齐：渲染卡片所需的 streamUrl 等字段若 Resume 响应缺失，用本地目录同 id 条目补齐（`toVideoItem` 已生成 streamUrl，通常无需补齐；对齐逻辑做成纯函数）。

### R3 回退语义
* 回退条件：请求异常（网络/认证）、响应为空列表。回退时保持现有本地推导行为不变。
* 回退不弹错误（继续观看是锦上添花视图，失败静默）。

## Acceptance Criteria

* [ ] 服务器网页端「继续观看」里的条目与 App 一致（含其他设备看的、刚看完的）。
* [ ] 看了几秒就停的内容按服务器规则显示/隐藏（不再出现 App 多出来的条目）。
* [ ] Resume 请求失败时回退本地推导，UI 不报错、不空白（有本地候选时）。
* [ ] 单测：Resume 端点请求参数、映射复用、对齐纯函数、回退判定。
* [ ] compile + test + lint + assemble 全绿；CHANGELOG 条目；manual-checklist + APK。

## Out of Scope

* 「最高评分」「未观看」shelf 的对齐（仍本地推导）。
* Resume 列表持久化缓存。
* 音乐/有声书域。

## Technical Notes

* 关键文件：`EmbyApi.kt`、`EmbyRepository.kt`、`VideoScreen.kt`、`VideoScreenLogic.kt`、`VideoBrowseComponents.kt`。
* `getItems` 的 `Fields` 默认值可直接复用（Resume 端点同样接受 Fields 参数）。
* 对齐纯函数：`mergeResumeItemsWithCatalog(resume, catalog): List<VideoItem>`——resume 优先，catalog 补缺字段。
* 注意 `VideoScreen` 的 config-state version guard：Resume 请求也要带请求身份，过期响应不得写入状态（复用现有 `videoConfigStateVersion` 模式）。
