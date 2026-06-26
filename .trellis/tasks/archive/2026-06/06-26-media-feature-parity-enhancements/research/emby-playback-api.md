# Emby Playback API Research

## Sources

* Emby developer home/API workflow: https://github.com/MediaBrowser/Emby/wiki
* PlaybackInfo endpoint: https://dev.emby.media/reference/RestAPI/MediaInfoService/getItemsByIdPlaybackinfo.html
* Video streaming wiki: https://github.com/MediaBrowser/Emby/wiki/Video-Streaming
* HLS documentation: https://dev.emby.media/doc/restapi/Http-Live-Streaming.html
* API key authentication: https://github.com/MediaBrowser/Emby/wiki/Api-Key-Authentication

## Findings

* Emby developer docs now point to `dev.emby.media` and the API browser for detailed operation definitions.
* `GET /Items/{Id}/PlaybackInfo` returns live playback media info for a user-authenticated item, including `MediaSources`, `PlaySessionId`, media-source ids, direct-stream/transcoding URLs, direct play support, and stream indexes.
* The legacy video streaming guide describes `/Videos/{Id}/stream` aliases and says direct stream uses `static=true`. It also lists `MediaSourceId` and `PlaySessionId` as required parameters for video stream URLs.
* Emby HLS docs expose `/Videos/{Id}/master.m3u8`; required parameters include `Id`, `MediaSourceId`, and `DeviceId`. The docs also say active HLS encodings should be cleaned up after playback via `/Videos/ActiveEncodings?DeviceId=xxx`.
* API keys can be passed by `X-Emby-Token` header or `api_key` query parameter; this repo already uses the header for API calls and query parameter for image URLs.

## Repo Fit

* Current `EmbyRepository` already caches an authenticated session with `userId` and token, but these are private and not exposed to UI.
* Current `VideoItem` has item id, library id, title, type, overview, year, duration, and image URL, but no playback URL, media source id, play session id, or stream metadata.
* Existing Media3 dependencies can play video; a video-specific playback engine/screen can mirror the music/audiobook playback pattern without adding a new library.

## Recommended MVP Contract

1. Add a repository method such as `suspend fun getPlaybackInfo(item: VideoItem): VideoPlaybackInfo`.
2. Map the first playable media source into a domain object containing:
   * `itemId`
   * `title`
   * `streamUrl`
   * `playSessionId`
   * `mediaSourceId`
   * `durationSeconds`
   * optional thumbnail/overview metadata
3. For MVP, prefer a direct static stream URL built from the selected media source and play session. If the stream fails or no media source exists, surface a typed `EmbyApiException.Kind.API`.
4. Defer HLS/transcoding controls until the app can clean up active encodings and manage device/session lifecycle explicitly.

## Open Implementation Choice

* Option 1: Direct static stream only. Fastest, lowest scope, relies on client/server codec compatibility.
* Option 2: PlaybackInfo plus HLS fallback. More compatible, but requires active-encoding cleanup and larger tests.
* Option 3: Full player parity. Include subtitles, PIP, gestures, HLS/transcoding, and playback reports; too large for this MVP.
