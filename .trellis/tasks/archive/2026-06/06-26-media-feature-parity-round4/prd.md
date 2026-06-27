# 媒体功能完善第四轮

## Goal

参考音流、Audiobookshelf 官方移动端、Emby/Yamby 的成熟媒体体验，继续完善 Nordic Media Hub 的音乐、有声书、视频功能。目标不是一次性复刻所有功能，而是选一批高价值、可验证、与现有架构贴合的功能完成闭环。

## What I Already Know

- 当前仓库已完成三轮媒体功能增强，工作树干净。
- 音乐已有 Navidrome/Subsonic 曲库、专辑/歌曲/歌手/歌单、搜索、歌词、收藏、歌单 CRUD、队列、离线下载、智能电台、播放历史、EQ。
- 有声书已有 Audiobookshelf 登录、书库/详情、播放 session、进度同步、关闭 session、倍速、跳转、章节、睡眠定时。
- 视频已有 Emby 登录/目录、视频库过滤、PlaybackInfo、直接播放、播放器、倍速、播放进度上报、详情页、剧集季/集浏览。
- 本轮用户给出的参考方向：
  - 音乐参考音流。
  - 有声书参考 Audiobookshelf 官方。
  - 视频参考 Emby、Yamby。

## Research References

- `research/music-streammusic.md` - 音流侧重多服务 NAS 音乐、歌词/缓存/增量同步/本地搜索/播放体验 polish。
- `research/audiobookshelf-official.md` - Audiobookshelf 官方生态侧重继续收听、离线、进度同步、章节、锁屏/耳机控制与快速恢复。
- `research/video-emby-yamby.md` - Emby/Yamby 侧重字幕/音轨选择、字幕样式、手势、PIP、库过滤、直播和播放策略。

## Requirements

### MVP: Broad Polish Round

- Music:
  - Add local list search/filter for large music lists: all songs, albums, artists, playlists/detail songs.
  - Add lyrics display controls: prefer synced/plain lyrics when both exist, lyrics offset, large text toggle.
  - Add playlist-only refresh so users do not need a full music-library refresh for playlist changes.
- Audiobooks:
  - Add Continue Listening shelf based on Audiobookshelf progress data.
  - Restore the last active audiobook/player entry after app restart when possible.
- Video:
  - Map Emby media streams from PlaybackInfo and expose audio/subtitle track selection.
  - Add subtitle scale and offset controls.
  - Add basic player gestures: horizontal seek and vertical volume/brightness intent if Android APIs allow safe implementation.

## Decision (ADR-lite)

**Context**: The user wants another parity round using three reference products: Stream Music / 音流 for music, Audiobookshelf official app for audiobooks, and Emby/Yamby for video.

**Decision**: Implement the broad three-domain MVP in one task instead of focusing on only one media domain.

**Consequences**: This creates a wider cross-layer change set, so implementation should proceed in small slices by media domain and keep each slice testable. Larger items such as full audiobook offline download, video Live TV, and transcoding quality policy remain out of scope for this round.

## Confirmation

- User confirmed the broad three-domain MVP on 2026-06-27.
- User chose to exclude audiobook bookmarks/listening notes from this round on 2026-06-27.

## Candidate Backlog

- Music later:
  - ReplayGain/loudness normalization.
  - Duplicate song detection.
  - Album artist support.
  - m3u import/export.
  - m3u8/radio station playback.
- Audiobook later:
  - Full offline audiobook download with multi-file progress/cancel/delete.
  - Podcast episode support.
  - Bookmarks/listening notes, either local-first or server-backed after API contract is confirmed.
- Video later:
  - PIP mode.
  - Library visibility preferences.
  - Stream quality/direct-stream/transcode preferences.
  - Live TV.

## Acceptance Criteria (Evolving)

- [ ] Music list search/filter works without network refetch and preserves playback callbacks.
- [ ] Music lyrics controls affect current player display and survive recomposition.
- [ ] Playlist-only refresh updates playlists without full album/song/artist sync.
- [ ] Audiobook home shows Continue Listening when progress data exists.
- [ ] Last active audiobook can be resumed after app restart when data is available.
- [ ] Emby PlaybackInfo mapping exposes audio/subtitle streams.
- [ ] Video player can switch audio/subtitle tracks or clearly disables unavailable tracks.
- [ ] Subtitle scale/offset controls are visible and affect playback where Media3 supports them.
- [ ] Repository/unit tests cover new API/data mapping behavior.

## Definition of Done

- Tests added/updated for repository mapping, persistence helpers, and pure playback/UI logic where practical.
- `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
- `.\gradlew.bat :app:lintDebug --no-daemon` passes.
- `.\gradlew.bat :app:assembleDebug --no-daemon` considered if playback service, manifest, or packaging-affecting code changes.
- Specs updated if new API or persistence contracts are introduced.

## Open Questions

- None.

## Out of Scope (Temporary)

- Replacing Navidrome, Audiobookshelf, or Emby repository architecture.
- Adding Plex/Jellyfin video support.
- Public-directory storage requiring broad storage permissions.
- Server-side transcoding implementation unless explicitly chosen.
- Audiobook bookmarks/listening notes.

## Technical Notes

- Key music files: `MusicScreenV2.kt`, `MusicPlayerScreen.kt`, `MusicHomeSections.kt`, `MusicPlaybackEngine.kt`, `NavidromeRepository.kt`, `NavidromeApi.kt`.
- Key audiobook files: `AudiobookScreen.kt`, `AudiobookPlayerScreen.kt`, `AudiobookShelfRepository.kt`, `AudiobookShelfApi.kt`, `AudiobookPlaybackEngine.kt`.
- Key video files: `VideoScreen.kt`, `VideoDetailScreen.kt`, `VideoPlayerScreen.kt`, `VideoPlaybackEngine.kt`, `EmbyRepository.kt`, `EmbyApi.kt`.
- Applicable specs: `.trellis/spec/backend/index.md`, `navidrome-integration.md`, `audiobookshelf-integration.md`, `emby-integration.md`, `quality-guidelines.md`, `database-guidelines.md`, `error-handling.md`.
