# 媒体功能完善第三轮：音乐

## Goal

Polish Nordic Media Hub's music experience by adding five high-value features referencing 音流/StreamMusic: offline download, enhanced queue management, equalizer, smart radio, and play history.

## What I Already Know

* Music already has: albums, songs, artists, playlists, search, lyrics, queue management, repeat/shuffle, star/favorite, playlist CRUD, cached data via DataStore.
* Uses Navidrome/Subsonic API, Media3 ExoPlayer with OkHttp + 100MB LRU disk cache.
* MusicPlaybackEngine wraps a Media3 MediaController connected to MusicPlaybackService (foreground MediaSessionService).
* App private directory chosen for downloads (no MANAGE_EXTERNAL_STORAGE permission needed).
- Subsonic API supports `download` endpoint for raw file download and `scrobble` endpoint for play tracking.

## Assumptions

- Downloads go to app private external directory (context.getExternalFilesDir), no extra permissions needed.
- Smart radio uses Subsonic `getSimilarSongs` or `getRandomSongs` as the seed mechanism.
- Equalizer uses Android's system Equalizer audio effect session integration.
- Play history is persisted locally via DataStore; Last.fm scrobbling is deferred.

## Open Questions

* None. User confirmed all 5 features and app private directory storage on 2026-06-26.

## Requirements

### Feature 1: Download / Offline Mode

- Add Subsonic `download` API endpoint: `GET /download?id={songId}` — returns raw audio file bytes.
- Create `MusicDownloadManager` that manages download tasks via OkHttp, stores files in app private external directory (`Music/` subdirectory).
- Track download state per song: not_downloaded, downloading (with progress), downloaded.
- Add download/delete toggle on song items (download icon button).
- Add a "已下载" (Downloaded) section on the home screen showing offline songs.
- When playing a downloaded song, use the local file URI instead of the streaming URL.
- Songs downloaded are available for offline playback without network.

### Feature 2: Enhanced Queue Management

- The existing `MusicQueueSheet` bottom sheet shows the queue with play-next, remove, and clear-upcoming.
- Add drag-to-reorder support in the queue sheet (long-press + drag handles).
- Add "add to queue" action from library screens (songs, albums) — append to end of current queue.
- Show mini now-playing bar on library screens when music is playing (persistent bottom bar with song title, play/pause, next).

### Feature 3: Equalizer / Audio Effects

- Integrate Android system `Equalizer` audio effect via Media3 `AudioEffect` session.
- Add an EQ screen/dialog accessible from MusicPlayerScreen.
- Show preset list (Normal, Pop, Rock, Jazz, Classical, Bass Boost, etc.) and a custom band slider view.
- Use the active Media3 audio session ID to attach the Equalizer.

### Feature 4: Smart Radio / Auto-Mix

- Add Subsonic `getSimilarSongs` endpoint: `GET /getSimilarSongs?id={songId}&count=50`.
- Add "智能电台" button on song items / now-playing screen.
- When tapped: fetch similar songs and enqueue them after the current song.
- Fallback: if `getSimilarSongs` returns empty, use `getRandomSongs` instead.
- Show a notification that radio mode started with song count.

### Feature 5: Play History / Scrobbling

- Add Subsonic `scrobble` endpoint: `GET /scroble?id={songId}&submission=true` (marks as played on server).
- Call `scrobble(submission=true)` when a song has played for >50% of its duration or >4 minutes.
- Persist play history locally in DataStore (song id, timestamp, play count).
- Add "最近播放" (Recently Played) section on the home screen showing last 20 played songs.
- Call `scrobble(submission=false)` on song start to mark "now playing" on the Navidrome server.

## Acceptance Criteria

- [ ] Downloading a song stores it locally; playing it offline works without network
- [ ] Download state icon (downloaded/downloading/not) visible on song items
- [ ] "已下载" section on home shows downloaded songs
- [ ] Queue sheet supports drag-to-reorder
- [ ] "Add to queue" action available from song items in library
- [ ] Mini now-playing bar visible on library screens when music is active
- [ ] EQ dialog opens from player screen with preset list and custom band sliders
- [ ] Selecting an EQ preset applies audible change to playback
- [ ] "智能电台" button fetches similar songs and enqueues them
- [ ] Play history persisted locally; "最近播放" section on home
- [ ] Scrobble (now-playing + submission) calls sent to Navidrome server
- [ ] Repository tests for new API endpoints

## Definition of Done

* Tests added/updated for behavior changes
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes
* `.\gradlew.bat :app:lintDebug --no-daemon` passes when UI/API behavior changes

## Out of Scope

* Last.fm scrobbling integration (defer to future task)
* Download to public directory / MANAGE_EXTERNAL_STORAGE
* Android Auto, Chromecast, gapless playback, crossfade, replay gain
* Replacing or changing the Navidrome integration architecture
- Large visual redesign

## Technical Notes

* Music key files: MusicScreenV2.kt, MusicHomeSections.kt, MusicPlayerScreen.kt, MusicQueueSheet.kt, MusicPlaybackEngine.kt, NavidromeRepository.kt, NavidromeApi.kt
- MusicPlaybackService.kt hosts the foreground MediaSessionService and ExoPlayer
- MusicMediaItems.kt handles NavidromeSong <-> MediaItem round-trip
- NavidromeMusicCacheRepository.kt handles DataStore persistence
- Existing 100MB LRU disk cache is for streaming buffer, not offline storage
