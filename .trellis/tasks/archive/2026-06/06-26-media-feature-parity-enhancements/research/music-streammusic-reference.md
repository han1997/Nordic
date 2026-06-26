# Music Reference: StreamMusic / Navidrome Clients

## Sources

* StreamMusic GitHub README: https://github.com/gitbobobo/StreamMusic
* StreamMusic intro docs: https://github.com/gitbobobo/StreamMusic/blob/main/docs/intro.md
* StreamMusic 1.2.x changelog: https://github.com/gitbobobo/StreamMusic/blob/main/docs/versions/1.2.x.md
* Navidrome client catalog: https://www.navidrome.org/apps/

## Findings

* StreamMusic (音流) positions itself as a NAS music client across Android, iOS, macOS, and Windows, supporting Subsonic, Navidrome, Emby, Jellyfin, AudioStation, and Plex.
* StreamMusic's public docs emphasize a consistent cross-platform music experience over professional Hi-Fi/effects tooling.
* StreamMusic changelog entries show practical music-client features relevant to this app: song rating for Navidrome/Subsonic, queue bug fixes, lyric display improvements, login URL normalization, and playlist filtering for provider-specific quirks.
* The Navidrome app catalog shows common competitive features across modern Subsonic/OpenSubsonic clients: gapless playback, offline mode/downloads, multiple servers, Material You, playlists, bookmarks, jukebox, scrobbling, Android Auto, Chromecast, synced lyrics, replay gain, equalizer, widgets, and smart playlists.

## Repo Fit

* Nordic already has Navidrome server config, repository refresh, all-song browsing, album browsing/sorting, playlist browsing, lyrics loading, player screen, and queue management.
* The lowest-risk feature-parity additions are UI/interaction improvements over existing API surfaces: richer search/filter, rating/favorite actions if API support is added, better lyrics display, and stronger playlist/library affordances.
* Higher-risk features are offline downloads, Android Auto, Chromecast, and multi-server management because they require new platform integrations and persistence/contracts.

## Candidate MVP Features

1. Music polish pass: improve library search/filtering, lyrics display states, and queue/playlist actions using existing Navidrome data.
2. Music server affordances: add favorite/rating support via Subsonic/Navidrome API endpoints, with tests.
3. Deferred: offline downloads, Android Auto, Chromecast, equalizer, replay gain, widgets.
