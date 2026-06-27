# Video Reference: Emby + Yamby

## Sources

- Emby playback methods: https://emby.media/support/articles/DirectPlay-Stream-Transcoding.html
- Emby Android TV docs: https://emby.media/support/articles/Android-TV.html
- Emby Android release notes: https://emby.media/community/topic/95467-emby-for-android-release-notes/
- Yamby Google Play listing: https://play.google.com/store/apps/details?id=com.hush.yamby

## Relevant Product Patterns

- Emby distinguishes Direct Play, Direct Stream, and Transcoding. Direct Stream may convert audio/subtitle/container without changing video tracks.
- Mature Emby/Yamby-style clients commonly expose:
  - Subtitle and audio track selection.
  - Subtitle scale and offset.
  - Playback speed.
  - Gesture controls for brightness/volume/seek.
  - PIP mode.
  - Library hiding/filtering.
  - Live TV support.
  - Direct play options and stream quality settings.

## Fit For Current Repo

- Already present: Emby auth/catalog, video library filtering, direct playback info, Media3 video player, speed control, progress reporting, movie/detail page, seasons/episodes.
- Major missing parity candidates:
  1. Audio/subtitle track selection from Emby `MediaSources.MediaStreams`.
  2. Subtitle style controls: scale and offset.
  3. Gesture controls in player: horizontal seek; vertical volume/brightness.
  4. PIP mode on Android.
  5. Library visibility settings.
  6. Stream quality / direct-play vs direct-stream/transcode preferences.
  7. Live TV support, which is broader and likely separate.

## Recommended MVP Slice

- Prioritize subtitle/audio track selection and player gestures:
  - It fits current PlaybackInfo flow.
  - It improves daily video playback without adding a new media domain.
  - It can be tested at repository mapping level and compile/lint level for UI wiring.
- Defer Live TV and server-side transcoding controls to later rounds.

