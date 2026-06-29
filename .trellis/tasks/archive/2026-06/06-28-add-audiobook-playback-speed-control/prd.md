# Add audiobook playback speed control

## Goal

Bring the AudiobookShelf player closer to official audiobook-client behavior by adding a simple playback speed control.

## Requirements

- Track audiobook playback speed in `AudiobookPlaybackState`.
- Add a playback engine command that cycles through common audiobook speeds: `0.75x`, `1.0x`, `1.25x`, `1.5x`, and `2.0x`.
- Apply the speed through Media3 so it affects actual playback, not just UI state.
- Render the current speed in `AudiobookPlayerScreen` and let the user cycle it while a session is loaded.
- Preserve existing play/pause, chapter navigation, skip interval, scrubber, progress sync, and close behavior.

## Acceptance Criteria

- [ ] `AudiobookPlaybackEngine` exposes a speed cycling command and publishes the current speed.
- [ ] `AudiobookPlayerScreen` shows the current speed and can trigger the cycle command.
- [ ] Pure unit tests cover speed cycling from known values and unknown/off-grid values.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests added or updated for changed behavior.
- Lint/type-check/test gates pass.
- Specs updated if the playback contract changes.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Add `playbackSpeed: Float = 1f` to `AudiobookPlaybackState`.
- Add `cyclePlaybackSpeed()` to `AudiobookPlaybackEngine`.
- Listen for Media3 playback parameter changes and publish state.
- Add `resolveNextAudiobookPlaybackSpeed(...)` as a pure helper for tests.
- Add an ASCII speed button like `1.25x` to the existing player control area.

## Decision (ADR-lite)

Context: Audiobook listeners frequently adjust speed; chapter and skip controls alone do not cover this listening workflow.

Decision: Use a fixed cycle of common speeds for the MVP rather than adding a menu or slider.

Consequences: The feature is simple, testable, and can later expand to a menu without changing playback state ownership.

## Out of Scope

- Persisting per-book or global speed preferences.
- Fine-grained speed slider.
- Sleep timer.
- Pitch configuration.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `audiobookshelf-integration.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`, `app/src/main/java/com/nordic/mediahub/ui/AudiobookPlayerScreen.kt`, `app/src/main/java/com/nordic/mediahub/MainActivity.kt`, `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`.
