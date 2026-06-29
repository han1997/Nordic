# Improve Next Media App Issue Round 23

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome album detail song-list mapping so compatible Subsonic/OpenSubsonic responses that omit or null `album.song` return empty song lists instead of crashing repository flows.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries in AudiobookShelf and Emby repository mapping.
* `NavidromeAlbumDetail.song` is currently a non-null Kotlin list with `emptyList()` default.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `NavidromeRepository` uses `albumDetail.song.map` in all-song loading and album-song/detail paths.
* Existing Navidrome tests cover present album songs, playlist mapping, lyrics, paging, and cover art behavior, but not missing/null album detail song arrays.

## Assumptions

* Missing or null Navidrome album detail `song` should behave like an empty song list.
* Present album detail songs should keep existing stream URL and cover art fallback mapping.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null Navidrome album detail `song` arrays as empty lists in repository mapping.
* Preserve present album song mapping, including stream URLs, song cover art, and album cover fallback behavior.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Album detail responses with missing/null `song` arrays map to empty song lists.
* [x] Present album songs still map correctly with stream URLs and cover art fallback.
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
* Changes outside Navidrome album-detail song-list mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/quality-guidelines.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make `NavidromeAlbumDetail.song` nullable at the DTO boundary, then use `.orEmpty()` in repository album-detail song mapping.
