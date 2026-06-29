# brainstorm: improve next media app issue

## Goal

Improve the Android media app in a narrow, verifiable round by fixing Navidrome structured lyric timestamp handling.

## What I Already Know

* The ongoing product direction is to keep music inspired by Yinliu, audiobooks aligned with official AudiobookShelf behavior, and video aligned with Yamby.
* Recent rounds already reset stale music, audiobook, and video state after saved server configuration changes.
* The repo is clean and has no active Trellis task at the start of this round.
* `.trellis/spec/backend/quality-guidelines.md` says OpenSubsonic structured lyric `line.start` values are milliseconds and must be preserved before applying offset.
* `NavidromeRepository.toLyricStartMillis(...)` currently guesses that small structured lyric start values are seconds when they are less than the song duration, so a valid `120` ms start can become `120000` ms.

## Assumptions

* The fix should remove the seconds-vs-milliseconds heuristic for structured lyrics.
* Existing plain LRC timestamp parsing remains unchanged.

## Open Questions

* None blocking. The broad continuous-improvement goal authorizes choosing the next narrow issue automatically.

## Requirements

* Preserve OpenSubsonic structured lyric `line.start` values as milliseconds for all numeric ranges.
* Keep structured lyric offset behavior unchanged: subtract signed millisecond offset and clamp below zero to `0`.
* Add a repository unit test covering a small millisecond structured timestamp that used to be misread as seconds.

## Acceptance Criteria

* [x] `NavidromeRepository.getLyrics(...)` returns `startMillis = 120` for structured lyric `line.start = 120`.
* [x] Existing structured offset and LRC lyric tests still pass.
* [x] Code changes address only structured lyric timestamp handling and tests.
* [x] Standard Gradle gates pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Kotlin compilation, unit tests, and lint pass.
* Trellis finish flow records the work, archives the task, and journals the session.

## Out of Scope

* Plain LRC parser changes.
* Music player lyric rendering redesign.
* New lyric providers or server APIs.

## Technical Notes

* Task created from the ongoing autonomous improvement goal.
* Relevant files:
  * `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`
  * `.trellis/spec/backend/quality-guidelines.md`
