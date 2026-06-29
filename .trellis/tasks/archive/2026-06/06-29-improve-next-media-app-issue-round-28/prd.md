# Improve Next Media App Issue Round 28

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome album list payload mapping so compatible Subsonic/OpenSubsonic responses that omit or null album arrays return empty app lists instead of failing album browsing or all-song expansion.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across AudiobookShelf, Emby, and Navidrome album detail, artist, playlist, and search payloads.
* `AlbumList.album` is currently a non-null Kotlin list with an `emptyList()` default.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `NavidromeRepository.getAlbumList(...)` maps `subsonic.albumList2?.album`.
* Existing tests cover present album list mapping, sort request parameters, blank cover art, and empty album lists, but not missing/null album arrays.

## Assumptions

* Missing or null Navidrome `albumList2.album` should behave like an empty album page.
* Empty album pages should keep the existing paging behavior: stop paging and return the albums accumulated so far.
* Present albums should keep existing cover-art URL mapping.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null Navidrome `albumList2.album` arrays as empty album pages in album list mapping.
* Preserve present album mapping, including cover art URLs.
* Preserve existing album paging stop behavior on empty pages.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Album list responses with missing/null `album` arrays map to empty album lists.
* [x] Present album list items still map correctly with cover art URLs.
* [x] Existing album paging behavior remains unchanged for empty pages.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Random song fallback behavior.
* Album browsing UI redesigns.
* Album paging algorithm changes.
* Broad nullable conversion for every Navidrome DTO list.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/quality-guidelines.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make `AlbumList.album` nullable at the DTO boundary, then use `.orEmpty()` in `getAlbumList(...)`.
