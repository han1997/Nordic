# Audiobook Reference: AudiobookShelf Official App

## Sources

* Audiobookshelf homepage: https://audiobookshelf.org/
* Audiobookshelf Android app listing: https://play.google.com/store/apps/details?id=com.audiobookshelf.app
* Audiobookshelf app releases: https://github.com/advplyr/audiobookshelf-app/releases
* Audiobookshelf Android shared storage guide: https://audiobookshelf.org/guides/android_app_shared_storage/
* Audiobookshelf app issue references around sleep timer and playback-speed correctness:
  * https://github.com/advplyr/audiobookshelf-app/issues/1399
  * https://github.com/advplyr/audiobookshelf-app/issues/169

## Findings

* Audiobookshelf is a self-hosted audiobook and podcast server with official mobile apps.
* The Android listing emphasizes connecting to a self-hosted server and streaming audiobooks/podcasts directly.
* Official app release notes and issues highlight audiobook-specific controls that users expect: downloads/offline media, sleep timer, end-of-chapter sleep behavior, playback-speed correctness, cover fallback compatibility, playlists/collections play state, rich descriptions, and translations.
* Sleep timer behavior is subtle: end-of-chapter timers must account for playback speed and/or chapter boundaries, and reset/fade behaviors are common audiobook-client expectations.

## Repo Fit

* Nordic already has AudiobookShelf libraries, item list, item detail, progress mapping, session start, periodic sync, close-session sync, chapters, and a player screen.
* Current app does not appear to include playback speed, sleep timer, bookmarks, offline downloads, collections/series surfaces, or podcast-specific behavior.
* The lowest-risk official-client parity additions are player controls that can be local to Media3 state first: playback speed, configurable skip intervals, chapter jump, and sleep timer.
* Offline downloads are valuable but require storage permissions, download management, and sync contracts, so they should not be first unless the user explicitly prioritizes offline.

## Candidate MVP Features

1. Audiobook player control pass: playback speed, richer skip/chapter controls, and sleep timer.
2. Library detail pass: collection/series metadata and better descriptions where API DTOs already expose it or can be added safely.
3. Deferred: offline downloads/shared storage, bookmarks with notes, podcasts, Android Auto.
