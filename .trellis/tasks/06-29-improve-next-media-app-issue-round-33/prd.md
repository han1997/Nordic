# Improve Next Media App Issue Round 33

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens AudiobookShelf library discovery so compatible or partial `/api/libraries` responses with missing, null, or blank per-library `id`, `name`, or `mediaType` fields do not crash mapping or surface unusable libraries. The repository should keep returning only usable book libraries.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across Navidrome and AudiobookShelf payloads.
* `AudiobookShelfLibrariesResponse.libraries` is already nullable and normalized with `orEmpty()`.
* `AudiobookShelfLibraryDto.id`, `name`, and `mediaType` are still non-null Kotlin strings.
* Gson can deserialize omitted or explicit-null string fields as runtime nulls despite non-null Kotlin declarations.
* `AudiobookShelfRepository.getLibraries()` calls `dto.mediaType.equals("book", ignoreCase = true)` and then maps `dto.id`, `dto.name`, and `dto.mediaType` directly into `AudiobookLibrarySummary`.
* Existing tests cover missing/null library arrays and media-type case-insensitivity, but not unusable library rows.
* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/error-handling.md`, and `.trellis/spec/backend/audiobookshelf-integration.md`.

## Assumptions

* Missing, null, empty, or whitespace-only library `id` means the row is unusable and should be skipped.
* Missing, null, empty, or whitespace-only library `name` means the row is unusable and should be skipped.
* Missing, null, empty, or whitespace-only `mediaType` should not match book and should be skipped.
* Existing case-insensitive inclusion of `book`, `Book`, and `BOOK` should remain unchanged.
* Audiobook playback/session behavior outside library discovery should remain unchanged.

## Requirements

* Model AudiobookShelf library DTO `id`, `name`, and `mediaType` as nullable wire fields.
* In `getLibraries()`, filter to usable book libraries only:
  * `mediaType` equals `book` ignoring case.
  * `id` is non-blank.
  * `name` is non-blank.
* Preserve original non-blank media-type casing on returned `AudiobookLibrarySummary`.
* Preserve existing token/auth behavior and missing/null library-array behavior.
* Preserve media-type case-insensitive inclusion for valid book libraries.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Library rows with missing/null/blank `id`, `name`, or `mediaType` are skipped without crashing.
* [x] Valid `book`, `Book`, and `BOOK` library rows still map to summaries.
* [x] Missing/null top-level `libraries` arrays still map to an empty list.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover unusable library row filtering and existing book media-type matching.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Audiobook playback session changes.
* Library item pagination changes.
* UI redesigns for empty-library states.
* New AudiobookShelf endpoints.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`, `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: make `AudiobookShelfLibraryDto.id`, `name`, and `mediaType` nullable and normalize/filter them in `getLibraries()` with `takeIf { it.isNotBlank() }`.
