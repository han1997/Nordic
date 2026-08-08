# optimize server sync performance

## Goal

Reduce the time it takes to sync Navidrome music data from the server so the music screen becomes usable faster on slower networks and larger libraries.

## What I already know

* The main refresh path is `loadNavidromeMusicRefresh()`, which currently loads recent albums first, then recently added songs, all songs, and artists.
* `NavidromeRepository.getSongsFromAlbums()` fetches album details one album at a time, which creates a sequential network bottleneck.
* The music screen already has cache-then-refresh behavior for some detail views, and `NavidromeMusicCacheRepository` persists browse and detail caches in DataStore.
* The repo has backend specs for Navidrome integration, error handling, quality, and cache hygiene.

## Assumptions (temporary)

* The user is referring to the initial Navidrome library sync, not playback progress sync or star/playlist actions.
* The biggest win will come from reducing sequential server round trips, not from UI-only changes.

## Open Questions

* None.

## Requirements

* Make the initial Navidrome music sync faster without changing the visible data set or breaking existing cache behavior.
* Also speed up adjacent Navidrome fetches for album detail, artist detail, and playlist detail loads.
* Prefer reusing existing cache data where possible.
* Keep error handling and user-facing messages consistent with the existing repository patterns.

## Acceptance Criteria

* [ ] The initial music refresh completes with fewer sequential network waits than before.
* [ ] The music screen still shows the same albums, songs, recently added songs, and artists as before.
* [ ] Album detail, artist detail, and playlist detail loads are faster on the network path they already use.
* [ ] Existing cache behavior for detail views remains intact.
* [ ] Repository tests cover the optimized sync path.

## Definition of Done

* Tests added/updated where the sync path changed.
* Kotlin compile and unit tests pass.
* Spec notes updated if a new performance rule or cache contract is discovered.

## Out of Scope

* Playback progress sync.
* UI redesign unrelated to sync speed.
* Navidrome auth or API contract changes unless they are required for the performance fix.

## Technical Notes

* Main sync entry: `app/src/main/java/com/nordic/mediahub/data/NavidromeMusicRefresh.kt`
* Main repository: `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
* Cache store: `app/src/main/java/com/nordic/mediahub/data/NavidromeMusicCacheRepository.kt`
* Music screen refresh trigger: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`
* Relevant spec: `.trellis/spec/backend/navidrome-integration.md`
* Relevant spec: `.trellis/spec/backend/error-handling.md`
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md`
