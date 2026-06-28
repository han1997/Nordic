# brainstorm: improve next media app issue

## Goal

Improve the Android media app in a narrow, verifiable round by loading all AudiobookShelf library items instead of only the first page.

## What I Already Know

* The ongoing product direction is to keep music inspired by Yinliu, audiobooks aligned with official AudiobookShelf behavior, and video aligned with Yamby.
* Recent rounds fixed stale state after saved config changes and corrected Navidrome structured lyric timestamp parsing.
* The repo is clean and has no active Trellis task at the start of this round.
* `AudiobookShelfApi.getLibraryItems(...)` exposes paginated `limit` and `page` query parameters, and `AudiobookShelfLibraryItemsResponse` exposes `total`.
* `AudiobookShelfRepository.getLibraryItems(...)` currently makes a single page request, so libraries larger than the API page size are truncated.

## Assumptions

* Keep the change in the repository/API test layer; the existing Compose list will render whatever complete list the repository returns.
* Use the existing `MockWebServer` repository test pattern to prove page requests and merged results.

## Open Questions

* None blocking. The broad continuous-improvement goal authorizes choosing the next narrow issue automatically.

## Requirements

* `AudiobookShelfRepository.getLibraryItems(libraryId)` must request additional pages until the server-reported `total` is loaded, or until the server returns an empty/short page.
* The repository must preserve existing auth headers, typed HTTP error behavior, and `AudiobookItemSummary` mapping.
* Add a focused repository unit test that returns two paginated responses and asserts the merged item list plus requested `page`/`limit` query values.

## Acceptance Criteria

* [x] `getLibraryItems(...)` returns items from multiple AudiobookShelf pages.
* [x] Test requests include `page=0`, `page=1`, and the repository page size.
* [x] Existing AudiobookShelf auth/progress tests still pass.
* [x] Standard Gradle gates pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Kotlin compilation, unit tests, and lint pass.
* Trellis finish flow records the work, archives the task, and journals the session.

## Out of Scope

* UI redesigns for audiobook browsing.
* Server-side search, sorting, or filters.
* Pagination changes for other services.

## Technical Notes

* Task created from the ongoing autonomous improvement goal.
* Relevant files:
  * `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`
  * `.trellis/spec/backend/audiobookshelf-integration.md`
