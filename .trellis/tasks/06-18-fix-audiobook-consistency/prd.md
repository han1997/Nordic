# Fix audiobook consistency

## Goal

Make the current AudiobookShelf state consistent across docs and UI so the app no longer implies full audiobook support when only configuration storage and placeholder UI exist.

## Requirements

* README must stop describing AudiobookShelf as already supported end-to-end.
* The audiobook screen must clearly communicate that server configuration exists but library playback/integration is not implemented yet.
* Existing configuration save behavior must remain unchanged.

## Acceptance Criteria

* [ ] README wording matches the current implementation state.
* [ ] The audiobook screen no longer shows fake library items as if real data were loaded.
* [ ] The audiobook screen shows a clear waiting/coming-soon state for real AudiobookShelf integration.

## Definition of Done

* Relevant UI/doc consistency issue is fixed.
* Existing buildable code remains syntactically valid.

## Out of Scope

* Real AudiobookShelf API integration
* Authentication flow changes
* Playback implementation

## Technical Notes

* Current implementation stores `AudiobookShelfConfig` in `ConfigRepository`.
* `AudiobookScreen.kt` currently renders placeholder book cards.
* `README.md` currently states AudiobookShelf is supported as a delivered feature.
