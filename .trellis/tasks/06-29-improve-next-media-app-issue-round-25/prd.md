# Improve Next Media App Issue Round 25

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome playlist payload mapping so compatible Subsonic/OpenSubsonic responses that omit or null playlist arrays return empty app lists instead of crashing playlist browsing.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across AudiobookShelf, Emby, and Navidrome album/artist detail payloads.
* `NavidromePlaylistList.playlist` and `NavidromePlaylistDetail.entry` are currently non-null Kotlin lists with `emptyList()` defaults.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `NavidromeRepository.getPlaylists()` maps `subsonic.playlists?.playlist?.map`.
* `NavidromeRepository.getPlaylistSongs()` maps `playlist?.entry?.map`.
* Existing Navidrome tests cover present playlist summary/detail mapping, cover art fallback, and blank cover art handling, but not missing/null playlist arrays.

## Assumptions

* Missing or null Navidrome `playlists.playlist` should behave like an empty playlist summary list.
* Missing or null Navidrome `playlist.entry` should behave like an empty playlist song list.
* Present playlist summaries and entries should keep existing cover art URL and stream URL mapping.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null Navidrome playlist summary `playlist` arrays as empty lists in `getPlaylists()`.
* Treat missing/null Navidrome playlist detail `entry` arrays as empty lists in `getPlaylistSongs()`.
* Preserve present playlist summary and playlist song mapping, including playlist cover fallback and stream URLs.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Playlist summary responses with missing/null `playlist` arrays map to empty playlist lists.
* [x] Playlist detail responses with missing/null `entry` arrays map to empty song lists.
* [x] Present playlists and entries still map correctly with cover art and stream URLs.
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
* Changes outside Navidrome playlist summary/detail list mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/quality-guidelines.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make `NavidromePlaylistList.playlist` and `NavidromePlaylistDetail.entry` nullable at the DTO boundary, then use `.orEmpty()` in repository mapping.
