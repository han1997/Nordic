# Fix audiobook consistency

## Goal

Make the current AudiobookShelf state consistent across docs and UI.

## Resolution

This task was created when AudiobookShelf was still assumed to be configuration-only placeholder UI. Later implementation work added real AudiobookShelf auth, library browsing, detail loading, playback sessions, and progress sync. The correct final state is therefore the opposite of the original coming-soon plan: docs and UI should describe the implemented integration rather than hiding it.

## Requirements

* README must match the actual AudiobookShelf implementation state.
* The audiobook screen must not show fake library items as if real data were loaded.
* Existing configuration save behavior must remain unchanged.

## Acceptance Criteria

* [x] README wording matches the current implementation state.
* [x] The audiobook screen no longer shows fake library items as if real data were loaded.
* [x] The audiobook screen shows real configuration, loading, empty, detail, and playback states for AudiobookShelf integration.

## Definition of Done

* Relevant UI/doc consistency issue is fixed.
* Existing buildable code remains syntactically valid.

## Out of Scope

* Further AudiobookShelf feature expansion beyond the implemented auth/library/detail/playback/progress flow.

## Technical Notes

* Current implementation stores `AudiobookShelfConfig` in `ConfigRepository`.
* `AudiobookScreen.kt` currently renders placeholder book cards.
* `README.md` currently states AudiobookShelf is supported as a delivered feature.
