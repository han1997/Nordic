# 媒体功能完善第七轮

## Goal

继续参考音流、AudiobookShelf 官方移动端、Emby/Yamby 的成熟体验完善 Nordic Media Hub。本轮聚焦视频侧的“继续观看”工作流：从 Emby 服务端读取可续播视频，在视频首页展示横向续播入口，并从服务端保存的播放位置开始播放。

## What I Already Know

- 工作树在本轮开始前干净。
- Round 6 已完成 local-first AudiobookShelf 播放书签。
- 总目标要求音乐参考音流、有声书参考 AudiobookShelf 官方、视频参考 Emby/Yamby。
- 视频侧已有 Emby/Jellyfin 类服务配置、媒体浏览、详情、播放、播放进度上报、剧集季/集、音轨/字幕选择、字幕缩放。
- Emby 官方 REST API 有 `GET /Users/{UserId}/Items/Resume`，响应 item 的 `UserData` 包含 `PlaybackPositionTicks`、`PlayedPercentage`、`Played`、`LastPlayedDate`。

## Assumptions (temporary)

- 继续观看使用 Emby 服务端状态，不额外做本地 fallback 缓存。
- 不在本轮做跨服务大改、标记已看/未看、收藏切换或全局导航重构。

## Open Questions

- None.

## Requirements (evolving)

- Add Emby continue-watching fetch via `GET /Users/{userId}/Items/Resume`.
- Map resume `UserData` into app-facing progress fields.
- Add `resumeItems` to the video catalog refresh path.
- Render a compact “继续观看” shelf above the current library item list when resumable items exist.
- Tapping a resume item opens the normal detail/play path and starts playback from `PlaybackPositionTicks`.
- 保持已有播放、进度上报和详情页行为不回退。

## Acceptance Criteria (evolving)

- [x] PRD 明确本轮视频功能 MVP。
- [x] `GET /Users/{userId}/Items/Resume` is called with video filters and auth token.
- [x] Resume item progress maps `PlaybackPositionTicks` to seconds and preserves played percentage.
- [x] Video home shows a continue-watching shelf only when resumable items exist.
- [x] Playing a resume item starts at the stored absolute position.
- [x] Existing library browsing, details, seasons/episodes, playback reporting, audio/subtitle controls still compile.
- [x] 若变更服务端契约或本地持久化，更新 `.trellis/spec/`。
- [x] `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
- [x] `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes when tests are added/affected.
- [x] `.\gradlew.bat :app:lintDebug --no-daemon` passes.

## Definition of Done

- Tests added or updated for non-trivial repository/helper behavior.
- Lint/type-check gates pass sequentially on Windows.
- Docs/specs updated if a new executable contract is introduced.
- Changes committed before finish-work.

## Out of Scope (temporary)

- Full video offline download.
- Mark played/unplayed, favorite toggles, playlists, or full history page.
- Local resume fallback cache when Emby is unreachable.
- Broad library filtering/sorting redesign.
- Large UI redesign unrelated to the chosen video MVP.

## Research References

- [`research/emby-resume.md`](research/emby-resume.md) — Emby official resume endpoint and `UserData` fields for continue-watching.

## Decision (ADR-lite)

**Context**: Emby/Yamby-style video apps surface unfinished media prominently and start playback from server-saved progress. Nordic already reports playback progress to Emby, but the video home does not read that progress back.

**Decision**: Use the official Emby `Items/Resume` endpoint for this MVP. Keep resume server-backed and render it as a compact home shelf. Pass resume position through `VideoItem` -> `VideoPlaybackInfo` -> `VideoPlaybackEngine.play()`.

**Consequences**: Continue watching works across devices through Emby server state. The app does not maintain a local fallback history in this slice.

## Technical Notes

- Task directory: `.trellis/tasks/06-27-media-feature-parity-round7`.
- Relevant starting files to inspect:
  - `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
  - `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`
  - `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`
  - `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`
  - `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`
  - `.trellis/spec/backend/emby-integration.md`
- Applicable specs:
  - `.trellis/spec/backend/index.md`
  - `.trellis/spec/backend/directory-structure.md`
  - `.trellis/spec/backend/error-handling.md`
  - `.trellis/spec/backend/quality-guidelines.md`
  - `.trellis/spec/backend/emby-integration.md`
