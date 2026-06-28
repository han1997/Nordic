# Extend music home playback queue

## Goal

Make the Music tab's home "recently added" shelf start a longer continuous listening queue instead of limiting playback to the visible preview cards.

## Requirements

- Keep the home shelf preview capped at the existing 12 visible songs.
- When a user taps a preview song, start playback from the full `recentlyAddedSongs` backing list.
- Preserve the tapped preview index so playback starts on the selected song.
- Do not change song tab sorting, album detail playback, search playback, playlist playback, or queue engine behavior.

## Acceptance Criteria

- [ ] The home recently-added shelf still renders only the preview slice.
- [ ] Tapping a preview song passes the full recently-added list to `onSongSelected`.
- [ ] A focused unit test verifies preview index mapping into the full playback queue.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests added or updated for changed behavior.
- Lint/type-check/test gates pass.
- Specs updated if this round establishes a durable Music UI convention.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Add small `internal` helper functions in `MusicScreenV2.kt` for deriving the home preview list and home playback queue.
- Use the preview helper for rendering and the playback helper for `onSongSelected`.
- Keep the implementation in UI because this is view-state queue source selection, not a Media3 playback mutation.

## Decision (ADR-lite)

Context: The current home shelf preview uses `recentlyAddedSongs.take(12)` for both rendering and playback. This makes a home-started listening session stop after the visible shelf.

Decision: Separate home preview data from the playback queue source while keeping index identity position-based.

Consequences: Home playback feels more like continuous listening without expanding the visual shelf or changing playback engine contracts.

## Out of Scope

- Changing Navidrome recently-added API limits.
- Adding radio/autoplay recommendations.
- Changing shuffle/repeat defaults.
- Redesigning the Music home screen.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`, `app/src/test/java/com/nordic/mediahub/ui/MusicScreenV2Test.kt`.
