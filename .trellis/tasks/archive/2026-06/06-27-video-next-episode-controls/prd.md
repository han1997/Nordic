# 视频播放下一集入口

## Goal

继续参考 Emby/Yamby 的追剧体验，让用户从电视剧详情页播放某一集后，可以在视频播放器里直接切到同一季的下一集。MVP 先做手动“下一集”入口，避免一次性引入自动倒计时播放和跨季队列重构。

## What I Already Know

* 视频模块已经支持 Emby 认证、库浏览、详情页、直接播放、播放进度上报、继续观看、Next Up、搜索、排序筛选、收藏/已看状态、剧集季/分集浏览。
* 研究材料 `.trellis/tasks/archive/2026-06/06-27-media-feature-parity-round10/research/emby-yamby-features.md` 把 `Autoplay Next Episode` 和 `Up Next / End-of-Episode Banner` 列为 Tier 2 功能。
* `VideoDetailScreen` 已经加载当前季 `episodes`，且点击分集时能构造临时 `VideoItem` 并获取 `VideoPlaybackInfo`。
* `VideoPlayerScreen` 当前只有播放/暂停、seek、倍速、音轨/字幕、字幕缩放和关闭，没有下一集入口。
* `MainActivity` 持有 `VideoPlaybackEngine` 和 `showVideoPlayer` 状态，是串联详情页播放和播放器命令的地方。

## Requirements

* 从电视剧详情页播放分集时，记录当前季内的分集队列和当前分集位置。
* 视频播放器展示“下一集”入口；只有存在下一集时才启用。
* 点击“下一集”时加载下一集的 `PlaybackInfo`，调用现有 `VideoPlaybackEngine.play(...)` 开始播放，并更新当前队列位置。
* 下一集播放失败时不清空当前播放状态，错误应在播放器可见位置提示。
* 从电影、单集搜索结果、继续观看、Next Up 等没有队列上下文的入口播放时，不显示或禁用下一集入口。
* 保持现有播放进度上报、倍速、音轨/字幕、手势控制行为不变。

## Acceptance Criteria

* [x] 电视剧详情页分集点击会把同季分集队列传到上层播放状态。
* [x] 播放器在有下一集时显示可点击的 Next Episode 控件。
* [x] 播放器在无下一集或无队列上下文时不提供可点击下一集。
* [x] 点击 Next Episode 会请求下一集 `PlaybackInfo` 并开始播放下一集。
* [x] 点击 Next Episode 后队列位置前进，连续点击可继续播放后续集。
* [x] 下一集加载失败会显示错误，不停止当前视频。
* [x] 编译、相关测试和 lint 通过。

## Definition of Done

* Tests added/updated for pure queue next-state behavior or a focused unit-testable helper.
* `compileDebugKotlin`, `testDebugUnitTest`, and `lintDebug` run sequentially on Windows.
* Emby integration spec updated if a reusable next-episode playback contract is established.
* Changes committed before finish-work.

## Technical Approach

Introduce a small app-facing queue model instead of changing Emby DTOs:

* Add a lightweight `VideoEpisodeQueue`/`VideoEpisodeQueueItem` model or equivalent helper in the UI/domain layer.
* In `VideoDetailScreen`, add an optional callback for episode playback that includes the clicked episode plus the current `episodes` list and index.
* In `MainActivity`, store the active episode queue and expose `hasNextEpisode`/`onPlayNextEpisode` to `VideoPlayerScreen`.
* Add a compact Next Episode control to `VideoPlayerScreen`, wired to the new callback and disabled when no next item exists.
* Keep all `PlaybackInfo` loading in repository calls; UI must not call Retrofit directly.

## Decision (ADR-lite)

**Context**: Full Emby-style autoplay requires end-of-playback detection, countdown UI, dismiss behavior, and likely cross-season queue handling. The current app has the current season's episode list in `VideoDetailScreen` but no persistent player queue.

**Decision**: Implement a manual same-season next-episode control first. Use the existing loaded episode list as queue context and reuse `EmbyRepository.getPlaybackInfo(...)` plus `VideoPlaybackEngine.play(...)`.

**Consequences**: This gives immediate追剧 ergonomics with small blast radius. Later automatic countdown can reuse the same queue state and add a playback-ended trigger without reworking episode mapping again.

## Out of Scope

* Automatic countdown autoplay at end of episode.
* Cross-season next episode lookup when the current season ends.
* Skip intro/credits.
* Changing Emby API contracts or adding a new backend endpoint.
* General playlist/queue UI for movies and arbitrary videos.

## Technical Notes

* Likely files:
  * `app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt`
  * `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`
  * `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
  * optional focused test under `app/src/test/java/com/nordic/mediahub/...`
* Existing pattern: music player already has `onSeekToNext` / `onSeekToPrevious` callbacks in `MusicPlayerScreen`, but video should start with only next episode because previous/cross-queue semantics are not yet defined.
* Applicable spec: `.trellis/spec/backend/emby-integration.md`.
