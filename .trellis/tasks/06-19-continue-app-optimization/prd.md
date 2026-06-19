# Continue App Optimization

## Goal

Continue optimizing the Nordic Android media hub after the recent Musiver-inspired music improvements. Navidrome playlist browsing is implemented as the first MVP. The current phase references 音流 / Stream Music and focuses on making the music section feel more like a dedicated music client through clearer usage paths and a more polished home surface.

## What I Already Know

* The user asked to "continue optimizing" after previous music work.
* The app is a Kotlin/Jetpack Compose Android media hub with Navidrome music, AudiobookShelf audiobooks, Emby video, Media3 playback, and shared bottom dock/player surfaces.
* Recent completed work:
  * Sortable Navidrome album browsing.
  * Manageable music queue controls with Media3 queue synchronization.
* Current code inspection shows likely optimization candidates:
  * Music lyrics are auto-loaded and displayed, but there is no search/manual override flow.
  * Navidrome playlists are now implemented as read-only list/detail/playback surfaces.
  * Navidrome config readiness exists, but there is no explicit endpoint health/status check surface.
  * Media list loading/empty/error states exist across music/audiobook/video, with room for consistency polish.
* 音流 / Stream Music is a NAS music player focused on self-hosted music services, cross-platform consistency, and music-client usage flows.
* The public 音流 reference is a docs site, not a directly inspectable app UI, so this task borrows product logic rather than copying visual screens.

## Assumptions

* "Continue optimizing" means continue improving the app's music experience unless the user chooses otherwise.
* We should choose one focused phase at a time and avoid bundling multiple unrelated optimizations.
* We should preserve recent album sorting and queue-management behavior.

## Open Questions

* None.

## Requirements

* Keep changes scoped to one optimization direction.
* Preserve existing playback, album sorting, and queue controls.
* Follow existing Android/Compose/Media3 patterns and project specs.
* Add focused tests or compile/lint verification appropriate to the selected change.
* Selected MVP: Navidrome playlist browsing.
* Replace the Music tab playlist placeholder with a real Navidrome playlist surface.
* Load playlists from Navidrome/Subsonic `getPlaylists.view`.
* Open playlist detail and load songs from `getPlaylist.view`.
* Allow tapping a playlist song to play that playlist from the tapped index.
* Keep playlist support read-only for this MVP.
* Current phase: 音流-inspired music usage logic and UI polish.
* Add an explicit quick access layer for Search, Songs, Albums, Artists, and Playlists from the music home.
* Keep the home surface content-first with album/song/playlist artwork as the visual focus.
* Make navigation behavior predictable: home quick actions enter the same existing library pages instead of creating parallel flows.
* Improve home copy, empty states, and key entry surfaces while preserving existing playback behavior.

## Acceptance Criteria

* [ ] PRD identifies the selected optimization MVP.
* [ ] Requirements distinguish MVP from out-of-scope follow-up ideas.
* [ ] Technical approach lists affected files and verification commands before implementation.
* [ ] Existing music album and queue behavior continues to compile and pass tests.
* [ ] New behavior is covered by focused tests where practical.
* [ ] Playlist tab displays Navidrome playlists when configured and synced.
* [ ] Empty playlist responses show an empty state instead of the old "coming soon" placeholder.
* [ ] Opening a playlist displays its song entries.
* [ ] Tapping a playlist song starts playback using the playlist song order.
* [ ] Repository tests assert playlist list/detail endpoints are called with expected paths and auth query parameters.
* [ ] PRD records 音流 reference findings and current optimization scope.
* [ ] Music home exposes direct entry points for search, all songs, albums, artists, and playlists.
* [ ] Quick access entries reuse existing library pages and do not bypass repository/playback contracts.
* [ ] Music home visual hierarchy emphasizes media artwork and avoids nested/decorative cards.
* [ ] Existing playlist, album, song, artist, and search flows continue to compile.

## Definition of Done

* Kotlin compile, unit tests, lint, and debug assemble pass as appropriate.
* Tests are added/updated for changed behavior where practical.
* Specs are updated if a reusable contract or convention changes.
* No unrelated user changes are reverted.

## Out of Scope

* Replacing the app architecture.
* Adding multiple optimization areas in one task.
* Reworking recent album sorting or queue controls except where integration requires it.
* Adding a new media provider.
* Creating, editing, deleting, or reordering Navidrome playlists.
* Caching playlists in the local music cache.
* Offline playlist downloads.
* Lyrics search/manual override in this phase.
* Rewriting the Media3 playback engine or queue model in this phase.
* Copying 音流 visuals exactly; this phase adapts product logic to Nordic's existing design system.

## Candidate MVPs

**Approach A: Navidrome Playlists** (Selected)

* Replace the current playlist placeholder with a real Navidrome playlist list/detail/play flow.
* Pros: removes an obvious unfinished music surface and builds on existing song list/play queue patterns.
* Cons: requires new Subsonic API methods and DTOs.

**Approach B: Lyrics Search & Manual Override**

* Add a current-song lyrics management flow for search/edit/local override/reset.
* Pros: strong player polish and aligns with Musiver-style lyrics improvements.
* Cons: needs local persistence and careful UI states.

**Approach C: Server Health / Endpoint Status**

* Add explicit Navidrome endpoint status checks and clearer setup diagnostics.
* Pros: improves reliability and supportability.
* Cons: less directly visible during normal playback.

**Approach D: Cross-Media UI State Polish**

* Make loading/empty/error surfaces and refresh behavior more consistent across music, audiobook, and video tabs.
* Pros: improves app-wide fit and finish.
* Cons: broader UI touch area and harder to define as one testable feature.

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/quality-guidelines.md`, and service-specific specs if touching AudiobookShelf or Emby.
* Research reference: `research/navidrome-playlists.md`.
* Research reference: `research/yinliu-music-reference.md`.
* Likely music files: `NavidromeApi.kt`, `NavidromeRepository.kt`, `MusicScreenV2.kt`, `NavidromeRepositoryTest.kt`.
* Existing verification commands:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
  * `.\gradlew.bat :app:assembleDebug --no-daemon`

## Decision (ADR-lite)

**Context**: The Music tab already has a playlist entry, but it currently renders only a placeholder. Navidrome supports playlists through standard Subsonic endpoints, and Nordic already has song-list playback flows that can be reused.

**Decision**: Implement read-only Navidrome playlist browsing and playback as this optimization MVP.

**Consequences**: The feature removes an unfinished surface and adds practical library navigation without introducing mutation/caching complexity. Playlist creation/editing remains future work.

## Technical Approach

* Add Subsonic playlist DTOs and Retrofit endpoints to `NavidromeApi`.
* Add `NavidromeRepository.getPlaylists()` and `getPlaylistSongs(playlistId)`.
* Map playlist song entries through the same cover-art/stream-url enrichment used by album songs.
* Extend `MusicScreenV2` with playlist list/detail state and UI.
* Reuse existing song row/playback callback patterns for playlist playback.
* Add repository tests using `MockWebServer` for playlist list/detail requests.

## Current Phase Technical Approach

* Add a music home quick access strip in `MusicScreenV2.kt` that routes to existing Search, Songs, Albums, Artists, and Playlists pages.
* Use existing repository-loading functions for album and playlist entry actions.
* Add or reuse small Compose primitives in the music UI layer instead of duplicating large one-off surfaces.
* Keep playback callbacks unchanged: song, album, playlist, and search selections continue through `onSongSelected(list, index)`.
* Run Kotlin compile, unit tests, lint, and debug assemble after implementation.

## Affected Files

* `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`
* `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
* `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`
* `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`
* `.trellis/spec/backend/quality-guidelines.md`

## Verification Commands

* `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
* `.\gradlew.bat :app:lintDebug --no-daemon`
* `.\gradlew.bat :app:assembleDebug --no-daemon`
