# 媒体功能完善第五轮

## Goal

继续参考音流、Audiobookshelf 官方移动端、Emby/Yamby 的成熟媒体体验，做第五轮高价值功能完善。第四轮已经补齐了音乐列表筛选、歌词控制、AudiobookShelf 继续收听、Emby 音轨/字幕选择和基础手势；本轮聚焦更耐用的离线/恢复能力和更真实的播放控制，而不是再做纯展示型入口。

## What I Already Know

- 当前仓库干净，上一轮提交已经归档：`8738be3 feat(media): add round four playback polish`、`3bb3d71 docs(spec): record media resume and track contracts`。
- Music:
  - 已有 Navidrome 曲库、专辑/歌曲/歌手/歌单、收藏、歌单 CRUD、播放队列、智能电台、播放历史、EQ、歌词控制、离线单曲下载。
  - `MusicDownloadManager` 下载文件到 app 外部音乐目录，并能根据已下载文件恢复 `DownloadState.DOWNLOADED`。
  - 现有恢复依赖当前曲库元数据回填；如果用户离线启动或曲库未加载，已下载歌曲只会有文件状态，没有可展示/可播放的完整 `NavidromeSong` 元数据列表。
- Audiobooks:
  - 已有 Audiobookshelf 登录、书库/详情、播放 session、进度同步、关闭 session、章节、倍速、跳转、睡眠定时、继续收听、上次播放入口。
  - 尚未看到有声书离线下载、多文件下载进度、取消/删除、或离线播放入口。
- Video:
  - 已有 Emby 登录/目录、视频库筛选、PlaybackInfo、直接播放、进度上报、详情页、剧集季/集浏览、播放器、倍速、音轨/字幕选择、字幕大小、基础拖动手势。
  - `VideoPlaybackEngine.subtitleOffsetSeconds` 当前是状态和 UI 控制；需要确认/实现真实 Media3 字幕时间偏移，或者明确禁用/改文案，避免“有控件但不生效”。
  - 尚未看到视频串流质量/direct-stream/transcode 偏好，也未看到 PIP/Live TV。

## Assumptions (temporary)

- 本轮仍然走三域媒体功能完善，但需要比第四轮更聚焦，避免一次性实现过大的离线/转码/直播范围。
- 优先选择能在本地验证、能加 repository/helper 测试、并且能让体验明显接近参考应用的功能。
- 不改 Navidrome、Audiobookshelf、Emby 的整体 repository 架构。

## Candidate Directions

### Approach A: Durable Offline & Resume Polish (Recommended)

- Music: persist downloaded song metadata beside files, so downloaded music can render/play after app restart even before full library refresh.
- Audiobooks: add an MVP audiobook offline manager for the active book/session tracks with progress, cancel/delete, and local playback when downloaded.
- Video: audit subtitle offset and either make it real through Media3-compatible subtitle configuration/path or remove/disable the offset control with accurate state.

Pros:
- Directly improves reliability and offline/resume behavior, which reference apps treat as core.
- Builds on existing code instead of opening new provider surfaces.
- Finds and fixes one known weak spot from round 4: subtitle offset control may be UI-only.

Cons:
- Audiobook offline download touches multiple layers and may need careful session/progress behavior.
- Full Audiobookshelf offline parity can get large, so MVP must be narrow.

### Approach B: Playback Policy & Quality Controls

- Video: add Emby direct-stream/transcode quality preference, max bitrate, and a clear direct-play fallback/error policy.
- Music: add ReplayGain/loudness normalization preference if Navidrome/Subsonic metadata exposes enough gain fields.
- Audiobooks: add playback behavior preferences such as skip durations and resume policy.

Pros:
- Strong fit for Emby/Yamby and power-user playback expectations.
- Mostly control/policy work rather than large file management.

Cons:
- Emby transcoding policy can be API-sensitive and harder to verify without real server variants.
- Music ReplayGain depends on available metadata; may require deeper Subsonic compatibility research.

### Approach C: Library Management & Import Polish

- Music: add m3u import/export or internet radio/m3u8 station management.
- Audiobooks: add bookmarks/listening notes.
- Video: add library visibility preferences and richer filters.

Pros:
- Broad visible product surface.
- Several items are useful without low-level playback changes.

Cons:
- Less foundational than offline/playback correctness.
- Bookmarks were explicitly excluded from round 4, so should only return if user wants that priority now.

## Requirements

- Implement Approach A as the working MVP unless the user redirects:
  - Music: persist downloaded song metadata beside local files so downloaded songs can render and play after app restart before a full library refresh.
  - Video: audit subtitle offset. If the app cannot actually apply offset through the current Media3 path, remove or disable the offset adjustment UI and keep only controls that affect playback.
  - Audiobook: document the offline-download contract and keep full audiobook offline download out of this coding slice unless a safe narrow implementation path emerges during coding.
- Preserve existing round 4 behavior.
- Add tests for new persistence, mapping, or pure helper logic where practical.
- Keep new behavior aligned with existing specs:
  - `.trellis/spec/backend/database-guidelines.md`
  - `.trellis/spec/backend/quality-guidelines.md`
  - `.trellis/spec/backend/navidrome-integration.md`
  - `.trellis/spec/backend/audiobookshelf-integration.md`
  - `.trellis/spec/backend/emby-integration.md`

## Acceptance Criteria

- [x] `MusicDownloadManager` writes metadata when a song download completes.
- [x] `MusicDownloadManager.restoreDownloadState()` restores downloaded songs with metadata when sidecar metadata exists.
- [x] Existing metadata repair from live library data still works for older downloads that lack sidecar metadata.
- [x] Deleting a download removes both media file and metadata sidecar.
- [x] Video subtitle offset controls do not claim to affect playback unless they actually do.
- [x] Audiobook offline download remains explicitly out of scope for this slice, with the next contract captured for future work.
- [x] New behavior has repository/helper tests where practical.
- [x] `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
- [x] `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
- [x] `.\gradlew.bat :app:lintDebug --no-daemon` passes.
- [x] `.\gradlew.bat :app:assembleDebug --no-daemon` considered if playback, manifest, resources, or packaging-affecting code changes.
- [x] Specs updated if new persistence/API/playback contracts are introduced.

## Definition of Done

- Feature slice implemented end to end.
- Existing behavior from round 4 is preserved.
- Tests and Gradle gates pass.
- Trellis task is archived and journaled after code commits.

## Decision (ADR-lite)

**Context**: Round 4 added several visible controls. The next parity gap should improve durability and avoid controls that look functional but are not actually wired to playback behavior.

**Decision**: Start round 5 with a narrow Approach A slice: durable music download metadata and truthful video subtitle controls. Treat full AudiobookShelf offline download as the next sub-slice after its session/download/progress contract is specified.

**Consequences**: This does not complete the entire broader objective, but it makes one real offline/resume capability stronger and prevents misleading playback UI. Audiobook offline remains a documented follow-up rather than an unsafe partial implementation.

## Open Questions

- None blocking for the working MVP. User can still redirect to Approach B or C before implementation finishes.

## Out of Scope

- Replacing Navidrome, Audiobookshelf, or Emby repository architecture.
- Adding Plex/Jellyfin video support.
- Public-directory storage requiring broad storage permissions.
- Full Live TV implementation.
- Full server-side transcoding implementation unless Approach B is selected and scoped narrowly.
- Full AudiobookShelf offline audiobook download, including multi-file download queue, local session reconstruction, and offline progress reconciliation.

## Technical Notes

- Task directory: `.trellis/tasks/06-27-media-feature-parity-round5`.
- Key music files inspected: `MusicDownloadManager.kt`, `MusicScreenV2.kt`, `MusicPlaybackEngine.kt`, `NavidromeRepository.kt`, `NavidromeApi.kt`.
- Key audiobook files inspected: `AudiobookShelfApi.kt`, `AudiobookShelfRepository.kt`, `AudiobookScreen.kt`, `AudiobookPlaybackEngine.kt`.
- Key video files inspected: `EmbyApi.kt`, `EmbyRepository.kt`, `VideoPlayerScreen.kt`, `VideoPlaybackEngine.kt`, `VideoScreen.kt`, `VideoDetailScreen.kt`.
