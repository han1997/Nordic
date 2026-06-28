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
  - `fun cyclePlaybackSpeed()`
  - `fun togglePlayPause()`
  - `fun stop()`
- Music playback engine:
  - `fun stop()` clears music Media3 state before audiobook playback takes over the shared session service.
- App shell:
  - `internal fun resolveAudiobookPlayRequestAction(currentSession: AudiobookPlaybackSession?, requestedLibraryItemId: String): AudiobookPlayRequestAction`

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
- Library refresh must resolve the selected library id against the latest `GET /api/libraries` response. Keep the previous selection only if that id is still present; otherwise fall back to the first returned library, or clear the selection when the response is empty.
- Audio URLs may need the bearer token appended as `token=<token>` when AudiobookShelf returns relative `contentUrl` values.
- Progress sync must use current absolute audiobook time, not current track-local time, and repository payloads must clamp `currentTime` to `0..durationSeconds` before sending progress, session sync, or close requests.
- Periodic session sync `timeListened` must measure only newly listened time in the current app session. Initialize the delta baseline from the greater of playback state position, `AudiobookPlaybackSession.startTimeSeconds`, and `currentTimeSeconds` so resumed books do not count already-listened time again.
- When session duration is zero or negative, progress/session/close `currentTime` must be `0.0`; request `duration` may be floored to `1.0` only to keep progress math and ABS payloads well-formed.
- Playback state must resolve absolute audiobook progress as `track.startOffsetSeconds + localPositionSeconds`, with the known-track local position clamped to `0..track.durationSeconds` before adding the track offset.
- Absolute audiobook seek positions must be mapped to the Media3 track list as `(mediaItemIndex, localOffsetSeconds)` and the local offset must be clamped to `0..track.durationSeconds`.
- Relative skip controls must resolve to an absolute audiobook position and use the same `seekTo(positionSeconds)` path as the scrubber.
- Relative skip targets must be clamped to `0..durationSeconds`; do not seek negative or beyond the audiobook duration.
- Playback speed is Media3 player state. `AudiobookPlaybackState.playbackSpeed` must reflect `Player.playbackParameters.speed`, and `cyclePlaybackSpeed()` cycles common audiobook steps: `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`.
- Chapter navigation must seek by absolute audiobook seconds, using the same `seekTo(positionSeconds)` path as the scrubber. Do not seek by track-local time when moving between chapters.
- Previous chapter behavior should restart the current chapter when playback is at or beyond a small threshold into it; before that threshold, it should jump to the previous chapter when one exists.
- Next chapter behavior should jump to the next chapter start when one exists.
- `PATCH /api/me/progress/*`, `POST /api/session/*/sync`, and `POST /api/session/*/close` must validate `Response<Unit>.isSuccessful`. Do not fire-and-forget these session endpoints.
- UI close flows should call `syncAndCloseSession(...)` so the final position is written before closing the AudiobookShelf session.
- When starting music or video while an audiobook is active, the app-shell should stop audiobook playback and attempt `syncAndCloseSession(...)` in the background. If that background close fails, do not reopen the stopped audiobook player over the newly selected media.
- When starting audiobook playback while the requested item already owns the active session, the app-shell should reuse the current session and show the audiobook player instead of calling `/play` again.
- When starting audiobook playback while a different audiobook session is active, the app-shell should stop the old local session and attempt `syncAndCloseSession(...)` in the background before calling `/play` for the new item. The new playback attempt must not be blocked by a background close failure.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Missing server URL or username | Do not construct `AudiobookShelfRepository`; show configuration state |
| Login HTTP failure | Throw `AudiobookShelfApiException.Kind.HTTP` with status code |
| Login response lacks token | Throw `AudiobookShelfApiException.Kind.AUTH` |
| Previously selected library id is absent from the latest library list | Fall back to the first returned library before requesting items |
| Latest library list is empty | Clear the selected library id and show the empty-library state |
| Library/item/playback response is empty | Throw `AudiobookShelfApiException.Kind.API` |
| Playback session has no playable tracks | Keep session visible with a playback error; do not start Media3 |
| Media3 reports a known-track local position beyond that track duration | Clamp the local position to the track duration before publishing absolute playback progress |
| Absolute seek target is before the first track | Seek to media item `0` at offset `0` |
| Absolute seek target is beyond the final track duration | Seek to the final media item with local offset clamped to that track duration |
| 30 second skip back is requested near the book start | Clamp to `0` |
| 30 second skip forward is requested near the book end | Clamp to `durationSeconds` |
| Playback speed is on an unknown/off-grid value | Cycle to the next higher supported speed, or wrap to the first speed |
| Chapter list is empty | Chapter navigation controls are disabled or engine chapter commands no-op |
| Previous chapter requested near the first chapter start | No-op instead of seeking to a negative position |
| Next chapter requested from the last chapter | No-op instead of seeking past duration |
| Progress sync fails while player is visible | Surface a progress-sync error without crashing playback |
| Periodic sync starts from a resumed session while playback state is still `0` | Use the session resume/current time as the first `timeListened` delta baseline |
| Progress/session close current time is negative or beyond duration | Clamp payload `currentTime` to `0..durationSeconds` |
| Progress/session close duration is zero or negative | Send `currentTime: 0.0`; keep request `duration` safe for progress math |
| Progress update/session sync/session close returns non-2xx | Throw `AudiobookShelfApiException.Kind.HTTP` with the failing status code |
| User leaves audiobook playback | Call `syncAndCloseSession(...)` with the last absolute position and clear Media3 audiobook state |
| Background close fails during music/video handoff | Keep the new media surface active; do not reopen the stopped audiobook player |
| User starts audiobook playback while music is active | Call `MusicPlaybackEngine.stop()` before `AudiobookPlaybackEngine.play(...)` |
| User starts the same audiobook that is already active | Reuse the current session and show the player; do not call `startPlayback()` again |
| User starts a different audiobook while one is active | Background sync/close the old session, then start the requested item |

### 5. Good/Base/Bad Cases

- Good: User opens an audiobook, `startPlayback()` returns a session, Media3 plays session tracks, progress sync runs periodically, and `syncAndCloseSession()` is called when leaving the player.
- Good: User taps the same audiobook while it is already active; the app reopens the current player without creating a duplicate AudiobookShelf session.
- Good: User starts a different audiobook while one is active; the app syncs/closes the previous session in the background and starts the new `/play` session.
- Good: User switches AudiobookShelf server/account, refreshes libraries, and the app requests items from a library id returned by that server instead of a stale id from the previous server/account.
- Good: User can skip back/forward by the fixed audiobook interval; progress sync keeps using the resulting absolute position.
- Good: User can cycle playback speed from the player, and Media3 playback speed plus UI state stay in sync.
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
  - progress/session/close `currentTime` payload clamps to `0..durationSeconds`
  - zero-duration progress/session/close payloads keep `currentTime` at `0.0` while using a safe request duration
  - HTTP and empty-body errors map to typed `AudiobookShelfApiException` kinds
  - non-2xx progress/session responses throw `AudiobookShelfApiException.Kind.HTTP`
- UI/helper tests should assert library selection resolution keeps an existing id, falls back from a stale id, and clears selection for an empty library list.
- App-shell helper tests should assert periodic sync baseline resolution uses the session resume/current time when playback state has not caught up, uses playback state when it is ahead, and clamps negative values to zero.
- App-shell helper tests should assert manual audiobook close failures present the player/error, while background handoff close failures do not reopen the player.
- App-shell helper tests should assert audiobook play requests start a new session with no current session, reuse the current session for the same `libraryItemId`, and close the current session before starting a different `libraryItemId`.
- Playback tests should assert:
  - absolute audiobook position is track offset plus player position
  - absolute audiobook position clamps known-track local player position to the track duration
  - absolute seek target mapping resolves the expected media item index and clamps local offsets at track boundaries
  - relative skip target calculation clamps at the beginning and end of the audiobook
  - playback speed cycling covers known values and unknown/off-grid values
  - previous/next chapter helpers resolve absolute chapter start positions, including exact restart-threshold boundaries and missing chapter cases
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
