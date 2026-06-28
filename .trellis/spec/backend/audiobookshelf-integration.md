# AudiobookShelf Integration Contract

## Scenario: Android AudiobookShelf Auth, Library, Playback, and Progress

### 1. Scope / Trigger

- Trigger: Any change to AudiobookShelf login, library browsing, playback-session start, progress sync, or session close behavior in the Android app.
- This is cross-layer work: local config, Retrofit DTOs, repository mapping, Compose state, Media3 playback, and AudiobookShelf session APIs must stay aligned.
- The implementation must treat AudiobookShelf playback as a session contract, not just as raw audio URLs.

### 2. Signatures

- Config readiness: `AudiobookShelfConfig.isReadyForAudiobookSync(): Boolean`
- Repository:
  - `suspend fun getLibraries(): List<AudiobookLibrarySummary>`
  - `suspend fun getLibraryItems(libraryId: String): List<AudiobookItemSummary>`
  - `suspend fun getLibraryItem(itemId: String): AudiobookItemDetail`
  - `suspend fun startPlayback(itemId: String): AudiobookPlaybackSession`
  - `suspend fun syncProgress(session: AudiobookPlaybackSession, currentTimeSeconds: Int, deltaSeconds: Int)`
  - `suspend fun closeSession(session: AudiobookPlaybackSession, currentTimeSeconds: Int)`
  - `suspend fun syncAndCloseSession(session: AudiobookPlaybackSession, currentTimeSeconds: Int, deltaSeconds: Int = 0)`
- Playback engine:
  - `fun play(session: AudiobookPlaybackSession)`
  - `fun seekTo(positionSeconds: Int)`
  - `fun seekBackBy(intervalSeconds: Int = 30)`
  - `fun seekForwardBy(intervalSeconds: Int = 30)`
  - `fun seekToPreviousChapter()`
  - `fun seekToNextChapter()`
  - `fun togglePlayPause()`
  - `fun stop()`
- Music playback engine:
  - `fun stop()` clears music Media3 state before audiobook playback takes over the shared session service.

### 3. Contracts

- Login uses `POST /login` with `x-return-tokens: true`.
- Authenticated AudiobookShelf API calls pass `Authorization: Bearer <token>`.
- MVP endpoints:
  - `GET /api/libraries`
  - `GET /api/libraries/{id}/items?minified=1`
  - `GET /api/items/{id}?expanded=1&include=progress`
  - `POST /api/items/{id}/play`
  - `PATCH /api/me/progress/{libraryItemId}`
  - `POST /api/session/{sessionId}/sync`
  - `POST /api/session/{sessionId}/close`
- Domain mapping must keep music and audiobook models separate. Do not map audiobook sessions into `NavidromeSong`.
- Audio URLs may need the bearer token appended as `token=<token>` when AudiobookShelf returns relative `contentUrl` values.
- Progress sync must use current absolute audiobook time, not current track-local time.
- Relative skip controls must resolve to an absolute audiobook position and use the same `seekTo(positionSeconds)` path as the scrubber.
- Relative skip targets must be clamped to `0..durationSeconds`; do not seek negative or beyond the audiobook duration.
- Chapter navigation must seek by absolute audiobook seconds, using the same `seekTo(positionSeconds)` path as the scrubber. Do not seek by track-local time when moving between chapters.
- Previous chapter behavior should restart the current chapter when playback is more than a small threshold into it; near the start of a chapter, it should jump to the previous chapter when one exists.
- Next chapter behavior should jump to the next chapter start when one exists.
- `PATCH /api/me/progress/*`, `POST /api/session/*/sync`, and `POST /api/session/*/close` must validate `Response<Unit>.isSuccessful`. Do not fire-and-forget these session endpoints.
- UI close flows should call `syncAndCloseSession(...)` so the final position is written before closing the AudiobookShelf session.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Missing server URL or username | Do not construct `AudiobookShelfRepository`; show configuration state |
| Login HTTP failure | Throw `AudiobookShelfApiException.Kind.HTTP` with status code |
| Login response lacks token | Throw `AudiobookShelfApiException.Kind.AUTH` |
| Library/item/playback response is empty | Throw `AudiobookShelfApiException.Kind.API` |
| Playback session has no playable tracks | Keep session visible with a playback error; do not start Media3 |
| 30 second skip back is requested near the book start | Clamp to `0` |
| 30 second skip forward is requested near the book end | Clamp to `durationSeconds` |
| Chapter list is empty | Chapter navigation controls are disabled or engine chapter commands no-op |
| Previous chapter requested near the first chapter start | No-op instead of seeking to a negative position |
| Next chapter requested from the last chapter | No-op instead of seeking past duration |
| Progress sync fails while player is visible | Surface a progress-sync error without crashing playback |
| Progress update/session sync/session close returns non-2xx | Throw `AudiobookShelfApiException.Kind.HTTP` with the failing status code |
| User leaves audiobook playback | Call `syncAndCloseSession(...)` with the last absolute position and clear Media3 audiobook state |
| User starts audiobook playback while music is active | Call `MusicPlaybackEngine.stop()` before `AudiobookPlaybackEngine.play(...)` |

### 5. Good/Base/Bad Cases

- Good: User opens an audiobook, `startPlayback()` returns a session, Media3 plays session tracks, progress sync runs periodically, and `syncAndCloseSession()` is called when leaving the player.
- Good: User can skip back/forward by the fixed audiobook interval; progress sync keeps using the resulting absolute position.
- Good: User can jump to previous/next chapters from the player; absolute position updates continue to drive progress sync.
- Base: User only browses libraries and details; no playback session is created and no progress endpoint is called.
- Bad: App extracts a stream URL and plays it without calling `/play`, `/sync`, or `/close`; AudiobookShelf resume state will drift.

### 6. Tests Required

- Compile/build checks must cover all changed Android code:
  - `:app:compileDebugKotlin`
  - `:app:lintDebug`
  - `:app:testDebugUnitTest`
  - `:app:assembleDebug` for final packaging verification when playback wiring changes.
- Repository tests should assert:
  - login token fallback from `user.token` to `user.accessToken`
  - relative cover/audio URL normalization
  - progress fraction and current time payload fields
  - HTTP and empty-body errors map to typed `AudiobookShelfApiException` kinds
  - non-2xx progress/session responses throw `AudiobookShelfApiException.Kind.HTTP`
- Playback tests should assert:
  - absolute audiobook position is track offset plus player position
  - relative skip target calculation clamps at the beginning and end of the audiobook
  - previous/next chapter helpers resolve absolute chapter start positions, including restart threshold and missing chapter cases
  - `stop()` clears session state and media items
  - closing playback calls repository `closeSession()` with the last absolute position

### 7. Wrong vs Correct

#### Wrong

```kotlin
val session = repository.startPlayback(item.id)
player.setUri(session.audioTracks.first().contentUrl)
// No periodic sync and no closeSession call.
```

#### Correct

```kotlin
val session = repository.startPlayback(item.id)
audiobookPlaybackEngine.play(session)

// Periodically while active:
repository.syncProgress(session, currentTimeSeconds, deltaSeconds)

// When leaving playback:
repository.syncAndCloseSession(session, currentTimeSeconds)
audiobookPlaybackEngine.stop()
```
