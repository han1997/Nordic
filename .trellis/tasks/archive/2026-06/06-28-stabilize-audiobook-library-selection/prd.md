# Stabilize audiobook library selection

## Goal

Make AudiobookShelf library refresh behavior resilient when the saved server, account, or available library set changes. The UI should not keep requesting a stale `selectedLibraryId` that is absent from the latest `/api/libraries` response.

## What I Already Know

* The active long-running goal asks to keep improving music, AudiobookShelf, and Yamby-style video behavior with feature work, performance optimization, and bug fixes.
* There is no active Trellis task and the worktree was clean at the start of this round.
* Recent completed rounds already covered video continue-watching completion filtering, audiobook track seek clamping, and pending music queue start stabilization.
* `AudiobookScreen.refreshAudiobooks` currently uses `selectedLibraryId ?: loadedLibraries.firstOrNull()?.id`, which can keep an old library id after config/account/library changes.

## Requirements

* Resolve the active audiobook library id from the latest library list on every refresh.
* Preserve the previous selection only when that id still exists in the latest list.
* Fall back to the first returned library when the previous selection is missing.
* Clear the selection and item list when the latest library list is empty.
* Cover the resolver behavior with focused unit tests.

## Acceptance Criteria

* [x] A refresh with an existing selected library keeps that selection.
* [x] A refresh with a stale selected library falls back to the first available library.
* [x] A refresh with no available libraries clears the selected library.
* [x] Audiobook screen refresh code uses the resolver before loading library items.
* [x] Focused unit tests pass.

## Definition of Done

* Tests added or updated where behavior changes.
* Kotlin compile and relevant unit tests pass.
* Specs or notes updated if a reusable contract is learned.
* Trellis finish workflow is run at the end of the round.

## Technical Approach

Extract the selection rule into a small pure helper near `AudiobookScreen`, use it in `refreshAudiobooks`, and test it from the UI unit test package. Keep repository/API contracts unchanged.

## Out of Scope

* New AudiobookShelf API endpoints.
* UI redesign.
* Music or video feature changes in this specific round.
* Persisting selected library across app launches.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`.
* Relevant tests: add focused coverage under `app/src/test/java/com/nordic/mediahub/ui/`.
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`.
