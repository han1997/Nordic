# Clear stale video selection after refresh

## Goal

Keep the video detail state aligned with the latest Emby catalog refresh. When a refresh returns a catalog that no longer contains the currently selected video, the UI should clear that detail selection instead of continuing to show a stale item.

## What I Already Know

* The long-running goal asks to keep improving music, AudiobookShelf, and Yamby-style video behavior with feature work, performance optimization, and bug fixes.
* The previous round is complete, archived, journaled, and the worktree was clean before this task started.
* `EmbyRepository.getCatalog(selectedLibraryId)` already validates the selected library id against the latest libraries and falls back to an available library.
* `VideoScreen.refreshVideo` currently clears `selectedVideo` only when its `libraryId` differs from `catalog.selectedLibraryId`, so a removed or unavailable item from the same library can remain selected after refresh.

## Requirements

* Preserve the selected video after refresh only when it belongs to the selected library and its id is still present in the refreshed item list.
* Clear the selected video when the selected library changes.
* Clear the selected video when the same library refresh no longer contains that video id.
* Cover the selection resolver with focused unit tests.

## Acceptance Criteria

* [x] Existing selected video is preserved when refreshed catalog still contains that id in the same library.
* [x] Selected video is cleared when refreshed selected library differs.
* [x] Selected video is cleared when refreshed items omit the selected video id.
* [x] `VideoScreen.refreshVideo` uses the resolver after applying refreshed catalog data.
* [x] Focused unit tests pass.

## Definition of Done

* Tests added or updated where behavior changes.
* Kotlin compile and relevant unit tests pass.
* Specs or notes updated if a reusable contract is learned.
* Trellis finish workflow is run at the end of the round.

## Technical Approach

Extract a small pure helper near `continueWatchingShelf` or other `VideoScreen` helpers. Use it from `refreshVideo` to resolve `selectedVideo` from the latest `VideoCatalog` state. Keep Emby repository/API contracts unchanged.

## Out of Scope

* New Emby endpoints.
* Video player playback behavior.
* Video UI redesign.
* Persisting selected video detail across app launches.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`.
* Relevant tests: update `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`.
* Relevant spec: `.trellis/spec/backend/emby-integration.md`.
