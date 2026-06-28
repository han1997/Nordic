# brainstorm: improve next media app issue

## Goal

Improve AudiobookShelf reliability by making every body-bearing AudiobookShelf repository endpoint convert empty Retrofit/Gson responses into typed `AudiobookShelfApiException.Kind.API` errors.

## What I Already Know

* The ongoing product direction is to keep music inspired by Yinliu, audiobooks aligned with official AudiobookShelf behavior, and video aligned with Yamby.
* The current repo is clean and has no active task at the start of this round.
* The AudiobookShelf integration contract says library/item/playback empty responses, including Retrofit/Gson `EOFException` before a `Response` is returned, must throw `AudiobookShelfApiException.Kind.API`.
* `AudiobookShelfRepository.getLibraries()` currently handles this EOF case.
* `getLibraryItems(...)`, `getLibraryItem(...)`, and `startPlayback(...)` still call Retrofit directly and can leak raw `EOFException` for an empty 200 response.

## Assumptions

* This is a repository-boundary correctness fix; no UI changes are needed.
* A small shared repository helper is justified because multiple endpoints share the same response/body validation contract.

## Open Questions

* None blocking. The existing spec defines the desired behavior.

## Requirements

* Convert empty-body `EOFException` from `getLibraryItems(...)`, `getLibraryItem(...)`, and `startPlayback(...)` into `AudiobookShelfApiException.Kind.API`.
* Preserve existing HTTP status error behavior and successful response mapping.
* Keep progress/session `Response<Unit>` behavior unchanged.
* Add focused repository tests proving typed API errors for at least item-detail and playback empty responses, plus the paginated item-list EOF path.

## Acceptance Criteria

* [x] Empty AudiobookShelf item-list body produces `AudiobookShelfApiException.Kind.API`.
* [x] Empty AudiobookShelf item-detail body produces `AudiobookShelfApiException.Kind.API`.
* [x] Empty AudiobookShelf playback body produces `AudiobookShelfApiException.Kind.API`.
* [x] Existing AudiobookShelf repository tests still pass.
* [x] Standard Gradle gates pass sequentially.

## Definition of Done

* Focused unit tests added.
* Kotlin compilation, unit tests, and lint pass.
* Trellis finish flow records the work, archives the task, and journals the session.

## Out of Scope

* UI empty-state copy changes.
* Progress/session `Unit` endpoint behavior.
* New AudiobookShelf endpoints.

## Technical Notes

* Relevant files:
  * `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`
  * `.trellis/spec/backend/audiobookshelf-integration.md`
