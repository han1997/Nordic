# Preserve structured lyric timing with offsets

## Goal

Improve music lyric sync by honoring OpenSubsonic/Navidrome structured lyric timing exactly, including the optional `structuredLyrics.offset` field.

## What I Already Know

* The app already supports Navidrome `getLyricsBySongId` structured lyrics and prefers them over plain lyrics.
* OpenSubsonic documents structured lyric `line.start` values in milliseconds.
* OpenSubsonic documents `structuredLyrics.offset` as an optional millisecond adjustment where positive means lyrics appear sooner and negative means later.
* `NavidromeStructuredLyrics` currently lacks an `offset` field, so Nordic ignores structured lyric offset metadata.
* The player consumes `MusicLyricsLine.startMillis`, so repository mapping directly controls active lyric highlighting.

## Requirements

* Add structured lyric offset mapping to the Navidrome API DTO.
* Apply the offset to each structured lyric line start time.
* Positive offsets must make line timestamps earlier by subtracting the offset.
* Negative offsets must make line timestamps later.
* Adjusted timestamps below zero must clamp to `0`.
* Missing offset must preserve existing timing behavior.
* Add focused repository tests for structured lyric offset mapping.

## Acceptance Criteria

* [x] Structured lyrics with no offset preserve documented millisecond `line.start` values.
* [x] Positive structured offset makes line timestamps earlier.
* [x] Negative structured offset makes line timestamps later.
* [x] Positive structured offset clamps adjusted timestamps below zero.
* [x] Focused repository tests pass.
* [x] Required Gradle checks pass sequentially.

## Definition of Done

* Tests added/updated where appropriate.
* Compile, full unit tests, and lint pass.
* Spec updated to preserve the structured lyric offset contract.
* Task is committed, archived, and journaled before the next loop.

## Out of Scope

* Word/syllable-level `cueLine` rendering.
* Multiple lyric kind/language selection UI.
* Manual lyric delay controls.
* Plain LRC parsing changes.

## Research References

* `research/opensubsonic-structured-lyrics.md` - OpenSubsonic structured `line.start` values are milliseconds and `offset` is a signed millisecond adjustment.

## Technical Notes

* Relevant code:
  * `app/src/main/java/com/nordic/mediahub/api/NavidromeApi.kt`
  * `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`
  * `.trellis/spec/backend/quality-guidelines.md`
* Verification commands on Windows must run sequentially:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
