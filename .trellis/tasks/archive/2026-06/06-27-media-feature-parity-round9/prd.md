# 媒体功能完善第九轮

## Goal

继续参考 Emby / Yamby 一类成熟视频客户端完善 Nordic Media Hub 的视频体验。本轮聚焦剧集详情页的单集观看进度：从季列表进入某一集时，保留 Emby 返回的 `UserData`，在单集卡片展示已看 / 继续观看状态，并让播放从该集自己的进度恢复。

## What I Already Know

- 工作树在本轮开始前干净。
- 当前视频功能已经有 Emby 媒体库浏览、详情页、直接播放、播放进度上报、继续观看、音轨 / 字幕控制。
- `VideoItem` 已有 `VideoProgress`，`EmbyItemDto` 已解析 `UserData`，`getPlaybackInfo(item)` 已把 `item.progress.currentTimeSeconds` 带入 `VideoPlaybackInfo.resumePositionSeconds`。
- `VideoEpisode` 当前没有 `progress` 字段，`EmbyRepository.toVideoEpisode(...)` 丢弃了单集 `UserData`。
- `VideoDetailScreen` 从 `VideoEpisode` 构造临时 `VideoItem` 播放时也没有带进度，所以从剧集季列表点某集不会按该集进度续播。
- `EmbyApi.getEpisodes(...)` 当前未显式请求 `Fields=...UserData`，应与其他视频列表接口保持一致，让服务端返回用户进度字段。

## Requirements

- 单集模型保留 Emby `UserData` 映射出的 `VideoProgress`。
- `getEpisodes(seasonId)` 请求应包含足够字段，让 Emby 返回 overview、年份、时长、图片和用户进度。
- 剧集详情页的单集卡片展示观看状态：
  - 已看完：显示已看标记。
  - 有进度且未看完：显示继续观看进度或时间。
  - 无进度：保持现有简洁信息。
- 点击单集播放时，构造的 `VideoItem` 必须携带该集 `progress`，从而复用现有 `getPlaybackInfo(...).resumePositionSeconds` 续播逻辑。
- 保持电影、普通视频、主视频列表和首页继续观看行为不变。

## Acceptance Criteria

- [x] `VideoEpisode` 包含 `progress: VideoProgress?`。
- [x] `EmbyRepository.getEpisodes(...)` 能把 episode `UserData.PlaybackPositionTicks`、`PlayedPercentage`、`Played`、`LastPlayedDate` 映射到 `VideoEpisode.progress`。
- [x] 单集卡片对已看 / 可继续观看状态有清晰展示。
- [x] 从单集卡片启动播放时，episode progress 被传入临时 `VideoItem`。
- [x] Repository 单测覆盖 episode progress 映射和接口字段。
- [x] `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
- [x] `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
- [x] `.\gradlew.bat :app:lintDebug --no-daemon` passes.

## Definition of Done

- Tests added or updated for repository mapping behavior.
- Lint/type-check/test gates pass sequentially on Windows.
- Specs updated if the episode progress contract becomes a reusable Emby convention.
- Changes committed before finish-work.

## Out of Scope

- Adding a separate Yamby provider/backend.
- Autoplay next episode.
- Next-up recommendation rows.
- Mark watched / unwatched mutation APIs.
- Full video detail redesign.

## Technical Approach

- Extend `VideoEpisode` with `progress: VideoProgress? = null`.
- Add a `Fields` query to `EmbyApi.getEpisodes(...)`, including `Overview,ProductionYear,RunTimeTicks,ChildCount,ImageTags,UserData`.
- Map `EmbyItemDto.userData` through the existing `toVideoProgress()` helper inside `toVideoEpisode(...)`.
- Pass `episode.progress` into the temporary `VideoItem` created by `VideoDetailScreen` before calling `getPlaybackInfo(...)`.
- Add small UI text in `VideoEpisodeCard` for `已看完` or `继续观看 <time/progress>` without changing the overall layout.
- Update `EmbyRepositoryTest.getEpisodes_mapsEpisodesFromEmbyItems` or add a focused test for episode progress and request fields.

## Decision (ADR-lite)

**Context**: Mature Emby clients make episode-level progress visible and resumable from the series detail page. Nordic already supports progress at the `VideoItem` and playback layers, but series episode mapping currently drops that data.

**Decision**: Implement episode progress as repository/domain/UI plumbing, reusing the existing `VideoProgress` and `VideoPlaybackInfo.resumePositionSeconds` contracts.

**Consequences**: This improves series playback continuity without adding new playback-engine behavior or new provider scope. It may require updating the Emby integration spec because episode progress becomes part of the season/episode browsing contract.

## Technical Notes

- Task directory: `.trellis/tasks/06-27-media-feature-parity-round9`.
- Key files:
  - `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`
  - `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`
  - `app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt`
  - `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`
- Applicable specs:
  - `.trellis/spec/backend/index.md`
  - `.trellis/spec/backend/directory-structure.md`
  - `.trellis/spec/backend/error-handling.md`
  - `.trellis/spec/backend/quality-guidelines.md`
  - `.trellis/spec/backend/emby-integration.md`
