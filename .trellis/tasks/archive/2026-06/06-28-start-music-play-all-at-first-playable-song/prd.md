# Start music play-all at first playable song

## Goal

Make music "play all" flows start continuous playback at the first song with a usable stream URL instead of blindly starting at index 0. This prevents albums, artists, or playlists with an unavailable first track from failing before reaching playable tracks.

## What I Already Know

* The long-running goal asks to keep improving music playback, AudiobookShelf behavior, and Yamby-style video behavior with feature work, performance optimization, and bug fixes.
* Previous rounds in this turn completed AudiobookShelf library selection stabilization and video stale detail clearing.
* `MusicScreenV2.playAlbum(...)` already searches `albumSongs.indexOfFirst { !it.streamUrl.isNullOrBlank() }`, but falls back to index `0` when none are playable.
* Album detail, playlist detail, and artist detail "play all" actions currently call `onSongSelected(list, 0)` whenever the list is non-empty.

## Requirements

* Add a shared pure helper that resolves the first playable song index from a `List<NavidromeSong>`.
* Use the helper for album quick-play, album detail play-all, playlist detail play-all, and artist detail play-all.
* If a non-empty list has no playable songs, show a user-facing error instead of calling `onSongSelected`.
* Do not change single-song row click behavior.
* Cover the helper with focused unit tests.

## Acceptance Criteria

* [x] First playable index returns the first song whose `streamUrl` is not blank.
* [x] First playable index ignores null and blank stream URLs.
* [x] First playable index returns `null` when no songs are playable.
* [x] Music play-all entry points use the helper before calling `onSongSelected`.
* [x] Focused unit tests pass.

## Definition of Done

* Tests added or updated where behavior changes.
* Kotlin compile and relevant unit tests pass.
* Specs or notes updated if a reusable contract is learned.
* Trellis finish workflow is run at the end of the round.

## Technical Approach

Create an `internal fun firstPlayableSongIndex(songs: List<NavidromeSong>): Int?` in `MusicScreenV2.kt`, replace the existing inline `indexOfFirst` and play-all index `0` call sites, and add helper tests in `MusicScreenV2Test`.

## Out of Scope

* Filtering songs out of visible lists.
* Changing individual song-row click behavior.
* Playback engine changes.
* New Navidrome API calls.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/ui/MusicScreenV2Test.kt`.
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md` music queue/navigation contracts.
