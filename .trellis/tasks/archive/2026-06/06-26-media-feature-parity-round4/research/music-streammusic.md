# Music Reference: Stream Music / 音流

## Sources

- Stream Music introduction: https://music.aqzscn.cn/en/docs/intro/
- Stream Music services notes: https://music.aqzscn.cn/en/docs/services/
- Stream Music latest release notes: https://music.aqzscn.cn/en/docs/versions/latest/
- Stream Music 1.2.x release notes: https://music.aqzscn.cn/en/docs/versions/1.2.x
- Stream Music GitHub README: https://github.com/gitbobobo/StreamMusic

## Relevant Product Patterns

- Stream Music is positioned as a NAS music player across Subsonic/Navidrome/Emby/Jellyfin/AudioStation/Plex, with a consistent cross-platform library/player experience.
- Features worth considering for Nordic after the last three music rounds:
  - Lyrics improvements: embedded lyrics when cached, lyric notification offset, bilingual/line handling, empty-line controls, local lyric search/confirmation hooks.
  - Library modes: full media-library mode vs direct mode when full scanning is expensive.
  - Sync/cache behavior: incremental sync, single-section refresh such as playlists, pre-cache while playing.
  - Playback polish: app volume memory, m3u8 radio playback, richer notification/transport behavior, ReplayGain-like loudness normalization.
  - Organization polish: duplicate song detection, local search inside lists, album artist support, artist-grid layout.

## Fit For Current Repo

- Already present: Navidrome/Subsonic library, albums/songs/artists/playlists, search, lyrics, favorites, playlist CRUD, queue, offline download, smart radio, scrobble/play history, EQ.
- Good next candidates:
  1. Local list search/filter inside album/artist/playlist/song screens.
  2. Incremental/section refresh for playlists and library content to reduce full refresh cost.
  3. Lyrics quality controls: choose plain vs synced lyrics, offset, empty-line toggle, embedded/local fallback.
  4. Album artist and duplicate detection are useful but require data-model review.
  5. ReplayGain is valuable but depends on server metadata availability and Media3 support; treat as later unless metadata is already present.

## Recommended MVP Slice

- Add music polish that is UI/data-flow heavy but low-risk:
  - Local list search/filter for large song, album, artist, and playlist-detail lists.
  - Lyrics display controls: synced/plain selector when both exist, line offset, larger text toggle.
  - Playlist-only refresh action.

