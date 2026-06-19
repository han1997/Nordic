# Continue Musiver Music Improvements

## Goal

Continue improving Nordic's Navidrome music experience using Musiver as a product reference, after the previous task completed album browsing and sorting.

## What I Already Know

* The user wants to continue referencing Musiver (`https://github.com/liuyincs/musiver`) to add music-section functionality.
* The previous Musiver-inspired task completed album browsing and sorting in Nordic.
* Musiver's public repository does not contain application source code and has no declared license metadata, so it must be treated as product/release-note reference only.
* Nordic is a Kotlin/Jetpack Compose Android app with Navidrome music, playback, lyrics display, albums, artists, album detail, artist detail, search, and queue UI.
* Existing lyrics behavior loads by song id, then falls back to artist/title. The player displays synced/plain lyrics but has no search, edit, or local override flow.
* Current `NavidromeSong` has a single artist string and no artist ids on tracks, which makes multi-artist navigation higher risk.

## Assumptions

* The next feature should improve the existing Navidrome music experience, not add a new provider.
* We should keep changes scoped and verifiable with local Android build/tests.
* We should not copy Musiver code, assets, or UI text.

## Open Questions

* None.

## Requirements

* Use Musiver only as product behavior inspiration.
* Preserve the existing album browsing and sorting behavior.
* Keep config-scoped music behavior intact and avoid credential leakage.
* Implement one focused next music feature rather than several unrelated improvements in one task.
* Selected MVP: playback queue experience polish.
* Upgrade the music queue sheet from a mostly read-only jump list into a manageable queue surface.
* Keep the existing queue jump-to-item behavior.
* Add "play next" behavior for a queued track that moves it behind the current track.
* Add individual queue item removal.
* Add a way to clear upcoming tracks while preserving the current track.
* Keep playback state and queue metadata synchronized after Media3 queue mutations.
* Show a useful queue empty/near-empty state rather than leaving the sheet ambiguous.

## Acceptance Criteria

* [ ] PRD identifies the selected next MVP feature.
* [ ] Requirements distinguish MVP from future enhancements.
* [ ] Technical approach lists affected files and verification commands.
* [ ] Existing album browsing/sorting continues to compile and behave as before.
* [ ] New behavior has focused tests or compile-time verification appropriate to the change.
* [ ] Queue sheet still opens from the player and highlights the current item.
* [ ] Tapping a queue item seeks to that item.
* [ ] A non-current queued item can be moved to play next.
* [ ] Queue items can be removed without leaving stale queue metadata in UI state.
* [ ] Upcoming tracks can be cleared while the current song remains active.

## Definition of Done

* Tests added/updated where behavior changes.
* Kotlin compile, unit tests, lint, and debug assemble pass as appropriate.
* Docs/spec notes updated if a reusable behavior contract changes.
* No unrelated user changes are reverted.

## Out of Scope

* Replacing Nordic with Musiver.
* Copying Musiver code, assets, binaries, or UI text.
* Adding another music provider.
* Changing the already completed album sorting feature unless required for integration.
* Implementing multiple Musiver feature areas in one task.
* Lyrics search/manual override.
* Multi-artist navigation.
* Server health/route status.
* Drag-and-drop queue reorder.
* Persisting queue state across app restarts.

## Research References

* [`research/musiver-next-feature-reference.md`](research/musiver-next-feature-reference.md) - remaining Musiver-inspired music candidates after album sorting.

## Candidate MVPs

**Approach A: Lyrics Search & Manual Override** (Recommended)

* Add a current-song lyrics management flow: search by artist/title, paste/edit lyrics, persist local override, reset to server lyrics.
* Pros: high user-visible value and builds directly on existing lyrics display.
* Cons: needs local persistence and careful loading/error states.

**Approach B: Multi-Artist Display & Navigation**

* Expand song metadata and let users navigate from track/player artist labels to artist details.
* Pros: improves metadata browsing and aligns with Musiver release notes.
* Cons: current model lacks artist ids on songs; provider metadata support may vary.

**Approach C: Server Health / Endpoint Status**

* Add a Navidrome endpoint check and clear status/error surface near config or music entry.
* Pros: improves setup diagnostics.
* Cons: crosses config and music UI; less directly player/library focused.

**Approach D: Queue Experience Polish** (Selected)

* Improve queue metadata consistency and current-item behavior.
* Pros: improves playback reliability.
* Cons: needs a sharper target behavior or observed issue before implementation.

## Decision (ADR-lite)

**Context**: Musiver calls out queue metadata synchronization improvements. Nordic already exposes a queue sheet and maps Media3 media items back to `NavidromeSong`, but the sheet is primarily a jump list and does not expose queue management actions.

**Decision**: Implement playback queue experience polish as a manageable queue sheet: seek to item, move a future item to play next, remove queue items, clear upcoming items, and publish fresh queue metadata after each mutation.

**Consequences**: The implementation stays inside existing playback and Compose UI surfaces. Full drag-and-drop reorder and persistent queue restoration remain future work.

## Technical Approach

* Extend `MusicPlaybackEngine` with queue mutation methods backed by Media3 queue APIs.
* Keep `MusicPlaybackState.queue` and `queueIndex` published after each mutation.
* Extend `MusicQueueSheet` with compact per-row actions and an upcoming-clear action.
* Wire the new queue callbacks through `MainActivity`.
* Verify with Kotlin compile/unit checks and existing repository tests.

## Affected Files

* `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`
* `app/src/main/java/com/nordic/mediahub/ui/MusicQueueSheet.kt`
* `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
* `app/src/test/java/com/nordic/mediahub/playback/MusicPlaybackEngineTest.kt`

## Verification Commands

* `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
* `.\gradlew.bat :app:lintDebug --no-daemon`
* `.\gradlew.bat :app:assembleDebug --no-daemon`
