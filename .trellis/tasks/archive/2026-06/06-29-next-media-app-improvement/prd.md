# Skip LRC metadata in music lyrics

## Goal

Improve music lyric polish by preventing LRC metadata tags from appearing as visible lyric lines in the player, keeping the synced/unsynced lyric experience closer to a dedicated music app.

## What I already know

* The user wants autonomous rounds of app improvement with verification, commit, task archive, journal entry, then another round.
* Recent completed rounds improved Emby video progress sync and AudiobookShelf playback-session reuse.
* The repository is clean and has no active Trellis task at task creation time.
* The app is a single Kotlin/Jetpack Compose Android project under `app/`.
* `NavidromeRepository.parsePlainLyrics()` parses LRC timestamps but currently treats non-timestamp bracketed LRC metadata lines as plain lyric text.
* `MusicPlayerScreen` displays every parsed lyric line with non-blank text, so parser output directly affects the player UI.

## Assumptions (temporary)

* This round should stay narrow enough to implement, verify, commit, archive, and journal in one loop.
* Prefer existing project patterns over adding dependencies or broad redesign.
* LRC metadata tags such as `[ar:Artist]`, `[ti:Title]`, `[al:Album]`, `[by:Creator]`, `[length:03:30]`, and `[offset:+500]` are metadata, not lyric content.
* Bracketed plain lyric text without a metadata key, such as `[Chorus]`, should remain visible for unsynced lyrics.

## Open Questions

* None; persistent user goal authorizes small autonomous improvements.

## Requirements (evolving)

* Skip known LRC metadata-only rows when parsing plain lyrics from Navidrome/Subsonic responses.
* Preserve timed LRC lines, multi-timestamp lines, and unsynced plain text.
* Preserve bracketed plain lyric lines that are not known metadata keys.
* Add focused repository tests for metadata skipping and bracketed lyric preservation.

## Acceptance Criteria (evolving)

* [x] `getLyrics(...)` does not return visible `MusicLyricsLine` rows for known LRC metadata tags.
* [x] Timed LRC lines still parse as synced lyrics with expected timing.
* [x] Non-metadata bracketed text in plain lyrics remains visible.
* [x] Focused repository tests pass.
* [x] Required Gradle checks pass sequentially.

## Definition of Done (team quality bar)

* Tests added/updated where appropriate.
* Lint / typecheck / unit tests green.
* Docs/specs updated if behavior changes or a useful convention is learned.
* Task is committed, archived, and journaled before starting the next loop.

## Out of Scope (explicit)

* Visual redesign of the lyric screen.
* LRC offset timing adjustment.
* New lyric providers or edit/sync tools.
* New dependencies.

## Technical Notes

* Relevant spec index: `.trellis/spec/backend/index.md`.
* Relevant code:
  * `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
  * `app/src/test/java/com/nordic/mediahub/data/NavidromeRepositoryTest.kt`
* Verification commands on Windows must run sequentially:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
