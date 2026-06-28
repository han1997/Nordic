# Restart Audiobook Chapter at Threshold Boundary

## Goal

Make audiobook previous-chapter navigation behave consistently at the restart threshold. When playback is exactly the configured threshold after the current chapter start, the action should restart the current chapter instead of jumping to the prior chapter.

## What I Already Know

* The app is an Android media hub with AudiobookShelf playback support.
* `AudiobookPlaybackEngine.seekToPreviousChapter()` delegates chapter target selection to `resolvePreviousAudiobookChapterStartSeconds`.
* The resolver defaults to a 5 second restart threshold but currently uses a strict greater-than comparison, so exactly 5 seconds after chapter start is treated as near the start.
* Existing unit tests cover after-threshold, near-start, and first-chapter cases, but not the exact boundary.

## Requirements

* Treat `safePosition - currentChapter.startSeconds >= restartThresholdSeconds` as enough progress to restart the current chapter.
* Preserve existing behavior when playback is less than the threshold after chapter start.
* Preserve existing behavior for the first chapter and empty chapter lists.
* Add focused unit coverage for the exact threshold boundary.

## Acceptance Criteria

* [x] At exactly 5 seconds into a non-first chapter, previous chapter resolves to the current chapter start.
* [x] Less than 5 seconds into a non-first chapter still resolves to the previous chapter start.
* [x] Existing audiobook playback resolver tests continue to pass.

## Definition of Done

* Tests added or updated where appropriate.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* Docs/spec notes updated only if a reusable convention is discovered.

## Technical Approach

Update the previous-chapter resolver comparison and add a boundary-focused test in `AudiobookPlaybackEngineTest`.

## Decision (ADR-lite)

**Context**: Previous-chapter controls typically restart the current chapter once the listener has made meaningful progress into it, then jump backward only while near the start.

**Decision**: Make the threshold inclusive so the configured threshold value is the first restart point.

**Consequences**: This changes only a one-second boundary case for callers using integer seconds and makes the threshold easier to reason about.

## Out of Scope

* Changing the default threshold duration.
* Adding new audiobook UI controls.
* Changing next-chapter navigation.

## Technical Notes

* Relevant implementation: `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`
* Relevant tests: `app/src/test/java/com/nordic/mediahub/playback/AudiobookPlaybackEngineTest.kt`
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`
