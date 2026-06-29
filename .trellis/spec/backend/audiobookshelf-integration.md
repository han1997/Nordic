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
- UI helpers:
  - `internal fun resolveAudiobookSelectedLibraryId(currentLibraryId: String?, libraries: List<AudiobookLibrarySummary>): String?`
  - `internal fun resolveAudiobookSelectedItemAfterLibraryRefresh(selectedItem: AudiobookItemDetail?, items: List<AudiobookItemSummary>): AudiobookItemDetail?`
  - `internal fun resolveAudiobookLibraryPageAfterRefresh(currentPage: AudiobookLibraryPage, previousSelectedItem: AudiobookItemDetail?, refreshedSelectedItem: AudiobookItemDetail?): AudiobookLibraryPage`
  - `internal fun resolveAudiobookLibraryPageAfterConfigChange(currentPage: AudiobookLibraryPage): AudiobookLibraryPage`
- DTOs:
  - `AudiobookShelfLibrariesResponse.libraries: List<AudiobookShelfLibraryDto>?`
  - `AudiobookShelfLibraryDto.id: String?`
  - `AudiobookShelfLibraryDto.name: String?`
  - `AudiobookShelfLibraryDto.mediaType: String?`
  - `AudiobookShelfLibraryItemsResponse.results: List<AudiobookShelfLibraryItemMinifiedDto>?`
  - `AudiobookShelfLibraryItemMinifiedDto.id: String?`
  - `AudiobookShelfLibraryItemMinifiedDto.libraryId: String?`
  - `AudiobookShelfLibraryItemMinifiedDto.mediaType: String?`
  - `AudiobookShelfLibraryItemMinifiedDto.media: AudiobookShelfBookMinifiedDto?`
  - `AudiobookShelfBookMinifiedDto.id: String?`
  - `AudiobookShelfBookMinifiedDto.metadata: AudiobookShelfBookMinifiedMetadataDto?`
  - `AudiobookShelfBookMinifiedMetadataDto.title: String?`

### 3. Contracts

- Login uses `POST /login` with `x-return-tokens: true`.
- Login responses must include a `user` object with either a non-blank `token` or a non-blank `accessToken`.
- Prefer non-blank `user.token`; if it is missing, null, empty, or blank, fall back to non-blank `user.accessToken`.
- Authenticated AudiobookShelf API calls pass `Authorization: Bearer <token>`.
- MVP endpoints:
  - `GET /api/libraries`
  - `GET /api/libraries/{id}/items?minified=1&limit=<pageSize>&page=<page>`
  - `GET /api/items/{id}?expanded=1&include=progress`
  - `POST /api/items/{id}/play`
  - `PATCH /api/me/progress/{libraryItemId}`
  - `POST /api/session/{sessionId}/sync`
  - `POST /api/session/{sessionId}/close`
- Domain mapping must keep music and audiobook models separate. Do not map audiobook sessions into `NavidromeSong`.
- Library item browsing must page `GET /api/libraries/{id}/items` with a fixed repository page size. Continue requesting `page + 1` until the response `total` count is loaded, or until the server returns an empty/short page.
- Library item page result arrays are optional wire fields. DTOs must model `results` as nullable, and `getLibraryItems()` must normalize it with `orEmpty()` before mapping summaries and evaluating pagination stop conditions.
- Minified library item row fields are optional wire fields. DTOs must model item `id`, `libraryId`, `mediaType`, `media`, nested book `id`, nested `metadata`, and metadata `title` as nullable.
- `getLibraryItems()` must skip minified item rows that lack a non-blank item `id`, a `media` object, a `metadata` object, or a non-blank metadata `title`. Keep mapping other valid rows from the same response.
- Valid minified item rows with missing, null, empty, or blank row `libraryId` must use the requested endpoint library id as the returned `AudiobookItemSummary.libraryId`.
- Library item pagination must compare server `total` to the count of fetched rows, not the count of mapped summaries. Skipped unusable rows must not force extra page requests after the declared total has already been fetched.
- Library discovery response arrays are optional wire fields. DTOs must model `libraries` as nullable, and `getLibraries()` must normalize it with `orEmpty()` before applying the audiobook media type filter.
- Library discovery must include libraries whose `mediaType` equals `book` case-insensitively, and must continue excluding non-book media types such as podcasts.
- Library discovery item fields are optional wire fields. DTOs must model `AudiobookShelfLibraryDto.id`, `name`, and `mediaType` as nullable strings. `getLibraries()` must skip rows whose trimmed `id` or `name` is blank, and must skip rows whose trimmed `mediaType` is blank or not `book` case-insensitively.
- Returned `AudiobookLibrarySummary` values should use trimmed `id`, `name`, and `mediaType` values so UI state and follow-up item requests do not carry accidental surrounding whitespace.
- Audiobook `coverPath` values map to nullable app artwork URLs: null, empty, and whitespace-only paths stay `null`; non-blank relative paths are normalized against the server base URL; absolute `http://` and `https://` cover URLs are preserved.
- Expanded audiobook detail responses may omit `media.metadata.authors`, `media.metadata.narrators`, `media.metadata.series`, and `media.chapters`, or send them as null. DTOs must allow those fields to deserialize as nullable lists, and repository detail mapping must convert them to empty domain lists.
- Playback-session responses may omit `chapters` and `audioTracks`, or send them as null. DTOs must allow those fields to deserialize as nullable lists, and `startPlayback()` must convert them to empty domain lists.
- Library refresh must resolve the selected library id against the latest `GET /api/libraries` response. Keep the previous selection only if that id is still present; otherwise fall back to the first returned library, or clear the selection when the response is empty.
- Library refresh must reconcile an open detail item against the refreshed selected-library item summaries. Keep the open detail only if its id still exists in the refreshed summaries.
- If the detail page is open and the selected detail no longer exists after refresh, clear the selected detail and return to the library list. Do not show stale detail for books removed from or moved out of the selected library.
- If the selected detail still exists, keeping the already-loaded detail is allowed; do not re-fetch detail metadata unless the PRD explicitly adds that scope.
- Saved AudiobookShelf config changes are browsing boundaries. Return the Audiobook tab to `AudiobookLibraryPage.Home` and clear `libraries`, `selectedLibraryId`, `items`, `selectedItem`, pending detail id, stale loading state, and stale error state before loading the new account.
- In-flight library refresh, library selection, or detail requests from a previous saved config must not write list/detail/error/loading state after the config boundary. Guard these writes with a config-state version or equivalent request identity.
- Same-config manual refresh keeps the existing library/detail reconciliation behavior: preserve the selected library id and selected detail only when they still exist in the refreshed same-account responses.
- Audio URLs may need the bearer token appended as `token=<token>` when AudiobookShelf returns relative `contentUrl` values.
- Detect existing audio URL tokens by parsing query parameter names, not by raw substring search. If `token=` appears in the path but no `token` query parameter exists, still append the bearer token.
- Progress sync must use current absolute audiobook time, not current track-local time, and repository payloads must clamp `currentTime` to `0..durationSeconds` before sending progress, session sync, or close requests.
- Periodic session sync `timeListened` must measure only newly listened time in the current app session. Initialize the delta baseline from the greater of playback state position, `AudiobookPlaybackSession.startTimeSeconds`, and `currentTimeSeconds` so resumed books do not count already-listened time again. Report at least the last successfully synced baseline so an early zero-position player state cannot regress AudiobookShelf resume metadata.
- When session duration is zero or negative, progress/session/close `currentTime` must be `0.0`; request `duration` may be floored to `1.0` only to keep progress math and ABS payloads well-formed.
- Playback state must resolve absolute audiobook progress as `track.startOffsetSeconds + localPositionSeconds`, with the known-track local position clamped to `0..track.durationSeconds` before adding the track offset.
- Absolute audiobook seek positions must be mapped to the Media3 track list as `(mediaItemIndex, localOffsetSeconds)` and the local offset must be clamped to `0..track.durationSeconds`.
- Relative skip controls must resolve to an absolute audiobook position and use the same `seekTo(positionSeconds)` path as the scrubber.
- Relative skip targets must be clamped to `0..durationSeconds`; do not seek negative or beyond the audiobook duration.
- Playback speed is Media3 player state. `AudiobookPlaybackState.playbackSpeed` must reflect `Player.playbackParameters.speed`, and `cyclePlaybackSpeed()` cycles common audiobook steps: `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`.
- Chapter navigation must seek by absolute audiobook seconds, using the same `seekTo(positionSeconds)` path as the scrubber. Do not seek by track-local time when moving between chapters.
- Previous chapter behavior should restart the current chapter when playback is at or beyond a small threshold into it; before that threshold, it should jump to the previous chapter when one exists.
- Next chapter behavior should jump to the next chapter start when one exists.
- Player current-chapter display must resolve by sorted chapter `startSeconds`, not by incoming list order. While scrubbing, use the visible scrub position for display-only chapter resolution.
- Audiobook detail chapter lists must render in ascending `startSeconds` order, preserving original relative order for equal starts, so the visible detail order matches playback timeline order even when the server payload is unordered.
- `PATCH /api/me/progress/*`, `POST /api/session/*/sync`, and `POST /api/session/*/close` must validate `Response<Unit>.isSuccessful`. Do not fire-and-forget these session endpoints.
- UI close flows should call `syncAndCloseSession(...)` so the final position is written before closing the AudiobookShelf session. When a resumed session exists, close flows must use the same resume-aware baseline instead of a lower raw player position.
- When starting music or video while an audiobook is active, the app-shell should stop audiobook playback and attempt `syncAndCloseSession(...)` in the background. If that background close fails, do not reopen the stopped audiobook player over the newly selected media.
- When starting audiobook playback while the requested item already owns the active session, the app-shell should reuse the current session and show the audiobook player instead of calling `/play` again.
- When starting audiobook playback while a different audiobook session is active, the app-shell should stop the old local session and attempt `syncAndCloseSession(...)` in the background before calling `/play` for the new item. The new playback attempt must not be blocked by a background close failure.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| Missing server URL or username | Do not construct `AudiobookShelfRepository`; show configuration state |
| Login HTTP failure | Throw `AudiobookShelfApiException.Kind.HTTP` with status code |
| Login response lacks `user` or any non-blank token/accessToken | Throw `AudiobookShelfApiException.Kind.AUTH` |
| Library response omits `libraries` or sends it as null | Return an empty library list |
| Library response has `mediaType` values such as `book`, `Book`, or `BOOK` | Include those libraries in `getLibraries()` |
| Library response has non-book `mediaType` values | Exclude those libraries from `getLibraries()` |
| Library row omits `id`, `name`, or `mediaType`, or sends any as null | Skip that row and continue mapping other libraries |
| Library row has blank `id`, `name`, or `mediaType` | Skip that row and continue mapping other libraries |
| Library items response omits `results` or sends it as null on the first page | Return an empty item list |
| Library item row omits item `id` or sends it as null, empty, or blank | Skip that row and continue mapping other valid rows |
| Library item row omits `media`, omits `metadata`, omits metadata `title`, or sends any as null | Skip that row and continue mapping other valid rows |
| Library item row sends metadata `title` as empty or blank | Skip that row and continue mapping other valid rows |
| Valid library item row omits row `libraryId` or sends it as null, empty, or blank | Map `AudiobookItemSummary.libraryId` to the requested endpoint library id |
| Summary/detail/session `coverPath` is null, empty, or whitespace-only | Map cover URL to `null` |
| Summary/detail/session `coverPath` is relative or absolute | Normalize relative paths with the server base URL; preserve absolute HTTP(S) URLs |
| Library items response total is larger than the first page | Continue requesting subsequent pages and merge summaries before returning |
| Library items response fetched row count reaches `total` while mapped summaries are fewer because rows were skipped | Stop pagination without requesting another page |
| Library items response omits `results` or sends it as null on a later page | Stop pagination and return the items accumulated so far |
| Expanded detail `metadata.authors`, `metadata.narrators`, `metadata.series`, or `chapters` is missing or null | Map the corresponding `AudiobookItemDetail` list to `emptyList()` |
| Expanded detail authors, narrators, series sequence values, or chapters are present | Preserve mapped author/narrator names, `Series #sequence` labels, and chapter id/title/start/end values |
| Playback session `chapters` or `audioTracks` is missing or null | Map the corresponding `AudiobookPlaybackSession` list to `emptyList()` |
| Playback session chapters or audio tracks are present | Preserve chapter id/title/start/end values and playable audio track URL/token mapping |
| Previously selected library id is absent from the latest library list | Fall back to the first returned library before requesting items |
| Latest library list is empty | Clear the selected library id and show the empty-library state |
| Open detail item id is present in refreshed item summaries | Keep the selected detail page state |
| Open detail item id is absent from refreshed item summaries | Clear selected detail and return to the library list |
| Saved config changes while a detail page is open | Return to the audiobook home page and clear the selected detail before loading the new config |
| Previous-config library/detail response completes after saved config changed | Ignore the stale response and keep the new config's state |
| Library/item/playback response is empty, including Retrofit/Gson `EOFException` before a `Response` is returned | Throw `AudiobookShelfApiException.Kind.API` |
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
| Periodic sync starts from a resumed session while playback state is still `0` | Use the session resume/current time as the first `timeListened` delta baseline and report at least that baseline as `currentTime` |
| Progress/session close current time is negative or beyond duration | Clamp payload `currentTime` to `0..durationSeconds` |
| Progress/session close duration is zero or negative | Send `currentTime: 0.0`; keep request `duration` safe for progress math |
| Progress update/session sync/session close returns non-2xx | Throw `AudiobookShelfApiException.Kind.HTTP` with the failing status code |
| User leaves audiobook playback | Call `syncAndCloseSession(...)` with the last absolute position and clear Media3 audiobook state |
| Background close fails during music/video handoff | Keep the new media surface active; do not reopen the stopped audiobook player |
| User starts audiobook playback while music is active | Call `MusicPlaybackEngine.stop()` before `AudiobookPlaybackEngine.play(...)` |
| User starts the same audiobook that is already active | Reuse the current session and show the player; do not call `startPlayback()` again |
| User starts a different audiobook while one is active | Background sync/close the old session, then start the requested item |
| Audio URL path contains `token=` but query has no `token` parameter | Append the bearer token as a query parameter |
| Audio URL already contains a `token` query parameter | Do not append a duplicate token |

### 5. Good/Base/Bad Cases

- Good: User opens an audiobook, `startPlayback()` returns a session, Media3 plays session tracks, progress sync runs periodically, and `syncAndCloseSession()` is called when leaving the player.
- Good: AudiobookShelf-compatible servers return `Book` or `BOOK` media type casing, and the app still shows those audiobook libraries.
- Good: AudiobookShelf-compatible servers omit `libraries` or return it as null, and the app treats discovery as an empty library list.
- Good: AudiobookShelf-compatible servers include partial library rows, and the repository skips unusable rows while keeping valid book libraries.
- Good: User taps the same audiobook while it is already active; the app reopens the current player without creating a duplicate AudiobookShelf session.
- Good: User starts a different audiobook while one is active; the app syncs/closes the previous session in the background and starts the new `/play` session.
- Good: User switches AudiobookShelf server/account, refreshes libraries, and the app requests items from a library id returned by that server instead of a stale id from the previous server/account.
- Good: User opens a large AudiobookShelf library and the app displays books from every paginated item response, not only page 0.
- Good: An AudiobookShelf-compatible server omits `results` on an item page, and the repository treats that response as an empty page.
- Good: An AudiobookShelf-compatible server includes partial minified item rows, and the repository skips unusable rows while keeping valid books from the same page.
- Good: A valid minified item row omits its own `libraryId`, and the returned summary uses the requested library id from `/api/libraries/{id}/items`.
- Good: An AudiobookShelf-compatible server omits expanded detail metadata arrays or chapters, and the detail mapper returns empty lists instead of crashing.
- Good: Expanded detail authors, narrators, series sequence labels, and chapters still map when the arrays are present.
- Good: An AudiobookShelf-compatible server omits playback-session chapters or audio tracks, and `startPlayback()` returns an empty domain list instead of crashing before playback error handling.
- Good: User switches AudiobookShelf server/account from a detail page and the app returns to Home before loading the new account, instead of retaining the old detail object.
- Good: User refreshes while viewing a detail page for a book that still exists in the selected library; the detail page remains open.
- Good: User refreshes while viewing a detail page for a book removed from the selected library; the app returns to the library list instead of showing stale detail.
- Good: User can skip back/forward by the fixed audiobook interval; progress sync keeps using the resulting absolute position.
- Good: AudiobookShelf returns `/audio/token=placeholder/book.mp3?download=0`; Nordic still appends `&token=<token>` because the path text is not an auth query parameter.
- Good: User can cycle playback speed from the player, and Media3 playback speed plus UI state stay in sync.
- Good: User can jump to previous/next chapters from the player; absolute position updates continue to drive progress sync.
- Base: User only browses libraries and details; no playback session is created and no progress endpoint is called.
- Bad: `getLibraryItems()` requests only `page=0`, truncating any library with more books than the page size.
- Bad: `AudiobookShelfLibraryItemsResponse.results` is modeled as a non-null Kotlin list and pagination calls `.map` or `.isEmpty()` on it directly.
- Bad: Minified item row fields such as `id`, `media`, `metadata`, or `title` are modeled as non-null and mapped directly, allowing compatible partial rows to crash library browsing.
- Bad: Pagination compares `total` to mapped summary count after filtering, causing extra page requests when unusable rows were already included in the fetched total.
- Bad: Detail DTO list properties are non-null Kotlin lists and repository mapping calls `.map` directly, allowing Gson-omitted fields to become runtime nulls.
- Bad: Playback-session DTO list properties are non-null Kotlin lists and `startPlayback()` maps them directly, crashing before the no-playable-tracks state can be shown.
- Bad: `getLibraries()` compares `mediaType == "book"` case-sensitively and drops valid audiobook libraries returned as `Book`.
- Bad: `AudiobookShelfLibrariesResponse.libraries` is modeled as a non-null Kotlin list and repository mapping calls `.mapNotNull` directly.
- Bad: `AudiobookShelfLibraryDto.id`, `name`, or `mediaType` is modeled as a non-null Kotlin string and mapped directly into `AudiobookLibrarySummary`.
- Bad: Audio URL token detection uses `absolute.contains("token=")` and fails to append auth when that text appears in the path.
- Bad: App extracts a stream URL and plays it without calling `/play`, `/sync`, or `/close`; AudiobookShelf resume state will drift.

### 6. Tests Required

- Compile/build checks must cover all changed Android code:
  - `:app:compileDebugKotlin`
  - `:app:lintDebug`
  - `:app:testDebugUnitTest`
  - `:app:assembleDebug` for final packaging verification when playback wiring changes.
- Repository tests should assert:
  - login token fallback from non-blank `user.token` to non-blank `user.accessToken`
  - missing/null login `user` responses throw `AudiobookShelfApiException.Kind.AUTH`
  - missing/null/blank login token fields throw `AudiobookShelfApiException.Kind.AUTH`
  - missing and null library `libraries` arrays map to an empty library list
  - library filtering includes `book`, `Book`, and `BOOK` media type values and excludes non-book media types
  - library rows with missing, null, empty, or blank `id`, `name`, or `mediaType` are skipped without dropping valid rows
  - missing and null library item `results` arrays map to an empty item list on the first page
  - missing or null library item `results` arrays on a later page stop pagination while preserving accumulated summaries
  - minified library item rows with missing, null, empty, or blank item `id` are skipped without dropping valid rows
  - minified library item rows with missing/null `media`, missing/null `metadata`, or missing/null/blank metadata `title` are skipped without dropping valid rows
  - valid minified library item rows with missing/null/blank row `libraryId` map to the requested endpoint library id
  - paginated library item browsing stops when fetched row count reaches server `total`, even when mapped summaries are fewer because rows were skipped
  - valid minified item summary mapping preserves title, author, narrator, series, cover URL, duration, chapter count, and update timestamp
  - blank summary/detail/session `coverPath` values map to `null`
  - non-blank relative and absolute cover paths map to expected app-facing cover URLs
  - missing and null expanded detail `metadata.authors`, `metadata.narrators`, `metadata.series`, and `chapters` map to empty domain lists
  - present expanded detail authors, narrators, series sequence labels, and chapters map to expected domain values
  - missing and null playback-session `chapters` and `audioTracks` map to empty domain lists
  - present playback-session chapters and audio tracks map to expected domain values and authenticated audio URLs
  - paginated library item browsing requests `page=0`, `page=1`, and merges results until `total` is loaded
  - relative cover/audio URL normalization
  - audio URL token handling appends a token when no `token` query parameter exists, including when `token=` appears only in the path, and avoids duplicating an existing token query parameter
  - progress fraction and current time payload fields
  - progress/session/close `currentTime` payload clamps to `0..durationSeconds`
  - zero-duration progress/session/close payloads keep `currentTime` at `0.0` while using a safe request duration
  - HTTP and empty-body errors map to typed `AudiobookShelfApiException` kinds
  - non-2xx progress/session responses throw `AudiobookShelfApiException.Kind.HTTP`
- UI/helper tests should assert library selection resolution keeps an existing id, falls back from a stale id, and clears selection for an empty library list.
- UI/helper tests should assert selected audiobook detail resolution keeps an item still present in refreshed summaries and clears an absent item.
- UI/helper tests should assert detail page state returns home when an open detail is cleared and remains detail when the selection remains.
- UI/helper tests should assert saved config changes resolve every `AudiobookLibraryPage` to `Home`.
- UI/helper tests should assert current-chapter display resolution uses timestamp order for unsorted chapters and returns no chapter before the first chapter start.
- UI/helper tests should assert audiobook detail chapter sorting orders unsorted chapters by `startSeconds`, preserves equal-start stability, and keeps empty lists empty.
- App-shell helper tests should assert periodic sync baseline resolution uses the session resume/current time when playback state has not caught up, uses playback state when it is ahead, clamps negative values to zero, and resolves periodic sync positions monotonically from the last synced baseline.
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
  - sync delta helpers clamp backwards movement to zero and initialize from the furthest known absolute position

### 7. Wrong vs Correct

#### Wrong

```kotlin
data class AudiobookShelfLibrariesResponse(
    val libraries: List<AudiobookShelfLibraryDto> = emptyList()
)

return body.libraries.mapNotNull { dto ->
    if (!dto.mediaType.equals("book", ignoreCase = true)) return@mapNotNull null
    AudiobookLibrarySummary(id = dto.id, name = dto.name, mediaType = dto.mediaType)
}
```

Gson can set omitted Kotlin list fields to `null`; direct mapping can crash before the repository returns an empty library state.

#### Correct

```kotlin
data class AudiobookShelfLibrariesResponse(
    val libraries: List<AudiobookShelfLibraryDto>? = null
)

return body.libraries.orEmpty().mapNotNull { dto ->
    val mediaType = dto.mediaType?.trim()?.takeIf { it.equals("book", ignoreCase = true) }
        ?: return@mapNotNull null
    val id = dto.id?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val name = dto.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    AudiobookLibrarySummary(id = id, name = name, mediaType = mediaType)
}
```

#### Wrong

```kotlin
data class AudiobookShelfLibraryDto(
    val id: String,
    val name: String,
    val mediaType: String
)

AudiobookLibrarySummary(id = dto.id, name = dto.name, mediaType = dto.mediaType)
```

Gson can set missing or null string fields to runtime null; direct mapping can crash or surface unusable library rows.

#### Correct

```kotlin
data class AudiobookShelfLibraryDto(
    val id: String? = null,
    val name: String? = null,
    val mediaType: String? = null
)

val mediaType = dto.mediaType?.trim()?.takeIf { it.equals("book", ignoreCase = true) }
    ?: return@mapNotNull null
val id = dto.id?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
val name = dto.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

AudiobookLibrarySummary(id = id, name = name, mediaType = mediaType)
```

#### Wrong

```kotlin
data class AudiobookShelfLibraryItemsResponse(
    val results: List<AudiobookShelfLibraryItemMinifiedDto> = emptyList()
)

data class AudiobookShelfLibraryItemMinifiedDto(
    val id: String,
    val libraryId: String,
    val media: AudiobookShelfBookMinifiedDto
)

data class AudiobookShelfBookMinifiedDto(
    val metadata: AudiobookShelfBookMinifiedMetadataDto
)

data class AudiobookShelfBookMinifiedMetadataDto(
    val title: String
)

val pageItems = body.results
items += pageItems.map { it.toSummary() }
if (body.total != null && items.size >= body.total) break
```

#### Correct

```kotlin
data class AudiobookShelfLibraryItemsResponse(
    val results: List<AudiobookShelfLibraryItemMinifiedDto>? = null
)

data class AudiobookShelfLibraryItemMinifiedDto(
    val id: String? = null,
    val libraryId: String? = null,
    val media: AudiobookShelfBookMinifiedDto? = null
)

data class AudiobookShelfBookMinifiedDto(
    val metadata: AudiobookShelfBookMinifiedMetadataDto? = null
)

data class AudiobookShelfBookMinifiedMetadataDto(
    val title: String? = null
)

val pageItems = body.results.orEmpty()
fetchedItemCount += pageItems.size
items += pageItems.mapNotNull { it.toSummary(fallbackLibraryId = libraryId) }
if (body.total != null && fetchedItemCount >= body.total) break
```

#### Wrong

```kotlin
authors = media.metadata.authors.map { it.name }
chapters = media.chapters.map { chapter -> chapter.toDomain() }
audioTracks = session.audioTracks.mapNotNull { track -> track.toDomainOrNull() }
```

Gson can set omitted Kotlin list fields to `null`; direct mapping can crash before the repository returns a domain detail or playback session.

#### Correct

```kotlin
authors = media.metadata.authors.orEmpty().map { it.name }
chapters = media.chapters.orEmpty().map { chapter ->
    AudiobookChapter(
        id = chapter.id,
        title = chapter.title,
        startSeconds = chapter.start.toInt(),
        endSeconds = chapter.end.toInt()
    )
}
audioTracks = session.audioTracks.orEmpty().mapNotNull { track -> track.toDomainOrNull() }
```

#### Wrong

```kotlin
if (absolute.contains("token=")) absolute else "$absolute&token=$token"
```

This treats any path text as authentication state and can leave playback URLs unauthenticated.

#### Correct

```kotlin
val url = absolute.toHttpUrlOrNull()
if (url?.queryParameterNames?.any { it.equals("token", ignoreCase = true) } == true) {
    url.toString()
} else {
    url?.newBuilder()?.addQueryParameter("token", token)?.build()?.toString()
}
```

#### Wrong

```kotlin
if (dto.mediaType != "book") return@mapNotNull null
```

This can hide valid audiobook libraries from compatible servers that vary `mediaType` casing.

#### Correct

```kotlin
if (!dto.mediaType.orEmpty().trim().equals("book", ignoreCase = true)) return@mapNotNull null
```

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
