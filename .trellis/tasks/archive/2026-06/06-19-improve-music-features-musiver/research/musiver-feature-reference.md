# Musiver Feature Reference

## Sources

* Repository: `https://github.com/liuyincs/musiver`
* README: `https://raw.githubusercontent.com/liuyincs/musiver/main/README.md`
* Latest release inspected: `v2.0.1-beta.10`

## Important Constraint

The Musiver repository states that it does not contain application source code. It is useful as a product and release-note reference, not as an implementation reference.

The repo has no declared license in GitHub metadata. Do not copy app code, assets, UI text, or binaries from Musiver.

## Product Capabilities From README

Musiver positions itself as a multi-platform music playback client for user-owned music libraries. It does not provide music content or host audio.

Highlighted capabilities:

* Complete playback capability.
* Metadata-based music library browsing.
* Artwork, lyrics, and information display.
* Multi-server and multi-route support.
* Unified UX across private media services: Navidrome, Emby, Plex, Jellyfin, Subsonic/OpenSubsonic, Synology Audio Station, Audiobookshelf.

## Latest Release Notes Relevant To Nordic

`v2.0.1-beta.10` lists the following music/product improvements:

* Lyrics search and manual overwrite/persistence.
* Multi-artist display and navigation from tracks/player/pages to artist detail.
* Album list sorting by recently added and release date.
* Server management UI improvements and clearer error prompts.
* Endpoint health UI, network monitoring, and runtime route rebinding.
* Queue metadata synchronization improvements.
* Desktop volume fade-in/fade-out.

## Mapping To Current Nordic App

Current Nordic music module already has:

* Navidrome configuration and credential-based readiness.
* Config-scoped music refresh and cache.
* Music home, songs, artists, artist detail, album detail, and search pages.
* Media3 playback, queue sheet, repeat modes, previous/next, and seek.
* Lyrics retrieval by song id and fallback by artist/title.

Promising MVP candidates:

1. Lyrics search/override UX
   * Fit: Nordic already has lyrics retrieval and a lyric display panel.
   * Gap: No user-facing lyric search, alternate result picker, or local lyric override.
   * Risk: Requires new persistence contract for user lyric overrides and maybe provider-specific API limits.

2. Album sorting controls
   * Fit: Navidrome `getAlbumList2` already supports list types and paging.
   * Gap: UI currently emphasizes recently added albums and all songs, but does not expose album sort modes.
   * Risk: Moderate data/cache model changes if multiple album views are cached.

3. Multi-artist display/navigation
   * Fit: Musiver emphasizes multi-artist metadata and artist navigation.
   * Gap: Nordic song model has a single `artist: String?` and artist ids are not present on songs.
   * Risk: Subsonic/OpenSubsonic metadata support varies; may require API model expansion and fallback parsing.

4. Server health / route status
   * Fit: Recent config work already improved readiness.
   * Gap: No health check UI for Navidrome endpoint.
   * Risk: Cross-domain with config screen rather than purely music browsing.

Recommended MVP: Album sorting controls or lyrics search/override. Album sorting is lower-risk and fits existing Navidrome API/cache. Lyrics search is higher-value if the user's priority is player experience.
