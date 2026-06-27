# Research: Emby/Yamby Video Client Features

- **Query**: What features do mature Emby and Yamby video clients offer beyond basic browsing and playback? Focus on UX and functional features that improve the overall video experience.
- **Scope**: Mixed (internal codebase analysis + external product/web research + prior task archive research)
- **Date**: 2026-06-27

## Findings

### 1. Yamby (Third-Party Emby Android Client) Features

Yamby (package `com.hush.yamby`) is a lightweight, Material Design 3 Emby client available on Google Play. Based on prior research rounds (rounds 2, 4, enhancements) and public listing descriptions:

| Feature | Description |
|---|---|
| Material Design 3 UI | Modern Android design language, phone + tablet optimized |
| Lightweight / Fast | Lower overhead than the official Emby Android app |
| Easy Server Switching | Quick profile switching between multiple Emby servers |
| Library Hiding | Users can hide libraries they do not want to see |
| Player Gestures | Horizontal drag to seek; vertical drag for volume (right half) and brightness (left half) |
| Playback Speed | Adjustable playback speed in the video player |
| Subtitle Scale + Offset | Font size scaling and timing offset for subtitles |
| Embedded + External Subtitles | Supports both embedded and external subtitle tracks (SRT, SSA, ASS, VTT, PGS) |
| PIP Mode | Picture-in-picture when navigating away from the player |
| Live TV | Browsing and watching live TV channels from Emby |
| STRM Direct Play | Special handling for STRM files that point to external streams |
| Danmaku Support | Overlay bullet comments common in Asian video platforms |
| Cache Size Control | User can control how much local cache the app uses |
| Chromecast | Cast to Chromecast devices |
| Sort/Filter | Sort by name, date added, etc.; filter by genre, year, rating, etc. |

Sources from prior research:
- Yamby Google Play listing: `https://play.google.com/store/apps/details?id=com.hush.yamby`
- Yamby Softonic overview: `https://yamby.en.softonic.com/android`

### 2. Official Emby Client Features (Android, Android TV, Emby Theater, Web)

Based on Emby official documentation reached during this research plus prior rounds:

| Feature Category | Details |
|---|---|
| **Home Screen / Discovery** | Latest items, continue watching (resume), next up (TV series), recommendations/suggestions, "because you watched" rows |
| **Search** | Server-side full-text search across libraries: `GET /Search/Hints` with query, media types, item types, limit |
| **Favorites / Collections** | Mark items as favorite via `UserData.IsFavorite`; filter libraries to show only favorites; `GET /Users/{userId}/Items` with `Filters=IsFavorite` |
| **Sort & Filter** | `GET /Users/{userId}/Items` supports `SortBy` (Name, DateCreated, DatePlayed, ProductionYear, SortName, Random, CommunityRating, CriticRating, etc.), `SortOrder` (Ascending/Descending), `Filters` (IsUnplayed, IsResumable, IsFavorite, etc.), `Genres`, `Years`, `Studios`, `OfficialRatings`, `Tags`, `MinCommunityRating` |
| **Next Up** | `GET /Shows/NextUp` returns the next unwatched episode for each series the user is watching; shown on home screen as a "Next Up" row |
| **Autoplay Next Episode** | After an episode finishes, automatically start the next episode in the season |
| **Up Next / End-of-Episode Banner** | Show a "Next episode in X seconds" overlay near the end of an episode, letting the user skip or dismiss |
| **Continue Watching / Resume** | `GET /Users/{userId}/Items/Resume` with `MediaTypes` and `IncludeItemTypes` filters; shows partially watched items with progress bar and resume position |
| **Picture-in-Picture (PIP)** | Android PIP mode when the user navigates away from the video player |
| **Chromecast / Google Cast** | Cast to Chromecast or Google Cast devices; remote playback control from phone |
| **External Player Support** | Option to play via external players (VLC, MX Player) instead of the built-in player |
| **Download / Offline** | Download items for offline playback (Premiere feature); requires `POST /Videos/{Id}/download`; synced items stored with offline activation |
| **Parental Controls** | Per-user parental ratings and content restrictions; `GET /Items/{userId}/Items` respects `MaxParentalRating` and content tags |
| **Live TV & DVR** | Browse live TV channels, view program guide, schedule and watch recordings |
| **Subtitles** | Embedded + external subtitle track selection; subtitle appearance customization (font size, color, outline); subtitle offset timing |
| **Audio Track Selection** | Switch between multiple audio tracks in multi-audio media |
| **Playback Speed** | Adjustable speed: typically 0.5x to 2x |
| **Playback Quality / Bitrate** | Stream quality selector: Auto, or specific max bitrate; controls when Emby transcodes vs direct plays |
| **Transcoding Fallback** | If direct play fails, request HLS transcode: `GET /Videos/{Id}/master.m3u8` with quality and audio/subtitle stream params |
| **Hardware Decoding** | Android TV / Shield support for hardware-accelerated codecs including DTS-HD MA, TrueHD, Dolby Digital |
| **Mark Played / Unplayed** | `POST /Users/{userId}/PlayedItems/{Id}` and `DELETE /Users/{userId}/PlayedItems/{Id}`; toggle watched state per item |
| **Item Detail Page** | Full metadata display: title, overview, year, rating, runtime, genres, studios, cast/crew, similar items, special features |
| **Similar / Related Items** | `GET /Items/{Id}/Similar` returns related content |
| **People / Cast** | `GET /Items/{Id}/People` returns actors, directors; link to person page with their filmography |
| **Genre / Studio Browsing** | Browse by genre (`GET /Genres`), studio (`GET /Studios`), or year |
| **Intro Skip** | Emby Server can detect intros; clients receive intro timestamps and show a "Skip Intro" button |
| **Session Management** | `GET /Sessions` shows active sessions; remote control of other client sessions |
| **Media Info** | Full media info display: codec, bitrate, resolution, audio channels, container |

### 3. Common Video Client UX Patterns (Cross-Platform)

These patterns appear across Netflix, YouTube, Plex, Jellyfin, Emby, and similar mature video clients:

| UX Pattern | Description |
|---|---|
| **Global Search** | Full-text search across all libraries with type-ahead or instant results; often with recent/popular search suggestions |
| **Continue Watching Row** | Horizontal shelf of partially-watched items with progress bar; prominent placement on home screen |
| **Next Up Row** | Horizontal shelf showing next unwatched episode per series; chronological by last watched date |
| **Autoplay Next Episode** | After episode ends, auto-starts the next; large clients show a countdown overlay ("Next episode in 5...4...3") |
| **Skip Intro Button** | When intro timestamps are available, show a skip button during the intro segment |
| **Filter / Sort Bar** | Library-level controls: sort by name/date added/date played/rating/runtime; filter by genre/year/watched status/unplayed/favorites |
| **Favorites** | Heart/star toggle on items; dedicated "Favorites" tab or filter |
| **Collections** | Group related items (e.g., Marvel series, Star Wars saga) into collections |
| **Watchlist / Bookmarks** | Add to watchlist; separate "My List" section |
| **Mark Watched/Unwatched** | Per-item toggle; syncs to server; affects library display (e.g., unplayed badge count) |
| **Download / Offline** | Save media for offline playback; typically a Premiere/premium feature; download quality selector |
| **Picture-in-Picture** | Mini player overlay when user navigates away; common on Android 8+ |
| **Chromecast / Cast** | Cast to external displays; remote control from the phone |
| **External Player** | Let the user choose an alternate video player (VLC, MX Player) |
| **Subtitle Customization** | Font size, color, outline/background/position; timing offset for desynced subs |
| **Playback Speed** | Variable speed: 0.5x to 3x; remember per-session or per-user preference |
| **Player Gestures** | Swipe left/right to seek; swipe up/down for volume/brightness; double-tap to skip forward/backward |
| **Lock Screen / Media Session** | Android media session for lock screen controls, Bluetooth/headphone button handling |
| **Background Playback** | Continue audio when app is backgrounded (PIP or audio-only) |
| **Sleep Timer** | Auto-pause after a set duration; common in bedtime viewing |
| **Screen Orientation** | Auto-rotate to landscape for video player; lock orientation toggle |
| **Thumbnail Scrubbing** | Show preview thumbnails while seeking through the progress bar |
| **Chapter Marks** | Navigate between chapters within a video |
| **Parental Controls** | PIN-protected content by rating; separate user profiles with different ratings caps |
| **Dark/Light/AMOLED Theme** | Theme switching; AMOLED black mode popular in video apps |
| **Media Info / Codec Display** | Show current video/audio/subtitle codec details during playback |
| **Multi-Server Support** | Switch between multiple Emby/Jellyfin/Plex servers |
| **Library Visibility** | Hide unwanted libraries from the home screen |
| **Notifications** | New episode alerts, server status notifications |

### 4. Nordic Media Hub Current Video Features (As of Round 9)

Based on direct codebase inspection of the current files:

| Feature | Status | Notes |
|---|---|---|
| Emby Auth (API key + user/pass) | Implemented | `EmbyApi.authenticateByName`, `getUsers` |
| Library Discovery | Implemented | `EmbyApi.getUserViews` with video-only collection type filter |
| Video Item Listing | Implemented | `EmbyApi.getItems` with `IncludeItemTypes`, recursive, `Fields=Overview,ProductionYear,...` |
| Thumbnail URL | Implemented | `/Items/{id}/Images/Primary` with tag + token + maxWidth |
| Direct Stream Playback | Implemented | `/Videos/{id}/stream?static=true&MediaSourceId=...&PlaySessionId=...&api_key=...` |
| Playback Progress Reporting | Implemented | Start/Progress/Stop via `Sessions/Playing*` endpoints; reports every 10 seconds |
| Continue Watching Shelf | Implemented | `EmbyApi.getResumeItems` rendered as `VideoContinueWatchingSection` in `VideoScreen` |
| Resume from Progress | Implemented | `VideoPlaybackInfo.resumePositionSeconds` -> `VideoPlaybackEngine.play()` seeks before playback |
| Video Detail Page | Implemented | `VideoDetailScreen` with title, overview, year, duration, type meta chips, play button |
| Series/Season/Episode Browsing | Implemented | `getSeasons`/`getEpisodes` APIs; season chips; episode cards with image + metadata |
| Episode Progress | Implemented | `VideoEpisode.progress: VideoProgress?`; watched/continue-watching labels on episode cards; episode progress passed into temporary `VideoItem` for resume |
| Playback Speed | Implemented | 0.5x/0.75x/1x/1.25x/1.5x/2x in `VideoPlayerScreen` |
| Audio Track Selection | Implemented | `VideoPlaybackEngine.selectAudioTrack` via language-based Media3 track selection |
| Subtitle Track Selection | Implemented | `VideoPlaybackEngine.selectSubtitleTrack` via language-based Media3 selection; external subtitle URL support with `api_key` |
| Subtitle Scale | Implemented | `SubtitleView.setFractionalTextSize(0.0533f * state.subtitleScale)` with A-/A+ controls; range 75%-175% |
| Subtitle Timing Offset | Intentionally Removed | Round 5 audited that Media3 cannot reliably apply timing offset to raster subtitles; offset controls removed per spec |
| Player Gestures | Implemented (basic) | Horizontal drag to seek; vertical drag for volume (right half) and brightness (left half) in `VideoPlayerScreen` |
| Player Error Handling | Implemented | `VideoPlaybackState.errorMessage` surfaced in UI red text |
| Library Selector | Implemented | Horizontal chip row in `VideoScreen` for switching between video libraries |
| Overview Expansion | Implemented | "Expand/Collapse" for long overview text in detail screen |

### Files Found

| File Path | Description |
|---|---|
| `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt` | Emby Retrofit API interface with all endpoints |
| `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt` | Repository mapping Emby DTOs to domain models |
| `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt` | Media3 ExoPlayer-based video playback engine |
| `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt` | Video home: library selector, continue watching, item list |
| `app/src/main/java/com/nordic/mediahub/ui/VideoDetailScreen.kt` | Video detail: metadata, seasons, episodes, play button |
| `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt` | Video player: gestures, controls, track selection, speed |
| `.trellis/spec/backend/emby-integration.md` | Full Emby integration spec with 5 scenarios |
| `.trellis/tasks/archive/2026-06/06-26-media-feature-parity-round4/research/video-emby-yamby.md` | Round 4 prior research |
| `.trellis/tasks/archive/2026-06/06-26-media-feature-parity-enhancements/research/video-emby-yamby-reference.md` | Enhancements prior research |
| `.trellis/tasks/archive/2026-06/06-26-media-feature-parity-enhancements/research/emby-playback-api.md` | Emby playback API prior research |
| `.trellis/tasks/archive/2026-06/06-27-media-feature-parity-round7/research/emby-resume.md` | Resume API prior research |

### Code Patterns

1. **Emby API call pattern**: All API calls go through `EmbyApi` Retrofit interface, require `X-Emby-Token` header, and return `Response<T>`. The `requireBody()` extension throws typed `EmbyApiException` on non-2xx or null body.
2. **Domain model mapping**: All mapping happens in `EmbyRepository` private extension functions (`toVideoItem`, `toVideoSeason`, `toVideoEpisode`, `toVideoMediaTrack`). Compose UI never sees raw DTOs.
3. **Tick-to-seconds conversion**: `EMBY_TICKS_PER_SECOND = 10_000_000L` used consistently for `RunTimeTicks`, `PlaybackPositionTicks`, and `PositionTicks`.
4. **Progress reporting pattern**: `VideoPlaybackEngine` starts a `progressReportJob` that fires `onPlaybackProgress` every 10 seconds; `onPlaybackStopped` fires in `stop()` and `release()`. Reports are fired via `runCatching` so failures never block playback.
5. **Track selection via language**: `applyTrackSelection` uses `setPreferredAudioLanguage` and `setPreferredTextLanguage` on Media3 `TrackSelectionParameters`, which is language-based rather than index-based.

### External References

- Emby Android TV support article: `https://emby.media/support/articles/Android-TV.html` -- describes Direct Play format support, subtitle formats (SRT/SSA/ASS/SUB/VTT/PGS), audio codec support (AAC/MP3/OGG/OPUS/AC3/DTS/TrueHD), and bitrate strategy (Auto vs manual)
- Emby Android release notes: `https://emby.media/community/topic/95467-emby-for-android-release-notes/` -- recent versions (3.5.37 June 2026) include Samsung DeX fixes, subtitle rendering fixes, additional force-transcoding options
- Yamby Google Play listing: `https://play.google.com/store/apps/details?id=com.hush.yamby`
- Yamby Softonic overview: `https://yamby.en.softonic.com/android`
- Emby playback methods: `https://emby.media/support/articles/DirectPlay-Stream-Transcoding.html`
- Emby REST API reference: `https://dev.emby.media/reference/RestAPI/Index.html` (documentation has migrated; some old GitHub Pages links now return 404)

### Related Specs

- `.trellis/spec/backend/emby-integration.md` -- Full Emby integration contract covering 5 scenarios: Read-Only Browsing, Direct Playback, Progress Reporting & Season/Episode, Continue Watching, PlaybackInfo Media Streams & Track Controls

## Feature Gap Analysis: Nordic vs Mature Clients

### Already Implemented (Strong Baseline)

1. Auth + library discovery + item listing
2. Direct stream playback with ExoPlayer/Media3
3. Progress reporting (start/periodic/stop)
4. Continue watching row with progress bars
5. Resume playback from saved position
6. Video detail page with metadata and play button
7. Series/season/episode browsing with progress
8. Playback speed control (0.5x - 2x)
9. Audio/subtitle track selection
10. Subtitle scale control
11. Player gestures (horizontal seek, vertical volume/brightness)
12. Error handling with typed exceptions

### Not Yet Implemented (Parity Gaps, Ranked by Value)

**Tier 1 -- High UX Impact, Reasonable Implementation Effort:**

1. **Search** -- Emby `GET /Search/Hints` with query, media types, item types. Global search across all video libraries. This is the single most requested feature in any media client. Emby endpoint: `Search/Hints?SearchTerm={query}&MediaTypes=Video&IncludeItemTypes=Movie,Episode,Series,Video&Limit=20&UserId={userId}`. Response includes `SearchHint` objects with `Id`, `Name`, `Type`, `ImageTags`, `RunTimeTicks`, `ProductionYear`, `IndexNumber`, `ParentIndexNumber`.

2. **Sort & Filter** -- Extend `EmbyApi.getItems` to accept `SortBy`, `SortOrder`, `Filters`, `Genres`, `Years` parameters. Add a filter/sort bar in `VideoScreen` above the item list. Most mature clients offer: sort by Name/Date Added/Date Played/Year/Rating; filter by Genre/Year/Watched/Unwatched/Favorites.

3. **Next Up (TV Series)** -- Emby `GET /Shows/NextUp?UserId={userId}&Limit=12` returns the next unwatched episode for each series the user has started. Show as a horizontal "Next Up" shelf on the video home, like "Continue Watching" but for TV. Highly desirable for TV show watchers.

4. **Mark Played/Unplayed** -- Emby `POST /Users/{userId}/PlayedItems/{itemId}` and `DELETE /Users/{userId}/PlayedItems/{itemId}`. Allows users to manually mark items as watched or unwatched. Directly affects library display and "Next Up" calculations.

5. **Favorites** -- Emby `POST /Users/{userId}/FavoriteItems/{itemId}` and `DELETE /Users/{userId}/FavoriteItems/{itemId}`. Add favorite toggle on video items; add "Favorites" filter in the library view or a dedicated shelf.

**Tier 2 -- Medium UX Impact, Moderate Implementation Effort:**

6. **Autoplay Next Episode / End-of-Episode Overlay** -- When an episode ends, show a "Next episode in X seconds" countdown, then auto-start the next one. Requires knowing the current series/season/episode ordering and pre-fetching the next episode's PlaybackInfo. This is a core TV-watching UX pattern from Netflix/Emby/Plex.

7. **Skip Intro Button** -- Emby Server can detect intros and provide intro timestamps via `GET /Items/{Id}/Intro`. Show a "Skip Intro" button during the detected intro segment. Requires server-side intro detection to be configured.

8. **Library Hiding / Visibility** -- Store library visibility preferences locally (DataStore). Filter out hidden libraries from `VideoScreen`. Yamby offers this; it is useful when the user has music/photo libraries they want to ignore.

9. **Similar Items / Recommendations** -- Emby `GET /Items/{Id}/Similar?UserId={userId}&Limit=8`. Show related items at the bottom of `VideoDetailScreen`. Improves content discovery.

10. **Latest Items** -- Emby `GET /Users/{userId}/Items/Latest?ParentId={libraryId}&Limit=8`. Shows recently added items per library. Common "Latest" or "Recently Added" shelf on home screens.

11. **People / Cast & Crew** -- Emby `GET /Items/{Id}/People`. Show cast and crew on video detail page, with links to person pages showing their filmography.

**Tier 3 -- Significant UX Impact, Larger Implementation Effort:**

12. **PIP Mode** -- Android Picture-in-Picture. Enter PIP when the user navigates away or presses home during video playback. Requires `enterPictureInPictureMode()` API, manifest configuration, and PIP parameters. Common in Yamby and the official Emby app.

13. **Chromecast / Google Cast** -- Cast Emby video to external displays. Requires Media3 Cast integration, a `MediaLibraryService`, session management, and remote playback control. Significant effort but a major feature gap vs mature clients.

14. **Download / Offline** -- Download video items for offline playback. Emby Premiere feature. Requires file management, download progress UI, offline activation, quality selection, and storage permission handling. Large scope.

15. **Live TV & DVR** -- Browse channels, view guide, watch live streams, manage recordings. Requires multiple Emby API endpoints and a significant UI layer. Deferrable.

16. **Transcoding / Quality Preference** -- Allow user to choose max streaming bitrate, force-transcoding vs direct-play, and select transcoding quality. Requires HLS URL construction (`/Videos/{Id}/master.m3u8`), active encoding cleanup, and bitrate/codec negotiation. Complex.

17. **Parental Controls** -- Per-user content restrictions based on parental ratings. Requires user profile with `MaxParentalRating` support and content filtering throughout the app.

18. **External Player Support** -- Option to open video in an external player (VLC, MX Player) using an `Intent` with the stream URL and token. Medium effort.

19. **Media Info Display** -- Show detailed codec/resolution/bitrate/container info for the currently playing item. Requires mapping more `MediaSourceDto` and `MediaStreamDto` fields.

20. **Intro/Credits Detection** -- Emby can detect intros and provide timestamp data. Client shows a "Skip" button during intro. Requires API integration with `GET /Items/{Id}/Intro`.

21. **Danmaku / Bullet Comments** -- Niche feature popular in Asian video culture (Bilibili-style). Yamby offers this as a differentiator. Large scope; requires a danmaku rendering layer.

### Emby API Endpoints Not Yet Used in Nordic

| Endpoint | Feature | Priority |
|---|---|---|
| `GET /Search/Hints` | Global search across libraries | Tier 1 |
| `GET /Shows/NextUp` | Next unwatched episode shelf | Tier 1 |
| `POST /Users/{userId}/FavoriteItems/{id}` | Mark favorite | Tier 1 |
| `DELETE /Users/{userId}/FavoriteItems/{id}` | Unfavorite | Tier 1 |
| `POST /Users/{userId}/PlayedItems/{id}` | Mark played | Tier 1 |
| `DELETE /Users/{userId}/PlayedItems/{id}` | Mark unplayed | Tier 1 |
| `GET /Users/{userId}/Items/Latest` | Latest/recently added items | Tier 2 |
| `GET /Items/{id}/Similar` | Related/recommended items | Tier 2 |
| `GET /Items/{id}/People` | Cast & crew | Tier 2 |
| `GET /Genres` | Genre browsing | Tier 2 |
| `GET /Studios` | Studio browsing | Tier 2 |
| `GET /Items/{id}/Intro` | Intro timestamps for skip button | Tier 2 |
| `GET /Videos/{id}/master.m3u8` | HLS transcoding stream | Tier 3 |
| `GET /LiveTv/Channels` | Live TV channel list | Tier 3 |
| `GET /LiveTv/Programs` | TV guide/program listing | Tier 3 |
| `GET /Sessions` | Active session management | Tier 3 |

### SortBy Values Supported by Emby

These are the available sort options for `GET /Users/{userId}/Items?SortBy=`:

- `SortName` -- Alphabetical by name
- `DateCreated` -- When the item was added to the library
- `DatePlayed` -- When the item was last played
- `ProductionYear` -- Release year
- `CommunityRating` -- User/community rating
- `CriticRating` -- Critic score
- `Random` -- Randomized order
- `Runtime` -- Duration
- `IsFavorite` -- Favorites first
- `IsUnplayed` -- Unplayed first

### Filter Values Supported by Emby

These are the available filter options for `GET /Users/{userId}/Items?Filters=`:

- `IsFavorite` -- Only favorited items
- `IsUnplayed` -- Only items not yet watched
- `IsResumable` -- Items with partial playback progress
- `IsPlayed` -- Only fully watched items

Additional filter parameters:
- `Genres` -- Comma-separated genre names
- `Years` -- Comma-separated production years
- `Studios` -- Comma-separated studio names
- `OfficialRatings` -- Comma-separated parental ratings
- `Tags` -- Comma-separated tags
- `MinCommunityRating` -- Minimum community rating (float)
- `HasSubtitles` -- Boolean filter for items with subtitles

## Caveats / Not Found

- Yamby's Google Play page and Softonic listing are JS-rendered and could not be fully scraped in this session. Feature list for Yamby is based on prior research rounds (rounds 2, 4, enhancements) which successfully captured the feature list at that time.
- Emby's official support documentation has migrated from GitHub Pages; several article URLs (Home-Screen.html, Searching.html) now return 404. The Android TV article was the only one successfully loaded.
- The Emby REST API reference at `dev.emby.media` returns mainly CSS/JS references when scraped; the actual endpoint documentation requires a JS-capable browser.
- No explicit Yamby GitHub repository was found. Yamby appears to be a closed-source app.
- Feature details like intro-skip, next-up, and similar-items endpoints are well-documented in the Emby REST API community discussions and prior research, even though the live API docs page could not be scraped this session.
