# Improve Next Media App Issue Round 31

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome/OpenSubsonic structured lyrics mapping so compatible lyric responses that omit or null structured lyric arrays or line arrays do not break lyric loading, and can still fall back to plain lyrics when available.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list boundaries across Navidrome and AudiobookShelf payloads.
* `NavidromeLyricsList.structuredLyrics` and `NavidromeStructuredLyrics.line` are currently non-null Kotlin lists with `emptyList()` defaults.
* Gson can still deserialize omitted or explicit-null list fields as runtime nulls.
* `SubsonicData.toMusicLyrics()` filters structured lyrics by calling `lyrics.line.any { ... }`.
* `NavidromeStructuredLyrics.toMusicLyrics()` maps `line` directly.
* Existing lyric tests cover present structured lyrics, structured offsets, LRC metadata, and plain lyric parsing, but not missing/null structured lyric arrays.

## Assumptions

* Missing or null `lyricsList.structuredLyrics` should behave like no structured lyrics.
* Missing or null nested structured `line` arrays should make that structured lyric entry unusable, not an error.
* If no usable structured lyric exists but plain lyrics are present, the repository should keep falling back to plain lyrics.
* Present structured lyrics should keep existing millisecond timing and offset behavior.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null `lyricsList.structuredLyrics` arrays as absent structured lyrics.
* Treat missing/null structured lyric `line` arrays as empty structured lyric lines.
* Preserve fallback from unusable structured lyrics to plain lyrics.
* Preserve present structured lyric mapping, including millisecond starts, offsets, sorting, and blank-line filtering.
* Preserve audiobook and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Lyrics responses with missing/null `structuredLyrics` arrays do not crash and can fall back to plain lyrics.
* [x] Structured lyric entries with missing/null `line` arrays are ignored as unusable structured lyrics.
* [x] Present structured lyrics still map correctly with timing and offset behavior.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision and fallback behavior.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Lyric rendering UI redesigns.
* LRC parser behavior changes beyond existing tests.
* Broad nullable conversion for every Navidrome DTO list.
* New lyric source endpoints.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md` and `.trellis/spec/backend/quality-guidelines.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make `NavidromeLyricsList.structuredLyrics` and `NavidromeStructuredLyrics.line` nullable at the DTO boundary, then use `.orEmpty()` in structured lyric selection and mapping.
