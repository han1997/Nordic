# Apply LRC offset to music lyrics

## Goal

Improve synced music lyrics by honoring LRC `[offset:...]` metadata so displayed lyric timing reflects the source file's intended global timestamp adjustment.

## What I already know

* The previous round made known LRC metadata rows invisible in the lyric view.
* `NavidromeRepository.parsePlainLyrics()` currently parses timestamped LRC rows but does not apply `[offset:...]`.
* The player consumes `MusicLyricsLine.startMillis`, so parser timing directly controls active lyric highlighting.
* This is a small music polish improvement aligned with the user's Yinliu-inspired music direction.

## Assumptions

* The change should be limited to plain LRC parsing in `NavidromeRepository`.
* Offset adjustment should be tested through `NavidromeRepository.getLyrics(...)` with `MockWebServer`, matching existing repository test patterns.
* Negative adjusted timestamps should clamp to `0` rather than producing negative lyric times.

## Open Questions

* None.

## Requirements

* Parse `[offset:<signed milliseconds>]` metadata from plain LRC lyrics.
* Apply the offset to every timestamped plain LRC line using LRC semantics: positive offsets make lyrics appear sooner, so subtract the signed offset from parsed timestamps.
* Clamp adjusted timestamps below zero to `0`.
* Keep offset rows hidden from visible lyric text.
* Preserve existing behavior for lyrics without offset metadata.

## Acceptance Criteria

* [x] Positive LRC offset makes parsed timestamps earlier.
* [x] Negative LRC offset makes parsed timestamps later.
* [x] Positive LRC offset clamps adjusted timestamps below zero.
* [x] Offset rows do not appear as visible lyric lines.
* [x] Focused repository tests pass.
* [x] Required Gradle checks pass sequentially.

## Definition of Done

* Tests added/updated where appropriate.
* Compile, full unit tests, and lint pass.
* Spec updated if the offset contract should be preserved.
* Task is committed, archived, and journaled before the next loop.

## Out of Scope

* Lyric offset editing UI.
* Persisting per-song manual lyric delay.
* Structured lyrics timing changes.
* New lyric providers.

## Technical Notes

* Relevant code:
  * `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`
  * `.trellis/spec/backend/quality-guidelines.md`
* Research:
  * `research/lrc-offset-semantics.md`
* Verification commands on Windows must run sequentially:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
