# Resume video playback from Emby position

## Goal

Make Video playback honor Emby resume metadata so continue-watching items start from their saved playback position instead of restarting from the beginning.

## Requirements

- When starting a new playable `VideoItem`, seek to `playbackPositionSeconds` if it is positive and the item is not marked played.
- Clamp resume positions to the known `durationSeconds` when duration is available.
- Start from `0` for items marked played, items with no resume position, or non-positive resume values.
- Keep the existing manual scrubber and play/pause behavior unchanged.
- Do not add Emby playback-progress reporting in this task.

## Acceptance Criteria

- [ ] `VideoPlaybackEngine.play(...)` applies the initial resume seek for new videos.
- [ ] A pure helper test covers positive resume, played item reset, and clamping beyond duration.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests added or updated for changed behavior.
- Lint/type-check/test gates pass.
- Specs updated if the video playback contract changes.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Add an `internal` helper in `VideoPlaybackEngine.kt` to resolve initial playback start position in milliseconds.
- Call the helper after setting/preparing a new media item and before starting playback.
- Keep existing same-video behavior unchanged so returning to the same player state does not repeatedly jump back to the saved resume point.

## Decision (ADR-lite)

Context: The Video tab already maps Emby resume metadata and shows continue-watching shelves, but playback starts at zero.

Decision: Use the mapped `VideoItem.playbackPositionSeconds` only as an initial seek for new playback.

Consequences: Continue watching behaves like an Emby/Yamby-style client without introducing server-side progress writes yet.

## Out of Scope

- Reporting video playback progress back to Emby.
- Prompting the user to restart vs resume.
- Persisting local video progress.
- Changing playlist/episode autoplay behavior.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `emby-integration.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`, `app/src/test/java/com/nordic/mediahub/playback/VideoPlaybackEngineTest.kt`.
