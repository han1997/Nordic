# Defer video scrubber seeks

## Goal

Improve video playback performance and interaction stability by avoiding repeated player seeks while the user drags the scrubber.

## Requirements

- Keep the scrubber visually responsive during drag by tracking a local scrub position.
- Call `onSeek(...)` only when the user finishes scrubbing.
- Reset local scrub state after seeking and when the current video changes.
- Keep current display, play/pause, skip controls, close behavior, and resume behavior unchanged.

## Acceptance Criteria

- [ ] `VideoPlayerScreen` no longer calls `onSeek` on every slider value change.
- [ ] The displayed time follows the scrub position during drag.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Checks pass.
- Specs updated if this establishes a durable playback UI convention.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Mirror the existing Music/Audiobook pattern with `scrubPosition` local state.
- Use `Slider.onValueChange` only to update local state.
- Use `Slider.onValueChangeFinished` to call `onSeek(...)` once.

## Out of Scope

- Changing video playback engine seek semantics.
- Adding thumbnail preview while scrubbing.
- Emby progress sync.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `emby-integration.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant file: `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`.
