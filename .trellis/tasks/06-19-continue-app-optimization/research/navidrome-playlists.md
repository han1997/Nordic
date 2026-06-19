# Navidrome Playlist MVP Research

## Source

Navidrome exposes Subsonic-compatible playlist endpoints. This task uses the Subsonic API contract as a product/API reference and implements against the existing Nordic Navidrome repository style.

## Relevant Endpoints

* `getPlaylists.view`
  * Returns a `playlists` object containing a list of playlist summaries.
  * Common playlist fields: `id`, `name`, `comment`, `owner`, `public`, `songCount`, `duration`, `created`, `changed`, `coverArt`.

* `getPlaylist.view`
  * Requires `id`.
  * Returns a `playlist` object with playlist metadata and an `entry` list of songs.
  * Song entries follow the same broad Subsonic song shape used by the existing `NavidromeSong` model.

## Nordic Fit

Current Nordic music UI already has:

* A top music tab entry for playlists.
* A placeholder playlist page.
* Existing song list rows and `onSongSelected(List<NavidromeSong>, Int)` playback flow.
* Existing `NavidromeRepository` request/auth/error conventions.

## Recommended MVP

Implement read-only playlist browsing:

* Add `NavidromePlaylist` and `NavidromePlaylistDetail` DTOs.
* Add `NavidromeApi.getPlaylists(...)` and `NavidromeApi.getPlaylist(...)`.
* Add repository methods:
  * `getPlaylists(): List<NavidromePlaylist>`
  * `getPlaylistSongs(playlistId: String): List<NavidromeSong>`
* Add a real Playlists page in `MusicScreenV2`.
* Open playlist detail, show songs, and allow tapping a song to play the playlist from that index.

## Out Of Scope

* Creating, updating, deleting playlists.
* Adding/removing songs from playlists.
* Caching playlists in `NavidromeMusicCacheRepository`.
* Offline playlist support.

