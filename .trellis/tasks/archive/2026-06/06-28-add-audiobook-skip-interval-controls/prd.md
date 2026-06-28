# Add audiobook skip interval controls

## Goal

Improve the AudiobookShelf player with common audiobook-style 30 second skip back and skip forward controls while keeping progress sync and chapter navigation on the existing absolute seek path.

## Requirements

- Add audiobook playback engine commands for skipping backward and forward by a fixed 30 second interval.
- Clamp relative seek targets to `0..durationSeconds` so skip controls never seek negative or beyond the audiobook duration.
- Wire the commands from `MainActivity` into `AudiobookPlayerScreen`.
- Add player buttons for 30 second back/forward alongside the existing chapter previous/next and play/pause controls.
- Keep chapter navigation behavior unchanged.

## Acceptance Criteria

- [ ] `AudiobookPlaybackEngine` exposes relative skip commands using the existing `seekTo(positionSeconds)` path.
- [ ] The player UI has enabled 30 second back/forward controls when an audiobook session is loaded.
- [ ] Pure unit tests cover relative seek target clamping at the start, middle, and end of a book.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests added or updated for changed behavior.
- Lint/type-check/test gates pass.
- Specs updated if the playback contract changes.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Add `seekBackBy()` and `seekForwardBy()` commands to `AudiobookPlaybackEngine`, defaulting to 30 seconds.
- Add an `internal` helper for resolving relative audiobook seek targets so tests can verify boundary behavior without Media3.
- Add optional `onSeekBack` and `onSeekForward` callbacks to `AudiobookPlayerScreen`.
- Place the interval controls in the existing playback control row and keep chapter buttons disabled by chapter availability.

## Decision (ADR-lite)

Context: Audiobook clients typically provide short interval skip controls in addition to chapter navigation because chapter boundaries are too coarse for replaying or skipping narration.

Decision: Use a fixed 30 second interval for this MVP and route it through absolute `seekTo(...)`.

Consequences: The feature is simple and consistent with progress sync. Future work can add configurable intervals or long-press behavior without changing the AudiobookShelf API layer.

## Out of Scope

- Configurable skip duration.
- Playback speed controls.
- Sleep timer.
- AudiobookShelf server-side progress API changes.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `audiobookshelf-integration.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`, `app/src/main/java/com/nordic/mediahub/ui/AudiobookPlayerScreen.kt`, `app/src/main/java/com/nordic/mediahub/MainActivity.kt`, `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.
