# Media Feature Parity Enhancements

## Goal

Improve Nordic Media Hub's music, audiobook, and video experiences by borrowing proven interaction patterns from mature clients: a music app similar to 音流, the official AudiobookShelf experience for audiobooks, and Emby/Yamby-style video browsing. The work should turn broad product inspiration into a focused Android MVP that can be implemented and verified safely.

## What I Already Know

* User wants music to reference 音流, audiobooks to reference the official audiobook/AudiobookShelf experience, and video to reference Emby and Yamby.
* The repository is a single Android app under `app/`, using Kotlin, Jetpack Compose, Retrofit/OkHttp, Coil, DataStore, and Media3.
* Music currently uses Navidrome/Subsonic APIs with albums, all songs, recently added songs, artists, playlists, lyrics, playback queue, repeat mode, and cached refresh data.
* Audiobooks currently use AudiobookShelf APIs for libraries, items, item detail, playback sessions, periodic progress sync, close-session sync, chapters, and a dedicated player screen.
* Video currently has an Emby read-only browsing MVP: auth, user/library discovery, item listing, and thumbnail URL mapping. Existing spec explicitly marks video playback, Plex/WebDAV, token persistence, and wider account management as out of scope for that earlier MVP.
* Project specs emphasize typed errors, repository-owned API mapping, remembered repository instances, stable Compose lazy-list keys/content types, Media3 queue consistency, and sequential Gradle verification on Windows.

## Assumptions

* This should be a focused feature-parity pass, not a full clone of external apps.
* "audiobook official" likely means official AudiobookShelf client/web UX and API behavior.
* Yamby reference needs research; exact app/product identity is not yet confirmed.
* External dependency upgrades should remain out of scope unless required for a selected feature.
* The first implementation should preserve existing server integrations: Navidrome for music, AudiobookShelf for audiobooks, Emby for video.

## Open Questions

* None. User confirmed MVP scope on 2026-06-26.

## Requirements (Evolving)

* Research the referenced apps/services before selecting features.
* Identify a small MVP feature set with clear acceptance criteria.
* Prioritize video completion first: add an Emby playback MVP before broad music/audiobook feature parity.
* Use Emby `PlaybackInfo` and a direct/static stream URL as the MVP playback strategy.
* Add a video player surface that can launch from `VideoScreen` items and render basic playback state.
* Provide basic controls expected for MVP playback: close, play/pause, seek/progress, title/metadata, loading, and error states.
* Surface a clear error when no direct-playable media source is available or playback info cannot be loaded.
* Preserve existing user-facing behavior unless a selected enhancement intentionally changes it.
* Keep API mapping in repositories and UI state in Compose screens/components.
* Add or update tests for repository behavior, playback logic, and pure helper rules where behavior changes.

## Acceptance Criteria (Evolving)

* [ ] Research notes exist under `research/` for the relevant product references.
* [ ] MVP scope is explicitly chosen and documented.
* [ ] Requirements distinguish must-have enhancements from deferred parity ideas.
* [ ] Implementation plan maps selected features to concrete app files and verification commands.
* [ ] Tapping a playable Emby video item starts a Media3 video playback surface.
* [ ] Repository tests assert `PlaybackInfo` request/mapping and direct stream URL construction.
* [ ] Playback/UI failures show an error rather than silently doing nothing.

## Research References

* [`research/music-streammusic-reference.md`](research/music-streammusic-reference.md) - 音流/StreamMusic and Navidrome client conventions point toward music-library polish, lyrics, playlist/queue affordances, rating/favorite support, and larger deferred platform features like downloads/Android Auto/Chromecast.
* [`research/audiobookshelf-official-reference.md`](research/audiobookshelf-official-reference.md) - Official AudiobookShelf app expectations center on audiobook-specific playback controls: speed, sleep timer, chapter behavior, downloads, and better metadata surfaces.
* [`research/video-emby-yamby-reference.md`](research/video-emby-yamby-reference.md) - Emby/Yamby parity shows the biggest current gap is video playback, then player controls such as subtitles, gestures, PIP, speed, and library visibility.
* [`research/emby-playback-api.md`](research/emby-playback-api.md) - Emby playback should start from `PlaybackInfo`; direct static stream is the smallest MVP, while HLS fallback requires active encoding lifecycle work.

## Feasible MVP Approaches

### Approach A: Balanced Polish Across Three Tabs

* Music: improve search/filter or lyrics/queue affordances using existing data.
* Audiobooks: add one or two official-client player controls such as playback speed and sleep timer.
* Video: improve browsing/detail/library remembered state, but defer full playback.
* Pros: every tab improves in one pass.
* Cons: video remains incomplete if playback is deferred.

### Approach B: Video Completion First (Recommended)

* Add Emby video playback MVP using existing Emby auth/catalog data and Media3.
* Keep Yamby-inspired controls limited to essentials first: play item, show metadata, basic playback state, close player.
* Pros: closes the largest functional gap; Video tab becomes useful, not just browsable.
* Cons: music/audiobook improvements wait for follow-up tasks.
* Decision: Selected by user on 2026-06-26.

### Approach C: Player Controls First

* Music: queue/lyrics polish.
* Audiobooks: playback speed, skip/chapter controls, sleep timer.
* Video: basic player controls only if playback already exists or is included minimally.
* Pros: concentrates on daily playback ergonomics.
* Cons: can grow quickly across three different playback surfaces.

## Decision (ADR-lite)

**Context**: The app already has music and audiobook playback, while video currently stops at Emby catalog browsing.

**Decision**: Implement the video completion path first. The MVP should add real Emby video playback using the existing Emby integration and Media3 rather than spreading the task thinly across all three media areas.

**Consequences**: Music and audiobook feature parity ideas remain useful follow-up work. The immediate implementation should focus on a narrow, testable video playback path and avoid taking on advanced Yamby/Emby features such as Live TV, subtitle management, PIP, gestures, transcoding controls, and library hiding unless explicitly selected later.

### Direct Playback Strategy

**Context**: Emby supports multiple playback paths. Direct/static stream is smallest; HLS/transcoding improves compatibility but adds active-encoding cleanup and lifecycle work.

**Decision**: Use `PlaybackInfo` plus direct/static stream first. Do not implement HLS fallback in this MVP.

**Consequences**: The app can deliver real video playback with smaller blast radius. Some media that requires transcoding may fail with a clear error and can be handled in a later HLS/transcoding task.

## Technical Approach (Evolving)

* Add an Emby playback repository method that calls `GET /Items/{Id}/PlaybackInfo` for the selected `VideoItem`.
* Map the selected Emby media source to a domain playback object containing the stream URL and metadata needed by Media3.
* Add a video playback surface/engine that follows existing playback ownership patterns: repository maps API data, playback code owns Media3 state, UI renders state and commands.
* Build a direct/static stream URL from the selected media source and play session.
* Defer HLS/transcoding/subtitle/PIP features.

## Implementation Plan

1. Extend `EmbyApi`/`EmbyRepository` with playback-info DTOs, a domain playback model, direct stream URL construction, and unit tests.
2. Add video playback state/engine using Media3 patterns consistent with existing music/audiobook playback ownership.
3. Wire `VideoScreen` item taps through `MainActivity` to show a video player surface with loading/error/basic controls.
4. Run sequential Gradle verification: compile, unit tests, lint.

## Definition of Done

* Tests added or updated for behavior changes.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes when UI/API behavior changes.
* Specs are updated if new integration contracts or project conventions emerge.

## Out of Scope (Temporary)

* Replacing Navidrome, AudiobookShelf, or Emby integrations.
* Rebuilding the whole app navigation model in one task.
* Large visual redesign without functional benefit.
* Offline downloads, HLS/transcoding fallback, casting, subtitle management, PIP, gestures, Live TV, or multi-server account management.

## Technical Notes

* Main app shell: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Music UI/data/playback: `MusicScreenV2.kt`, `MusicHomeSections.kt`, `MusicPlayerScreen.kt`, `MusicQueueSheet.kt`, `NavidromeRepository.kt`, `NavidromeApi.kt`, `MusicPlaybackEngine.kt`.
* Audiobook UI/data/playback: `AudiobookScreen.kt`, `AudiobookPlayerScreen.kt`, `AudiobookShelfRepository.kt`, `AudiobookShelfApi.kt`, `AudiobookPlaybackEngine.kt`.
* Video UI/data: `VideoScreen.kt`, `EmbyRepository.kt`, `EmbyApi.kt`, `VideoServerAuth.kt`.
* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/audiobookshelf-integration.md`, `.trellis/spec/backend/emby-integration.md`.
