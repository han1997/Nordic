# Improve Music Lyric Active Line Selection

## Goal

Improve the music player lyric display so synced lyrics do not highlight a line before any timed lyric has actually started. This supports a smoother continuous music listening experience by keeping lyric highlighting aligned with playback.

## Requirements

* Keep unsynced lyrics unchanged: show the first visible lines with no active line.
* For synced lyrics, only a timestamped lyric line can become active.
* If playback is before the first timestamped lyric line, show the opening lyric window with no active line.
* Leading untimed lyric lines may remain visible, but they must not become active just because no timestamped line has started.
* Preserve the current centered-window behavior once an active timed line exists.

## Acceptance Criteria

* [x] Synced lyrics before the first timestamp produce no active visible line.
* [x] The first timed lyric line becomes active when playback reaches its start time.
* [x] Leading untimed lines remain visible but inactive.
* [x] Unsynced lyric behavior remains unchanged.
* [x] Focused unit tests cover the lyric selection helper.

## Definition of Done

* Tests added or updated for the lyric selection edge cases.
* `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially on Windows.
* Specs updated only if the task reveals a reusable convention or project rule.
* Task work is committed before Trellis finish/archive bookkeeping.

## Technical Approach

Update `selectVisibleLyricLines(...)` in `MusicPlayerScreen.kt` to represent "no active timed lyric yet" explicitly instead of coercing `indexOfLast(...) == -1` to `0`. Keep the visible window anchored at the start until a real active index exists, then reuse the existing window calculations.

Add focused UI-package unit tests that exercise the pure lyric helper, following the existing `MusicScreenV2Test` and `VideoScreenTest` style.

## Decision (ADR-lite)

**Context**: The current helper uses `coerceAtLeast(0)` after searching for the last timestamp at or before playback position. That makes the first visible line active even when the first line is untimed or when playback has not reached the first timed line.

**Decision**: Treat the active index as nullable for the "not started" state and map active display only when a timed line has started.

**Consequences**: The UI avoids premature lyric emphasis without changing data loading, parsing, or Media3 playback state.

## Out of Scope

* Changing Navidrome lyric fetching or parsing.
* Adding karaoke-style word highlighting.
* Changing music playback queue or continuous playback behavior.
* Changing visual typography or layout outside active-line selection.

## Technical Notes

* Target code: `app/src/main/java/com/nordic/mediahub/ui/MusicPlayerScreen.kt`.
* Existing tests use package-level pure helper tests under `app/src/test/java/com/nordic/mediahub/ui/`.
* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/directory-structure.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/guides/code-reuse-thinking-guide.md`.
