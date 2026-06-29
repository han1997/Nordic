# Improve Next Media App Issue Round 24

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome artist detail album-list mapping so compatible Subsonic/OpenSubsonic responses that omit or null `artist.album` return empty album lists instead of crashing artist detail loading.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Round 23 hardened Navidrome album detail `song` arrays.
* `NavidromeArtistDetail.album` is currently a non-null Kotlin list with `emptyList()` default.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `NavidromeRepository.getArtistAlbums()` uses `detail.album.map { ... }` directly.
* Existing tests cover artist detail UI error messages, but not Navidrome repository artist album mapping.

## Assumptions

* Missing or null Navidrome artist detail `album` should behave like an empty album list.
* Present artist albums should keep existing cover art URL mapping.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null Navidrome artist detail `album` arrays as empty lists in `getArtistAlbums()`.
* Preserve present artist album mapping, including cover art URL normalization.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Artist detail responses with missing/null `album` arrays map to empty album lists.
* [x] Present artist albums still map correctly with cover art URLs.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* UI redesigns or playback queue changes.
* New server integrations.
* Broad nullable conversion for every Navidrome DTO list.
* Changes outside Navidrome artist detail album-list mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/quality-guidelines.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make `NavidromeArtistDetail.album` nullable at the DTO boundary, then use `.orEmpty()` in `getArtistAlbums()`.
