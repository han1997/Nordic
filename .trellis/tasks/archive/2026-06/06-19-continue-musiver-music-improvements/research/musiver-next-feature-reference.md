# Musiver Next Music Feature Reference

## Sources

* Previous Nordic task research: `.trellis/tasks/archive/2026-06/06-19-improve-music-features-musiver/research/musiver-feature-reference.md`
* Musiver repository: `https://github.com/liuyincs/musiver`
* Latest release inspected in previous task: `v2.0.1-beta.10`

## Constraints

Musiver's public repository does not contain application source code and has no declared license metadata. Use it as product/release-note reference only. Do not copy code, assets, binaries, or UI text.

## Already Completed In Nordic

The previous Musiver-inspired task implemented album browsing and sorting:

* First-class album browsing reachable from the music home album section.
* Album sort modes for recently added, release year/date, and album name.
* Navidrome `getAlbumList2` mappings through `NavidromeAlbumSort`.

## Remaining Musiver-Inspired Candidates

1. Lyrics search and manual override
   * Musiver reference: lyrics search, manual overwrite, and persistence.
   * Nordic status: lyrics are loaded automatically by song id, then by artist/title fallback. The player can display synced/plain lyrics, but users cannot search alternatives or manually override bad/missing lyrics.
   * Likely files: `MainActivity.kt`, `MusicPlayerScreen.kt`, `NavidromeRepository.kt`, `NavidromeApi.kt`, `MusicLyrics.kt`.
   * Risk: needs a persistence contract for local overrides, plus UI states for search/loading/error/reset.

2. Multi-artist display and navigation
   * Musiver reference: multi-artist display and navigation from tracks/player/pages to artist detail.
   * Nordic status: `NavidromeSong` currently stores a single `artist: String?` and no artist ids on songs. Artist detail exists when browsing from the artist list.
   * Likely files: `NavidromeApi.kt`, `NavidromeRepository.kt`, `MusicScreenV2.kt`, `MusicPlayerScreen.kt`.
   * Risk: Subsonic/OpenSubsonic metadata support varies; fallback parsing may be needed.

3. Server health and endpoint status
   * Musiver reference: server management UI, endpoint health, network monitoring, and route rebinding.
   * Nordic status: Navidrome config readiness exists, but there is no explicit health check/status surface for the music endpoint.
   * Likely files: `ConfigCards.kt`, `NavidromeRepository.kt`, `NavidromeApi.kt`, `MusicScreenV2.kt`.
   * Risk: crosses settings/config and music browsing boundaries.

4. Playback queue metadata polish
   * Musiver reference: queue metadata synchronization improvements.
   * Nordic status: Media3 queue and queue sheet exist. Playback engine caches the queue and maps `MediaItem` back to `NavidromeSong`.
   * Likely files: `MusicPlaybackEngine.kt`, `MusicQueueSheet.kt`, `PlaybackDock.kt`, `MainActivity.kt`.
   * Risk: harder to define without a specific observed queue bug or desired behavior.

## Recommended Next MVP

Lyrics search and manual override is the strongest next Musiver-inspired feature because:

* It continues from an existing player lyrics surface rather than adding a disconnected page.
* It solves a user-visible gap: automatic lyrics can be missing or wrong.
* It can be scoped as local-only override persistence without changing server data.

Suggested MVP:

* Add a player lyrics management surface for the current song.
* Allow searching lyrics by editable artist/title query using the existing Navidrome lyrics endpoint.
* Allow manual paste/edit of LRC/plain lyrics.
* Persist local lyrics overrides per song id and prefer them before network lyrics.
* Add a reset action that removes the local override and falls back to server lyrics.

