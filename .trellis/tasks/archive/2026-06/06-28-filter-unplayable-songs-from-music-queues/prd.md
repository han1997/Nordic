# Filter unplayable songs from music queues

## Goal

Keep music queue playback continuous by excluding songs without a usable `streamUrl` before handing a queue to Media3. Bulk playback should not later fail just because an unavailable track remains in the queue after the first playable song.

## What I Already Know

* The long-running goal asks to keep improving music continuous playback, AudiobookShelf behavior, and Yamby-style video behavior with feature work, performance optimization, and bug fixes.
* The previous music round made play-all entry points start at the first playable song, but `MusicPlaybackEngine.playQueue(...)` still maps every song to a Media3 `MediaItem`.
* `NavidromeSong.toMediaItem()` uses `streamUrl.orEmpty()`, so unplayable queue entries become empty-URI media items.
* The music playback quality spec requires queue mutations to be owned by `MusicPlaybackEngine` and tested through pure helpers when possible.

## Requirements

* Filter queue entries whose `streamUrl` is null or blank before setting Media3 media items.
* Preserve the selected start song when that original song is playable.
* If the requested start song is not playable, start at the next playable song after it when possible.
* If there is no playable song after the requested start, start at the nearest playable song before it.
* Do nothing and surface a playback error state when no songs in the requested queue are playable.
* Apply the same filtering to pending queues before the Media3 controller connects.
* Cover queue filtering and start-index mapping with focused unit tests.

## Acceptance Criteria

* [x] Mixed queues keep only playable songs before playback.
* [x] A playable requested start song remains the current queue item after filtering.
* [x] An unplayable requested start song maps to the next playable song after it.
* [x] When only earlier playable songs exist, an unplayable requested start maps to the nearest earlier playable song.
* [x] All-unplayable queues do not publish a playable queue and show an error.
* [x] Focused unit tests pass.

## Definition of Done

* Tests added or updated where behavior changes.
* Kotlin compile, unit tests, and lint pass.
* Specs or notes updated if a reusable contract is learned.
* Trellis finish workflow is run at the end of the round.

## Technical Approach

Add a pure queue resolver in `MusicPlaybackEngine.kt` that returns a filtered queue and mapped start index. Use it at the start of `playQueue(...)` for both connected and pending controller paths. Keep single-song `play(song)` behavior unchanged.

## Decision (ADR-lite)

**Context**: UI play-all can pick a playable start index, but playback continuity belongs in the engine because many callers can pass queues and Media3 should not receive empty-URI entries.

**Decision**: Filter unplayable songs inside `MusicPlaybackEngine.playQueue(...)` and expose pure resolver tests.

**Consequences**: Queue UI will show the actual playable queue rather than unavailable tracks. This is preferable to a visible queue that later fails during continuous playback.

## Out of Scope

* Removing unplayable songs from visual library lists.
* Changing single-song row click behavior.
* Retrying or repairing missing Navidrome stream URLs.
* New Navidrome API calls.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/playback/MusicPlaybackEngineTest.kt`.
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md`.
