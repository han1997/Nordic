# Improve Next Media App Issue Round 32

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome/OpenSubsonic recent-song loading so compatible `getRandomSongs.view` responses that omit or null the nested `randomSongs.song` array do not rely on Gson defaults or crash-prone non-null DTO assumptions. Empty or absent random-song arrays should keep the existing fallback to recently added album tracks.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across Navidrome and AudiobookShelf payloads.
* The repository was clean after round 31 before this task started.
* Recent rounds converted several optional Subsonic arrays to nullable DTO fields and normalized them with `orEmpty()` in repository mapping.
* `SongList.song` is still declared as `List<NavidromeSong> = emptyList()`, unlike neighboring optional Subsonic arrays.
* `NavidromeRepository.getRecentSongs()` maps `subsonic.randomSongs?.song` and then falls back to recently added album tracks if the random-song result is empty.
* Existing tests cover album detail missing/null `song` arrays, but not random-song missing/null arrays.
* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/error-handling.md`, and `.trellis/spec/backend/quality-guidelines.md`.

## Assumptions

* Missing or null `randomSongs.song` should behave like an empty random-song result.
* Existing fallback behavior from empty random-song results to recently added album tracks should remain intact.
* Present random songs should still map to playable `NavidromeSong` values with stream URLs and cover-art URLs.
* Audiobook and video behavior outside the selected issue should remain unchanged.

## Requirements

* Model `SongList.song` as a nullable wire field.
* Normalize `subsonic.randomSongs?.song` with `orEmpty()` in `getRecentSongs()`.
* Preserve fallback from missing/null/empty random-song arrays to recently added album tracks.
* Preserve present random-song mapping, including stream URL and cover-art URL construction.
* Preserve existing typed exception wrapping behavior.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The round is captured in a Trellis task before implementation.
* [x] The selected issue is documented in this PRD before implementation.
* [x] `SongList.song` is nullable at the API DTO boundary.
* [x] Missing/null `randomSongs.song` arrays do not crash recent-song loading and keep fallback to album-derived recent songs.
* [x] Present random songs still map correctly with playable stream URLs and cover-art URLs.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing/null random-song array decision and fallback behavior.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Broad UI redesigns.
* Dependency upgrades.
* Large architecture changes.
* Multiple unrelated bug fixes in one commit.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: change `SongList.song` to `List<NavidromeSong>? = null`, normalize random-song mapping with `.orEmpty()`, and add `MockWebServer` coverage where missing/null `randomSongs.song` responses fall back to album detail songs.
