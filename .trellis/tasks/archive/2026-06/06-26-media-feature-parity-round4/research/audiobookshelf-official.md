# Audiobook Reference: Audiobookshelf Official App / Ecosystem

## Sources

- Audiobookshelf introduction: https://audiobookshelf.org/docs/documentation/introduction/
- Audiobookshelf Google Play listing: https://play.google.com/store/apps/details?id=com.audiobookshelf.app
- Audiobookshelf Android shared storage guide: https://www.audiobookshelf.org/guides/android_app_shared_storage/
- Audiobookshelf app discussion #864: https://github.com/advplyr/audiobookshelf-app/discussions/864
- Audiobookshelf progress sync issue #3213: https://github.com/advplyr/audiobookshelf/issues/3213

## Relevant Product Patterns

- Audiobookshelf emphasizes self-hosted audiobook/podcast management, per-user progress sync, companion mobile apps, and offline listening.
- Mobile users care about:
  - Continue listening surfaces.
  - Offline/download workflows.
  - Reliable progress sync across devices.
  - Headphone/lock-screen/media-button controls.
  - Fast recovery to the last book after app restart.
  - Chapter navigation, sleep timer, variable speed.

## Fit For Current Repo

- Already present: login/config, libraries/items/detail, playback sessions, periodic progress sync, close-session sync, playback speed, skip controls, chapter jump, sleep timer.
- Major missing parity candidates:
  1. Continue Listening / In Progress shelf from Audiobookshelf progress data.
  2. Offline audiobook download and local playback.
  3. Bookmarks and listening history.
  4. Startup recovery: resume last active audiobook/player state.
  5. Podcast episode support if the server library contains podcast media.

## Recommended MVP Slice

- Prioritize Continue Listening and bookmarks before offline downloads:
  - Continue Listening shelf uses Audiobookshelf progress fields already returned in item detail/list responses where available.
  - Bookmark support can be local-first if server bookmark endpoints are not yet wired.
  - Offline full-book download is larger and should be its own task because it needs multi-file storage, progress, cancellation, and session/progress reconciliation.

