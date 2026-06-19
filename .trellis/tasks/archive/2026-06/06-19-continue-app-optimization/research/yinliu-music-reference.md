# Yinliu Music Reference

## Source

* Site: https://music.aqzscn.cn/
* Intro page: https://music.aqzscn.cn/docs/intro
* GitHub link exposed by the site: https://github.com/gitbobobo/StreamMusic

## Findings

The public site is a Docusaurus documentation site for 音流 / Stream Music, not a directly inspectable web player UI. The intro page describes 音流 as a NAS music player that connects to self-hosted music services and provides a consistent cross-platform listening experience.

Relevant product cues:

* Music is treated as a dedicated listening product, not just a file browser.
* Supported services include Subsonic, Navidrome, Emby, Jellyfin, AudioStation, and Plex.
* The app emphasizes server-backed music libraries, cross-platform consistency, and lyrics display.
* The docs navigation separates intro, music services, feature comparison, usage instructions, practical tips, versions, FAQ, and development plan.

## Implications For Nordic

Nordic already shares the same self-hosted media premise, but the music tab should feel more like a dedicated music client:

* Keep Navidrome as the current implementation target and avoid adding new providers in this task.
* Make the top-level music paths explicit: discovery/home, songs, albums, artists, playlists, search.
* Put the most useful actions near the top of the music tab: search, all songs, albums, artists, playlists, refresh.
* Let album art and playlist art carry visual weight while the interface remains quiet.
* Improve empty/loading states so setup, cached content, and empty libraries are clear.

## MVP Recommendation

For this phase, optimize the music home and navigation model:

* Add a quick access strip for Search, Songs, Albums, Artists, and Playlists.
* Keep existing playlist browsing and playback behavior.
* Preserve existing album sorting, song playback, artist detail, and search flows.
* Polish copy and surfaces around the music home, playlist rows, and section transitions.

Out of scope for this phase:

* New music providers.
* Lyrics search/manual override.
* Playlist creation or editing.
* Player engine rewrites.
