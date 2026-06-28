# Allow Video Relative Seek With Unknown Duration

## Goal

Make video skip controls work for streams whose duration is not known yet. When duration is unknown, skip-forward should move relative to the current position instead of clamping the target to zero.

## What I Already Know

* `VideoPlaybackEngine.seekBackBy` and `seekForwardBy` delegate to `resolveVideoRelativeSeekPositionSeconds`.
* The helper currently uses `durationSeconds.coerceAtLeast(0)` as the maximum position, so a duration of `0` clamps every relative seek target to `0`.
* `durationSeconds == 0` is used elsewhere as an unknown-duration case, including resume and continue-watching helpers.
* The Emby integration spec says relative seek targets are clamped to `0..durationSeconds` when duration is known.

## Requirements

* Preserve existing relative seek clamping for known positive durations.
* For unknown or non-positive durations, clamp only at the start and allow positive forward targets.
* Keep calculations overflow-safe when adding large positions and deltas.
* Add focused unit coverage for unknown-duration forward and backward relative seek.

## Acceptance Criteria

* [x] With `durationSeconds = 0`, position `40`, and `deltaSeconds = 30`, the helper returns `70`.
* [x] With `durationSeconds = 0`, position `5`, and `deltaSeconds = -10`, the helper returns `0`.
* [x] Existing known-duration relative seek behavior remains unchanged.

## Definition of Done

* Tests added or updated where appropriate.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* Docs/spec notes updated if the Emby playback contract needs clarification.

## Technical Approach

Update `resolveVideoRelativeSeekPositionSeconds` so positive durations retain bounded seeking, while zero or negative durations use an unbounded upper range with a lower clamp at zero. Add regression tests in `VideoPlaybackEngineTest`.

## Decision (ADR-lite)

**Context**: Emby can expose videos or streams where the player/app does not yet know duration. Treating `0` as both unknown and a hard maximum makes skip controls unusable.

**Decision**: Interpret non-positive duration as unknown for relative seek calculation.

**Consequences**: Skip-forward remains useful before duration is known, while skip-back still cannot seek before the start.

## Out of Scope

* Changing video progress reporting payloads.
* Changing initial resume behavior.
* Adding new player UI controls.

## Technical Notes

* Relevant implementation: `app/src/main/java/com/nordic/mediahub/playback/VideoPlaybackEngine.kt`
* Relevant tests: `app/src/test/java/com/nordic/mediahub/playback/VideoPlaybackEngineTest.kt`
* Relevant spec: `.trellis/spec/backend/emby-integration.md`
