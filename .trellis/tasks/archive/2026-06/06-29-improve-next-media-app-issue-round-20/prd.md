# Improve Next Media App Issue Round 20

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds tightened backend mapping for Navidrome, Emby, and AudiobookShelf auth/cover fields.
* During round 19 testing, a minimal AudiobookShelf expanded-detail fixture without `metadata.authors`, `metadata.narrators`, or `metadata.series` crashed in `AudiobookShelfRepository.toDetail()`.
* Gson can leave non-null Kotlin list properties as null when compatible servers omit optional list fields, so repository mapping should use empty-list fallbacks at the boundary.

## Assumptions

* Missing AudiobookShelf detail metadata arrays should behave like empty arrays.
* Missing AudiobookShelf detail chapter arrays should behave like empty arrays.
* This is a backend repository mapping fix and does not require UI changes.

## Requirements

* Treat missing/null AudiobookShelf expanded-detail `metadata.authors`, `metadata.narrators`, and `metadata.series` as empty lists.
* Treat missing/null AudiobookShelf expanded-detail `chapters` as an empty list.
* Preserve existing mapping for present authors, narrators, series sequence labels, and chapters.
* Preserve existing music and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Expanded audiobook detail responses with missing/null metadata arrays map to empty domain lists.
* [x] Expanded audiobook detail responses with missing/null chapters map to an empty chapter list.
* [x] Present authors, narrators, series, and chapters still map correctly.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Audiobook playback-session track handling.
* Changes outside AudiobookShelf expanded-detail list-field mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/audiobookshelf-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`, `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: make nullable list DTO fields where Gson may omit them, then map with `.orEmpty()` in `toDetail()`.
