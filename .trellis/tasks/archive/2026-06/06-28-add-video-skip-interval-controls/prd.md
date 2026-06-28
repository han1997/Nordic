# Add video skip interval controls

## Goal

Improve the Video player with Yamby/Emby-style short interval controls so users can quickly jump backward or forward without relying only on the scrubber.

## Requirements

- Add `VideoPlaybackEngine` commands for skipping backward and forward by fixed intervals.
- Use 10 seconds for backward skip and 30 seconds for forward skip.
- Clamp relative seek targets to `0..durationSeconds` when duration is known.
- Wire the commands through `MainActivity` into `VideoPlayerScreen`.
- Add compact player buttons for back/forward skip alongside the existing play/pause control.
- Keep existing resume-on-start, manual scrubber, play/pause, and close behavior unchanged.

## Acceptance Criteria

- [ ] Playback layer exposes relative video skip commands that use the existing absolute `seekTo(positionSeconds)` path.
- [ ] Video player UI has enabled skip back/forward controls when a video is loaded.
- [ ] Unit tests cover relative seek target clamping at start, middle, and end.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests added or updated for changed behavior.
- Lint/type-check/test gates pass.
- Specs updated if playback contracts change.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Add pure helper `resolveVideoRelativeSeekPositionSeconds(...)` in `VideoPlaybackEngine.kt`.
- Add `seekBackBy(...)` and `seekForwardBy(...)` commands with default intervals.
- Pass `onSeekBack` and `onSeekForward` callbacks to `VideoPlayerScreen`.
- Keep UI labels ASCII (`-10`, `+30`) to avoid touching existing non-ASCII copy.

## Decision (ADR-lite)

Context: The video player already supports scrubber seeking and resume start, but lacks quick correction controls.

Decision: Implement fixed skip intervals in the playback engine and render small controls in the existing control row.

Consequences: The feature improves Yamby-style playback ergonomics without adding server-side state or changing Emby APIs.

## Out of Scope

- Emby playback progress reporting.
- Configurable skip durations.
- Keyboard/remote shortcuts.
- Episode autoplay.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `emby-integration.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`, `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`, `app/src/main/java/com/nordic/mediahub/MainActivity.kt`, `app/src/test/java/com/nordic/mediahub/playback/VideoPlaybackEngineTest.kt`.
