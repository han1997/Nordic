# Current Nordic app architecture relevant to AudiobookShelf

## Code references

* `app/src/main/java/com/nordic/mediahub/data/ServerConfig.kt`
* `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
* `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
* `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
* `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`
* `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackService.kt`
* `app/src/main/java/com/nordic/mediahub/playback/MusicMediaItems.kt`
* `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`

## What already exists

* `AudiobookShelfConfig` currently stores:
  * `serverUrl`
  * `username`
  * `password`
* `ConfigRepository` already persists AudiobookShelf config in DataStore.
* `AudiobookScreen` currently has config UI but no real repository, library fetch, detail view, or playback flow.
* Music integration already establishes a working end-to-end pattern:
  * `NavidromeRepository` for remote API calls and DTO normalization
  * screen-level state loading in Compose
  * `MusicPlaybackEngine` backed by `MediaController`
  * `MusicPlaybackService` backed by `ExoPlayer` and `MediaSession`
  * `MusicMediaItems.kt` adapter between app model and Media3 `MediaItem`

## Constraints discovered

* Current playback types are music-specific:
  * queue state is `List<NavidromeSong>`
  * current media model is `NavidromeSong`
  * media extras keys are music-oriented but generic enough to be adapted
* `MusicPlaybackService` itself is less music-specific than `MusicPlaybackEngine`:
  * it mostly manages ExoPlayer, caching, MediaSession, and audio attributes
  * this service layer looks reusable for audiobook playback
* `MusicPlayerScreen` assumes song title/artist/album/lyrics/repeat/queue semantics.
  * Audiobook playback will likely need chapter/resume/progress semantics instead.

## Architectural options

### Option A: Reuse `MusicPlaybackEngine` directly

Fastest initial implementation, but it leaks `NavidromeSong` and music-specific UI/state into audiobook code.

### Option B: Extract shared playback core, keep separate media-specific engines

Recommended if audiobook playback is in MVP. Reuse Media3/session/cache infrastructure, but separate domain models and UI state for music vs audiobook.

### Option C: Build a separate audiobook playback engine now

Lowest risk to current music code, but duplicates more playback code and may create drift later.

## Working recommendation

If playback is in scope, prefer Option B conceptually. For a fast first implementation, it may still be acceptable to land a thin `AudiobookPlaybackEngine` that reuses the existing service/session patterns before doing a broader abstraction pass.
