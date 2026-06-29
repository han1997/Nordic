# Improve Next Media App Issue Round 22

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Emby video list mapping so compatible servers that omit or null `Items` arrays in view or library-item responses return empty app lists instead of crashing repository mapping.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened Gson list boundaries for AudiobookShelf detail and playback session payloads.
* `EmbyItemsResponse.items` is currently a non-null Kotlin list with `emptyList()` default.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `EmbyRepository.getLibraries()` reads `.items.filter { ... }`.
* `EmbyRepository.getLibraryItems()` reads `response.items`, maps it, and uses its size for pagination.
* Existing Emby tests cover happy paths, HTTP/empty-body errors, pagination, auth, metadata mapping, and progress sync, but not missing/null `Items`.

## Assumptions

* Missing or null Emby view `Items` should behave like an empty library list.
* Missing or null Emby library-item `Items` should behave like an empty video list and stop pagination.
* Present `Items` mapping and pagination must remain unchanged.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null Emby `Users/{userId}/Views` response `Items` as an empty library list.
* Treat missing/null Emby `Users/{userId}/Items` response `Items` as an empty video item list.
* Preserve existing mapping for present libraries, video items, image URLs, stream URLs, and pagination.
* Preserve music and audiobook behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Emby view responses with missing/null `Items` map to an empty library list.
* [x] Emby library item responses with missing/null `Items` map to an empty item list.
* [x] Present `Items` mapping and pagination still work correctly.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* UI redesigns or video player changes.
* New server integrations.
* Emby paging strategy changes beyond empty/null page handling.
* Changes outside Emby list-response mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/emby-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`.
* Expected implementation: make `EmbyItemsResponse.items` nullable at the DTO boundary, then use `.orEmpty()` in library and item mapping.
