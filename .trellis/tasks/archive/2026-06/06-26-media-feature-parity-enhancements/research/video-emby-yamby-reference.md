# Video Reference: Emby and Yamby

## Sources

* Emby Android listing: https://play.google.com/store/apps/details?id=com.mb.android
* Emby Server GitHub README: https://github.com/MediaBrowser/Emby
* Emby home page: https://emby.media/
* Yamby Google Play listing: https://play.google.com/store/apps/details?id=com.hush.yamby
* Yamby Softonic overview: https://yamby.en.softonic.com/android

## Findings

* Emby positions itself around unified personal media libraries, artwork-rich metadata, streaming to devices, automatic conversion/transcoding, sharing, parental controls, and Live TV/DVR.
* Emby exposes REST-based APIs and client libraries for client development.
* Yamby is a third-party Emby Android client emphasizing Material Design 3, lightweight performance, phone/tablet support, easy server switching, hiding libraries, player gestures, cache size, playback speed, subtitle scale/offset, embedded/external subtitles, PIP, Live TV, STRM direct play, and danmaku support.

## Repo Fit

* Nordic's current video spec is explicitly read-only browsing: auth, library discovery, item listing, thumbnails. Video playback was out of scope for the previous MVP.
* The most important gap versus Emby/Yamby is not visual polish; it is actual item playback and video-player controls.
* Lowest-risk video MVP would add a simple Emby item player for playable items using Media3, then layer PIP/subtitles/gestures later.
* Server switching and hiding libraries overlap with config/library selection UI and could be added before or after playback, but they do not make the Video tab feel complete without playback.

## Candidate MVP Features

1. Video playback MVP: play selected Emby video items with Media3 using Emby stream URLs and existing auth token flow.
2. Video browsing polish: hide libraries, remember selected library, improve artwork/detail metadata.
3. Video player parity: playback speed, subtitles, basic gestures, PIP.
4. Deferred: Live TV/DVR, danmaku, STRM direct-play special cases, transcoding controls, parental controls.
