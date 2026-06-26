# 媒体功能完善：音乐·有声书·视频

## Goal

Improve Nordic Media Hub's music, audiobook, and video experiences by adding 11 targeted features across three batches, referencing mature client patterns: 音流/StreamMusic for music, official AudiobookShelf for audiobooks, and Emby/Yamby for video.

## What I Already Know

* Music (Navidrome) has the richest feature set: albums, songs, artists, playlists, search, lyrics, queue management, repeat modes, cached data. Maturity ~60-70%.
* Audiobook (AudiobookShelf) has library browsing, item detail, progress sync, chapter list, and a player screen. Maturity ~30-40%.
* Video (Emby) now has playback MVP (direct stream, play/pause/seek/close, error surfacing) but lacks browsing depth and player controls. Maturity ~15-20%.
* Project uses Kotlin, Jetpack Compose, Retrofit/OkHttp, Coil, DataStore, Media3.
* Repository maps API data, playback engines own Media3 state, UI renders state and sends commands.

## Assumptions

* Three areas will be implemented in three sequential batches (music → audiobook → video).
* Each batch is implemented, tested, and committed before the next starts.
* Features requiring new platform integrations (Android Auto, Chromecast, offline downloads) remain deferred.

## Open Questions

* None. User confirmed all features and batch strategy on 2026-06-26.

## Requirements

### Batch 1: Music (音流 reference)

* **收藏/星标**: Add Subsonic `star`/`unstar` API calls in NavidromeRepository; add star/favorite toggle UI on albums, songs, artists; add a "我的收藏" (Starred) entry on the home screen showing starred items.
* **随机播放 Shuffle**: Add shuffle mode toggle in MusicPlayerScreen (alongside existing repeat toggle); shuffle the current queue when activated via MusicPlaybackEngine; show shuffle state in UI.
* **播放列表管理**: Add Subsonic `createPlaylist`/`updatePlaylist`/`deletePlaylist` API calls; add playlist creation from selected songs; add song add/remove from playlists; add playlist delete in the playlists tab.

### Batch 2: Audiobook (AudiobookShelf official reference)

* **播放速度**: Add playback speed selector (0.5x/0.75x/1x/1.25x/1.5x/2x/3x) to AudiobookPlayerScreen; wire through AudiobookPlaybackEngine to Media3 player speed.
* **快退/快进跳秒**: Add rewind-10s and forward-30s buttons to AudiobookPlayerScreen; these are instant-seek shortcuts, not chapter jumps.
* **章节跳转**: Add previous-chapter / next-chapter buttons in AudiobookPlayerScreen; make chapter list tappable to jump to a chapter start position.
* **睡眠定时器**: Add sleep timer UI in AudiobookPlayerScreen (15/30/45/60 min options + "end of chapter"); when timer fires, pause playback; show remaining time in UI.

### Batch 3: Video (Emby/Yamby reference)

* **视频详情页**: Intercept video item taps to show a detail screen before playback; display title, overview, year, duration, type, and a "播放" button; the detail screen is the launch point for playback.
* **倍速播放**: Add playback speed selector (0.5x/0.75x/1x/1.25x/1.5x/2x) in VideoPlayerScreen controls; wire through VideoPlaybackEngine to ExoPlayer speed.
* **播放进度上报**: Add Emby `PlaybackStart`/`PlaybackProgress`/`PlaybackStop` API calls; report at start, periodically during playback, and on stop/exit; enables "continue watching" on the Emby home and cross-device resume.
* **TV 分季分集**: When item type is "Series", show seasons; when season is selected, show episodes; Episode items tap to play. Change EmbyRepository to support `getSeasons` and `getEpisodes` API calls.

## Acceptance Criteria

* [ ] Star/unstar toggles work on albums, songs, artists; "我的收藏" section appears on home
* [ ] Shuffle mode toggles and randomizes the queue; state persists during session
* [ ] Playlists can be created, edited (add/remove songs), and deleted
* [ ] Audiobook playback speed can be changed and persists during session
* [ ] Rewind 10s / forward 30s buttons seek correctly
* [ ] Previous/next chapter buttons jump to correct chapter boundaries
* [ ] Chapter list items are tappable and seek to chapter start
* [ ] Sleep timer pauses playback after the chosen duration or chapter end
* [ ] Video detail page shows metadata and a play button; tapping play starts playback
* [ ] Video playback speed can be changed during playback
* [ ] Playback progress reports to Emby server at start, periodically, and on stop
* [ ] Series items show season list; seasons show episode list; episodes play on tap
* [ ] Repository tests added/updated for new API calls and domain mapping
* [ ] Compile, unit tests, and lint pass

## Definition of Done

* Tests added/updated for behavior changes
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes
* `.\gradlew.bat :app:lintDebug --no-daemon` passes when UI/API behavior changes
* Specs updated if new integration contracts or project conventions emerge

## Technical Approach

Each batch follows the same pattern: extend API → extend repository → extend playback engine (if applicable) → extend UI → add tests.

### Batch 1: Music

1. Extend NavidromeApi with `star`/`unstar`/`getStarred2`/`createPlaylist`/`updatePlaylist`/`deletePlaylist` endpoints
2. Add NavidromeRepository methods for star/unstar, starred fetch, playlist CRUD
3. Add shuffle toggle in MusicPlaybackEngine (toggle shuffle on Media3 controller)
4. Update MusicPlayerScreen with shuffle button; add star toggle on album/song/artist items
5. Add starred section to MusicHomeSections; add playlist management UI
6. Add repository tests for new endpoints

### Batch 2: Audiobook

1. Add speed control to AudiobookPlaybackEngine (set playerParameters with speed)
2. Add seek-by-seconds helpers to AudiobookPlaybackEngine
3. Add chapter-jump helpers to AudiobookPlaybackEngine (find current chapter, seek to next/prev)
4. Add sleep timer logic (CoroutineScope timer + chapter-end detection)
5. Update AudiobookPlayerScreen with speed selector, skip buttons, chapter controls, sleep timer
6. Add pure-logic tests for chapter seeking and sleep timer

### Batch 3: Video

1. Add PlaybackStart/Progress/Stop endpoints to EmbyApi
2. Add reporting methods to EmbyRepository; wire periodic reporting in VideoPlaybackEngine
3. Add getSeasons/getEpisodes endpoints to EmbyApi and EmbyRepository
4. Create VideoDetailScreen composable; wire in MainActivity between VideoScreen and player
5. Add speed selector to VideoPlayerScreen; wire in VideoPlaybackEngine
6. Add season/episode browsing in VideoScreen/VideoDetailScreen
7. Add repository tests for progress reporting, season/episode mapping

## Out of Scope (explicit)

* Offline downloads, Android Auto, Chromecast, equalizer, replay gain, widgets, podcasts
* Replacing Navidrome, AudiobookShelf, or Emby integrations
* Large visual redesign without functional benefit
* HLS/transcoding fallback, subtitles, PIP, gestures for video
* Gapless playback, crossfade, Last.fm scrobbling for music
* Bookmarks with notes, collections/series page for audiobooks
* Live TV, danmaku, STRM, parental controls, multi-user picker for video

## Technical Notes

* Music key files: MusicScreenV2.kt, MusicHomeSections.kt, MusicPlayerScreen.kt, MusicQueueSheet.kt, MusicPlaybackEngine.kt, NavidromeRepository.kt, NavidromeApi.kt
* Audiobook key files: AudiobookScreen.kt, AudiobookPlayerScreen.kt, AudiobookPlaybackEngine.kt, AudiobookShelfRepository.kt, AudiobookShelfApi.kt
* Video key files: VideoScreen.kt, VideoPlayerScreen.kt, VideoPlaybackEngine.kt, EmbyRepository.kt, EmbyApi.kt
* Previous research archived: `.trellis/tasks/archive/2026-06/06-26-media-feature-parity-enhancements/research/`
