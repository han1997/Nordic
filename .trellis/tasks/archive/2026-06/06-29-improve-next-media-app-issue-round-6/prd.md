# brainstorm: improve next media app issue

## Goal

Improve the Android media app in a narrow, verifiable round by making AudiobookShelf library discovery report malformed empty responses as typed API errors instead of valid empty-library results.

## What I Already Know

* The ongoing product direction is to keep music inspired by Yinliu, audiobooks aligned with official AudiobookShelf behavior, and video aligned with Yamby.
* The AudiobookShelf integration spec says library/item/playback empty response bodies must throw `AudiobookShelfApiException.Kind.API`.
* `AudiobookShelfRepository.getLibraryItems(...)`, `getLibraryItem(...)`, and `startPlayback(...)` now validate empty bodies, but `getLibraries()` still uses `response.body()?.libraries.orEmpty()`.
* Treating a malformed 200-with-empty-body response as "no libraries" hides a server/proxy/API problem behind an empty state.

## Assumptions

* This is a repository-boundary correctness fix; no UI changes are needed.
* Existing `MockWebServer` tests are sufficient to verify typed error behavior.

## Open Questions

* None blocking. The existing spec defines the desired behavior.

## Requirements

* `AudiobookShelfRepository.getLibraries()` must throw `AudiobookShelfApiException.Kind.API` when the response body is null.
* Preserve existing behavior for HTTP failures and successful non-empty responses.
* Add a focused repository unit test for the empty-body library response.

## Acceptance Criteria

* [x] Empty `GET /api/libraries` body produces `AudiobookShelfApiException.Kind.API`.
* [x] Existing AudiobookShelf repository tests still pass.
* [x] Standard Gradle gates pass sequentially.

## Definition of Done

* Focused unit test added.
* Kotlin compilation, unit tests, and lint pass.
* Trellis finish flow records the work, archives the task, and journals the session.

## Out of Scope

* UI empty-state copy changes.
* Pagination or mapping changes beyond `getLibraries()`.
* New AudiobookShelf endpoints.

## Technical Notes

* Relevant files:
  * `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`
  * `.trellis/spec/backend/audiobookshelf-integration.md`
