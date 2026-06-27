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
- Periodic sync delta must be calculated from the last successful absolute sync position and clamped at zero if playback seeks backwards.
- `PATCH /api/me/progress/*`, `POST /api/session/*/sync`, and `POST /api/session/*/close` must validate `Response<Unit>.isSuccessful`. Do not fire-and-forget these session endpoints.
- UI close flows should call `syncAndCloseSession(...)` so the final position is written before closing the AudiobookShelf session.
- UI close flows should clear Media3 audiobook state only after `syncAndCloseSession(...)` succeeds. If close/sync fails, keep the player visible and surface the error so the user can retry without losing the session state.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Missing server URL or username | Do not construct `AudiobookShelfRepository`; show configuration state |
| Login HTTP failure | Throw `AudiobookShelfApiException.Kind.HTTP` with status code |
| Login response lacks token | Throw `AudiobookShelfApiException.Kind.AUTH` |
| Library/item/playback response is empty | Throw `AudiobookShelfApiException.Kind.API` |
| Playback session has no playable tracks | Keep session visible with a playback error; do not start Media3 |
| Progress sync fails while player is visible | Surface a progress-sync error without crashing playback |
| Progress update/session sync/session close returns non-2xx | Throw `AudiobookShelfApiException.Kind.HTTP` with the failing status code |
| User leaves audiobook playback and close succeeds | Call `syncAndCloseSession(...)` with the last absolute position, then clear Media3 audiobook state |
| User leaves audiobook playback and close fails | Keep the player visible, preserve Media3 audiobook state, and show the close error |
| User starts audiobook playback while music is active | Call `MusicPlaybackEngine.stop()` before `AudiobookPlaybackEngine.play(...)` |

### 5. Good/Base/Bad Cases

- Good: User opens an audiobook, `startPlayback()` returns a session, Media3 plays session tracks, progress sync runs periodically, and `syncAndCloseSession()` is called when leaving the player.
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
  - `stop()` clears session state and media items
  - closing playback calls repository `closeSession()` with the last absolute position
  - sync delta helpers clamp backwards movement to zero and initialize from the furthest known absolute position

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

## Scenario: Continue Listening and Last Audiobook Resume

### 1. Scope / Trigger

- Trigger: Any change to AudiobookShelf library item listing, progress DTO mapping, home resume shelves, or the saved last-audiobook pointer.
- This is cross-layer work: `GET /api/libraries/{id}/items`, repository summary mapping, DataStore config, and Compose home sections must agree on the progress contract.

### 2. Signatures

- API:
```kotlin
@GET("/api/libraries/{id}/items")
suspend fun getLibraryItems(
    @Header("Authorization") bearerToken: String,
    @Path("id") libraryId: String,
    @Query("minified") minified: Int = 1,
    @Query("include") include: String = "progress",
    @Query("limit") limit: Int = 50,
    @Query("page") page: Int = 0
): Response<AudiobookShelfLibraryItemsResponse>
```
- DTO/domain:
```kotlin
data class AudiobookShelfLibraryItemMinifiedDto(
    ..., val userMediaProgress: AudiobookShelfMediaProgressDto? = null
)

data class AudiobookItemSummary(
    ..., val progress: AudiobookProgress? = null
)
```
- Persistence:
```kotlin
val ConfigRepository.lastAudiobookItemId: Flow<String?>
suspend fun ConfigRepository.saveLastAudiobookItemId(itemId: String)
```

### 3. Contracts

- Library item list requests must include `include=progress`; otherwise Continue Listening cannot be derived without fetching every detail page.
- `AudiobookShelfRepository` maps `userMediaProgress` to `AudiobookItemSummary.progress` using the same `AudiobookProgress` domain model as expanded item detail.
- Continue Listening includes only unfinished items with `currentTimeSeconds > 0` and sorts by `lastUpdateMillis` descending.
- The app saves the last audiobook item id when playback starts successfully, not when the card is merely opened.
- The last audiobook resume card may only be shown when the saved id exists in the currently loaded library item list. Do not synthesize partial book rows from the saved id alone.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| List item has no `userMediaProgress` | Render as a normal item; exclude from Continue Listening |
| Progress is finished or current time is zero | Exclude from Continue Listening |
| Saved last item id is blank or missing from loaded items | Hide the last-played card |
| Library item list request fails | Surface the normal AudiobookShelf library error and keep player state unchanged |
| Playback start fails | Do not overwrite `lastAudiobookItemId` |

### 5. Good/Base/Bad Cases

- Good: Library list returns progress for three unfinished books; home shows them newest-first and the last-played card points to the saved item when present.
- Base: Server returns items without progress; library browsing still works and resume shelves are hidden.
- Bad: UI calls item detail for every row just to discover progress, causing slow home loads and avoidable API traffic.

### 6. Tests Required

- Repository test asserting `getLibraryItems()` sends `include=progress`.
- Repository test asserting `userMediaProgress.currentTime`, `duration`, `progress`, `isFinished`, and `lastUpdate` map to `AudiobookProgress`.
- Persistence/helper test when resume selection logic becomes non-trivial: unfinished progress is included, finished/zero-progress items are excluded, and sorting uses `lastUpdateMillis`.

### 7. Wrong vs Correct

#### Wrong
```kotlin
// Fetch every item detail just to build the resume shelf.
items.map { repository.getLibraryItem(it.id).progress }
```

#### Correct
```kotlin
// Request progress with the list payload and derive shelves locally.
val continueItems = items.filter { item ->
    item.progress?.let { !it.isFinished && it.currentTimeSeconds > 0 } == true
}
```
