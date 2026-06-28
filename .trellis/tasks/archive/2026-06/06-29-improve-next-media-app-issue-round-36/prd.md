# Improve Next Media App Issue Round 36

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens Navidrome/OpenSubsonic structured lyric parsing so compatible responses with missing, null, empty, or blank structured line text do not crash lyric loading. The repository should ignore unusable structured lines, keep valid structured lines, and still fall back to plain lyrics when no structured text is usable.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds hardened nullable list and row-field boundaries across Navidrome, AudiobookShelf, and Emby payloads.
* `NavidromeLyricsList.structuredLyrics` and `NavidromeStructuredLyrics.line` are already nullable and normalized with `orEmpty()`.
* `NavidromeStructuredLyricLine.value` is still modeled as a non-null `String = ""`.
* `SubsonicData.toMusicLyrics()` filters structured lyrics with `it.value.isNotBlank()`.
* `NavidromeStructuredLyrics.toMusicLyrics()` maps lines with `lyricLine.value.trim()`.
* Gson can deserialize omitted or explicit-null string fields as runtime nulls despite non-null Kotlin declarations.
* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, and the Navidrome lyrics section in `.trellis/spec/backend/quality-guidelines.md`.

## Assumptions

* Missing, null, empty, or blank structured line `value` is not a usable lyric line and should be skipped.
* A structured lyrics entry with only unusable line text should not block plain lyric fallback.
* Valid structured lines should keep existing millisecond start, offset, trimming, and synced/unsynced behavior.
* Plain LRC parsing behavior is out of scope except for fallback still working.

## Requirements

* Model `NavidromeStructuredLyricLine.value` as nullable.
* Treat missing, null, empty, and blank structured line values as absent.
* Skip unusable structured lines while preserving valid structured lines from the same structured entry.
* If no structured lyrics entry contains usable text, fall back to plain lyrics when present.
* Preserve existing structured lyric start-time, offset, clamping, and synced-state behavior for valid lines.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Structured lyric lines with missing/null/empty/blank `value` are skipped without crashing.
* [x] Valid structured lyric lines in the same response still map with trimmed text.
* [x] Structured entries with no usable line values fall back to plain lyrics when plain lyrics are present.
* [x] Existing structured lyric offset and millisecond start behavior remains unchanged.
* [x] Focused repository tests cover unusable structured line values and plain fallback.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* UI lyric rendering changes.
* Plain LRC parser changes beyond fallback coverage.
* New Navidrome/OpenSubsonic endpoints.
* Album, playlist, or song catalog mapping changes.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`, `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`.
* Expected implementation: make the structured lyric line value nullable, normalize with `orEmpty()` at the repository boundary, and add regression tests for missing/null/blank values.
