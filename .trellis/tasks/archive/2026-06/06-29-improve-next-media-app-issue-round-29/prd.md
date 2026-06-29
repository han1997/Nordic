# Improve Next Media App Issue Round 29

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens AudiobookShelf library discovery so compatible responses that omit or null the `libraries` array return an empty audiobook library list instead of risking a repository mapping failure.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across Navidrome and earlier AudiobookShelf detail/playback payloads.
* `AudiobookShelfLibrariesResponse.libraries` is currently a non-null Kotlin list with an `emptyList()` default.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `AudiobookShelfRepository.getLibraries()` maps `body.libraries.mapNotNull` directly and filters `mediaType` to audiobook libraries.
* Existing repository tests cover token handling, case-insensitive `book` filtering, and empty response body handling, but not missing/null `libraries` arrays.

## Assumptions

* Missing or null AudiobookShelf `libraries` arrays should behave like an empty library list.
* Present libraries should keep the existing case-insensitive `book` media type filter.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null AudiobookShelf `libraries` arrays as empty lists in `getLibraries()`.
* Preserve present library mapping, including the case-insensitive `book` filter.
* Preserve auth error behavior and non-library AudiobookShelf behavior.
* Preserve music and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Library responses with missing/null `libraries` arrays map to empty audiobook library lists.
* [x] Present `book`, `Book`, and `BOOK` libraries still map correctly while non-book libraries are excluded.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* AudiobookShelf item pagination changes.
* Playback session or progress sync changes.
* UI redesigns for the Audiobook tab.
* Broad nullable conversion for every AudiobookShelf DTO list.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/audiobookshelf-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`, `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: make `AudiobookShelfLibrariesResponse.libraries` nullable at the DTO boundary, then use `.orEmpty()` in `getLibraries()`.
