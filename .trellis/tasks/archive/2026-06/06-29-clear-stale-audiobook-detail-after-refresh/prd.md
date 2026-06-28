# Clear Stale Audiobook Detail After Refresh

## Goal

Keep the AudiobookShelf browsing flow consistent after library refreshes. If the currently open audiobook detail no longer exists in the refreshed selected library, the UI should clear the stale detail instead of leaving the user on an outdated book page.

## What I already know

* `AudiobookScreen.refreshAudiobooks(...)` reloads libraries and the selected library item list.
* `selectedItem` is an `AudiobookItemDetail?` used for the detail page.
* The current refresh path updates `items` but does not reconcile `selectedItem` against the refreshed list.
* Video already has a similar selection-refresh pattern through `resolveVideoSelectionAfterCatalogRefresh(...)`.

## Assumptions

* This task should only clear stale detail state; it should not add a new detail re-fetch path.
* If the selected audiobook still appears in the refreshed item summaries, keeping the existing loaded detail is acceptable.
* If the selected audiobook no longer appears, returning to the library list is less confusing than showing stale details.

## Requirements

* Add a small helper that keeps the selected audiobook detail only when its id still exists in the refreshed item summaries.
* Use the helper after AudiobookShelf library refreshes.
* When the detail page is open and the selected detail is no longer present after refresh, return to the home/library page.
* Add focused unit tests for keeping and clearing selected audiobook detail.

## Acceptance Criteria

* [x] A selected audiobook detail is kept when its id remains in the refreshed item list.
* [x] A selected audiobook detail is cleared when its id is absent from the refreshed item list.
* [x] Refresh behavior returns the screen to the library list when an open detail is cleared.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Tests added or updated.
* Required Gradle checks pass sequentially on Windows.
* Specs updated if the AudiobookShelf behavior contract changes.
* Work is committed before Trellis archive and journal commits.

## Out of Scope

* Re-fetching detail metadata for still-present selected audiobooks.
* Offline caching or persistent audiobook navigation state.
* Changes to AudiobookShelf repository APIs.

## Technical Approach

Add an internal helper in `AudiobookScreen.kt` near the existing audiobook selection helpers. `refreshAudiobooks(...)` will load the refreshed item list into a local value, assign it to `items`, resolve `selectedItem`, and reset `libraryPage` to `Home` when the detail page was open but the selected detail disappeared.

## Decision (ADR-lite)

**Context**: AudiobookShelf libraries can change between refreshes. Keeping a stale detail page after a book is removed or moved creates a mismatch between the visible detail and the current library list.

**Decision**: Reconcile the selected detail against the refreshed list locally and clear it when the summary id is absent.

**Consequences**: The behavior is deterministic and cheap. Existing detail metadata is not refreshed when the item still exists, which keeps this round narrowly scoped.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`, `app/src/test/java/com/nordic/mediahub/ui/AudiobookScreenTest.kt`.
* Relevant specs to load before coding: backend directory structure, quality guidelines, and AudiobookShelf integration contract.
