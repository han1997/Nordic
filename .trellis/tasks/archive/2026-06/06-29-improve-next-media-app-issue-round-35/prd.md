# Improve Next Media App Issue Round 35

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Emby video catalog browsing so compatible or partial `Users/{userId}/Views` and `Users/{userId}/Items` responses with unusable rows do not crash library or item loading. The repository should skip rows missing the fields needed to identify and display a library or video item, while preserving valid rows and existing pagination behavior.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable row-field boundaries across Navidrome and AudiobookShelf payloads.
* `EmbyItemsResponse.Items` is already nullable and normalized with `orEmpty()`.
* `EmbyItemDto.Id` and `EmbyItemDto.Name` are still modeled as non-null Kotlin fields.
* `EmbyRepository.getLibraries()` maps `item.id` and `item.name` directly into `VideoLibrary`.
* `EmbyRepository.getLibraryItems()` maps every page item directly with `item.toVideoItem(...)`, and `toVideoItem(...)` uses `id` and `name` for domain identity, thumbnails, and stream URLs.
* Gson can deserialize omitted or explicit-null object/string fields as runtime nulls despite non-null Kotlin declarations.
* Emby-compatible or plugin-backed servers can return partial rows; Nordic should keep usable rows rather than fail the whole catalog.
* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/emby-integration.md`.

## Assumptions

* Missing, null, empty, or blank `Id` means a library or video item row is unusable and should be skipped.
* Missing, null, empty, or blank `Name` means a library or video item row cannot be rendered meaningfully and should be skipped.
* Existing Emby library filtering, stream URL generation, thumbnail URL generation, resume metadata, series metadata, and typed error behavior should remain unchanged for valid rows.
* Item pagination should continue to advance by fetched row count, not mapped row count, so skipped rows do not cause unnecessary extra page requests once `TotalRecordCount` has been fetched.

## Requirements

* Model Emby item row fields that can be absent as nullable DTO fields:
  * `EmbyItemDto.id`
  * `EmbyItemDto.name`
* Convert Emby library rows to `VideoLibrary` with `mapNotNull`, skipping rows without a non-blank `Id` or non-blank `Name`.
* Convert Emby item rows to `VideoItem` with `mapNotNull`, skipping rows without a non-blank `Id` or non-blank `Name`.
* Trim valid `Id` and `Name` values before exposing them in domain models and URL builders.
* Preserve valid video item mapping for type, overview, year, duration, resume metadata, rating, series metadata, image URL, and stream URL.
* Preserve existing missing/null top-level `Items` behavior and typed error behavior.
* Preserve item pagination based on fetched page row count and `TotalRecordCount`.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Emby library rows with missing/null/blank `Id` or `Name` are skipped without crashing.
* [x] Emby item rows with missing/null/blank `Id` or `Name` are skipped without crashing.
* [x] Valid rows with surrounding whitespace in `Id` or `Name` map to trimmed domain values.
* [x] Valid video item mapping remains unchanged.
* [x] Missing/null top-level `Items` arrays still map to empty lists or stop pagination as before.
* [x] Item pagination still counts fetched rows, not only mapped videos, so skipped rows do not force unnecessary page requests when `TotalRecordCount` has already been fetched.
* [x] Focused repository tests cover unusable library rows, unusable item rows, trimming, and pagination total behavior with skipped rows.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* New video UI layouts or Yamby-style shelf changes.
* New Emby endpoints.
* Plex or WebDAV support.
* Changing Emby auth/session behavior.
* Playback engine behavior.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`.
* Expected implementation: make `EmbyItemDto.id` and `EmbyItemDto.name` nullable, add library and item `mapNotNull` boundaries, trim valid identity/display values, and keep item pagination advancing by fetched page rows.
