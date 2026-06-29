# Improve Next Media App Issue Round 26

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome search result payload mapping so compatible Subsonic/OpenSubsonic responses that omit or null search result arrays return empty app lists instead of failing music search.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across AudiobookShelf, Emby, and Navidrome album, artist, and playlist payloads.
* `SearchResult3.artist`, `SearchResult3.album`, and `SearchResult3.song` are currently non-null Kotlin lists with `emptyList()` defaults.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `NavidromeRepository.search()` maps `result.artist`, `result.album`, and `result.song` directly.
* `NavidromeRepositoryTest` has broad Navidrome repository coverage but no focused search result mapping tests.

## Assumptions

* Missing or null Navidrome search result `artist`, `album`, or `song` arrays should behave like empty result buckets.
* Present search result items should keep existing artist initials, album cover-art URL mapping, song cover-art URL mapping, and song stream URLs.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null Navidrome search result `artist` arrays as empty artist lists in `search(query)`.
* Treat missing/null Navidrome search result `album` arrays as empty album lists in `search(query)`.
* Treat missing/null Navidrome search result `song` arrays as empty song lists in `search(query)`.
* Preserve present artist, album, and song search result mapping, including cover art and stream URLs.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Search responses with missing/null `artist`, `album`, and `song` arrays map each bucket to empty lists.
* [x] Present artist, album, and song search result items still map correctly.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision and positive search mapping.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* UI search redesigns.
* Search ranking, pagination, or debounce behavior.
* Broad nullable conversion for every Navidrome DTO list.
* Changes outside Navidrome search result list mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/quality-guidelines.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make `SearchResult3.artist`, `SearchResult3.album`, and `SearchResult3.song` nullable at the DTO boundary, then use `.orEmpty()` in repository mapping.
