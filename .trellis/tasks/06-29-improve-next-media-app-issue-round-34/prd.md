# Improve Next Media App Issue Round 34

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens AudiobookShelf library item browsing so compatible or partial `/api/libraries/{id}/items` responses with unusable minified item rows do not crash list loading. The repository should skip rows missing the fields needed to show and open a book, while preserving valid rows and existing pagination behavior.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list and row-field boundaries across Navidrome and AudiobookShelf payloads.
* `AudiobookShelfLibraryItemsResponse.results` is already nullable and normalized with `orEmpty()`.
* `AudiobookShelfLibraryItemMinifiedDto.id`, `libraryId`, `mediaType`, and `media` are still modeled as non-null Kotlin fields.
* `AudiobookShelfBookMinifiedDto.metadata` and `AudiobookShelfBookMinifiedMetadataDto.title` are also non-null, then mapped directly by `toSummary()`.
* Gson can deserialize omitted or explicit-null object/string fields as runtime nulls despite non-null Kotlin declarations.
* `getLibraryItems()` currently adds `pageItems.map { it.toSummary() }` and uses the mapped item count for the `total` stop condition.
* If invalid rows are skipped, pagination should still compare the server `total` to the number of fetched rows, not only the number of mapped domain rows.
* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/audiobookshelf-integration.md`.

## Assumptions

* Missing, null, empty, or blank item `id` means the item row is unusable and should be skipped.
* Missing or null `media`, missing or null `metadata`, and missing/null/blank `metadata.title` mean the item row cannot be rendered/opened usefully and should be skipped.
* Missing/null/blank item `libraryId` can safely fall back to the requested library id because the endpoint is already scoped to `/api/libraries/{id}/items`.
* Existing cover URL, author, narrator, series, duration, chapter-count, and update-time mapping should remain unchanged for valid rows.
* Audiobook playback/session behavior outside library item summaries should remain unchanged.

## Requirements

* Model minified AudiobookShelf item row fields that can be absent as nullable DTO fields:
  * `AudiobookShelfLibraryItemMinifiedDto.id`
  * `AudiobookShelfLibraryItemMinifiedDto.libraryId`
  * `AudiobookShelfLibraryItemMinifiedDto.mediaType`
  * `AudiobookShelfLibraryItemMinifiedDto.media`
  * `AudiobookShelfBookMinifiedDto.id`
  * `AudiobookShelfBookMinifiedDto.metadata`
  * `AudiobookShelfBookMinifiedMetadataDto.title`
* Convert item rows to summaries with `mapNotNull`, skipping rows without a non-blank item id, media object, metadata object, or non-blank title.
* Use the requested library id as the domain `libraryId` fallback when a valid item row omits or blanks its own `libraryId`.
* Preserve valid item summary mapping for title, author, narrator, series, cover URL, duration, chapter count, and updated timestamp.
* Track fetched row count separately from mapped row count for pagination `total` stop logic.
* Preserve existing missing/null `results` behavior and typed error behavior.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Library item rows with missing/null/blank item id are skipped without crashing.
* [x] Library item rows with missing/null media, missing/null metadata, or missing/null/blank title are skipped without crashing.
* [x] Valid item rows with missing/null/blank row `libraryId` still map using the requested library id.
* [x] Pagination stop logic counts fetched rows, not only mapped summaries, so skipped rows do not force unnecessary page requests when `total` has already been fetched.
* [x] Valid item summary mapping remains unchanged.
* [x] Missing/null top-level `results` arrays still map to an empty list or stop pagination as before.
* [x] Focused repository tests cover unusable row filtering, requested-library fallback, and pagination total behavior with skipped rows.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Expanded audiobook detail mapping changes.
* Playback session mapping changes.
* UI redesigns for empty item lists.
* New AudiobookShelf endpoints.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`, `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: make the minified item DTO boundary nullable, change `toSummary(...)` to return `AudiobookItemSummary?`, map with `mapNotNull`, and use a separate fetched-row counter for `total` pagination.
