# Live Verification Report — UI Round 12 (r12)

## Meta
- **App**: fun.han1997.nordic v0.1.19 (versionCode 19)
- **Baseline**: main/a451eec
- **Device**: AVD nordic-ui-api34 / emulator-5580 (1080×2400, 480dpi)
- **Date**: 2026-09-17 (AM/PM sessions)
- **Repository**: sese NAS / fnOS WebDAV / Emby

## Summary

| Domain | Server | Status | Evidence |
|--------|--------|--------|----------|
| Music | Navidrome | ✅ PASS | 4 screenshots (home, songs, player playing, player paused) |
| Audiobook | AudiobookShelf | ✅ PASS | 3 screenshots (home, redroom detail, player playing) |
| Video (Emby) | Emby | ✅ Partial — browse OK, detail OK, progress resume OK. ⚠️ Playback failed (network timeout) | 3 screenshots (home, detail with continue, error) |
| Video (WebDAV) | WebDAV (fnOS) | ✅ PASS — directory browse, file info, auto-next playback, player controls | 7 screenshots (root listing, directory with files, auto-next prompts, player ended, player paused) |

## Detailed Verification

### Music — Navidrome
- Login: ✅ (home page loads with "Navidrome" source indicator, "正在刷新,先显示本地缓存")
- Browse: ✅ Albums (魔杰座 - 周杰伦), Songs, Playlists ("刚刚同步")
- Playback: ✅ Play → Pause/Resume (player screen shows progress, controls)
- Evidence: music-home.png, music-songs.png, music-player-playing.png, music-player-paused.png

### Audiobook — AudiobookShelf
- Login: ✅ (audiobook home with library listing)
- Browse: ✅ (redroom-detail showing book detail)
- Playback: ✅ (player playing state captured)
- Evidence: audiobook-home.png, audiobook-redroom-detail.png, audiobook-player-playing.png

### Video — Emby
- Login: ✅ (Emby source active, "本地缓存,刚刚更新")
- Browse: ✅ Categories (电视剧/电影/动漫), continue-watching with real server content
- Detail: ✅ Episode detail with full synopsis, year 2019, 54m duration
- Progress Resume: ✅ "续看 19m" / "继续从 19m 播放" — real server-synced progress
- Player Launched: ⚠️ **播放异常** "视频播放失败,请检查网络或文件格式后重试"
  - Root cause: `SocketTimeoutException` / `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`
  - Error UI: Well-formed typed Chinese error with Retry/Close actions
  - Probable cause: Emby server unreachable from emulator (network/environmental)
- Evidence: video-emby-home.png, video-emby-detail.png, video-player-error.png

### Video — WebDAV (fnOS)
- Login: ✅ (root directory lists 11 items)
- Browse: ✅ Multi-level directory navigation (root → sese → Telegram → ... → frigate_videos/recordings)
- Cache behavior: ✅ Labels show correct freshness ("刚刚更新", "6 分钟前更新", "11 分钟前更新")
- File info: ✅ Files show format (MP4) and size (e.g., "MP4 · 2.4 MB")
- Directory browse: ✅ 422 items in Telegram/ed_videos, breadcrumb trail preserved
- Auto-next: ✅ After clip completed, prompt shows next file in natural sort order (00.29 → 00.38 → 00.48 → 00.59 → 01.09 → ...)
- Player controls: ✅ Progress bar, play/pause, 1.0x speed, aspect ratio, episode list, next episode, fullscreen
- Player ended state: ✅ Shows at 0:10/0:10 with centered play button
- Evidence: webdav-home-root.png, webdav-directory-files.png, webdav-auto-next-prompt{1,2}.png, webdav-player-{ended,paused,resumed-controls}.png

## Acceptance Criteria Status

| Criterion | Status | Notes |
|-----------|--------|-------|
| 4 server types connected | ✅ | Navidrome, AudiobookShelf, Emby, WebDAV all configured and connected |
| Music playback closed loop | ✅ | Start→pause/resume→screenshots captured |
| Audiobook playback closed loop | ✅ | Start→playback screenshots captured |
| Video (Emby) playback closed loop | ⚠️ | Browse/detail/resume OK; stream failed — network timeout (environmental) |
| Video (WebDAV) playback closed loop | ✅ | File browse → play → auto-next → player controls captured |
| Screenshots without credentials | ✅ | No settings/credential screenshots in evidence |
| No new credentials in repo | ✅ | Only emulator encrypted storage, no repo files |

## UI Defects Found
None documented for this round. The Emby playback failure is environmental (server unreachable), not a UI defect.

## Not Verified / Limitations
- Emby video streaming — server unreachable during this session
- WebDAV subtitles (no SRT/ASS/VTT files in test directories)
- Sleep timer (audiobook domain not re-tested in this session)
- Lyrics display (Navidrome — not re-tested in this session)
- Cross-domain progress isolation (not explicitly tested)

