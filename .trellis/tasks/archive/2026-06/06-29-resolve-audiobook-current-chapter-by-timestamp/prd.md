# Resolve Audiobook Current Chapter by Timestamp

## Goal

Make the audiobook player's current chapter chip resolve from chapter timestamps instead of relying on the incoming chapter list order. This keeps the displayed chapter aligned with playback/navigation behavior even if AudiobookShelf sends chapters out of order.

## What I Already Know

* `AudiobookPlaybackEngine` sorts chapters by `startSeconds` before previous/next chapter navigation.
* `AudiobookPlayerScreen` currently uses `state.chapters.lastOrNull { chapter.startSeconds <= state.positionSeconds }`, which assumes the list is already ordered.
* The player already has focused pure helpers in neighboring UI files that are unit tested from `app/src/test/java/com/nordic/mediahub/ui`.

## Requirements

* Resolve the displayed current chapter by sorted `startSeconds`.
* Clamp negative playback positions to the start when resolving the current chapter.
* Use the visible scrub position for chapter-chip resolution while the user drags the scrubber.
* Add focused unit coverage for unsorted chapters, negative positions, and no matching chapter.

## Acceptance Criteria

* [x] With unsorted chapters and position inside chapter 2, the helper returns chapter 2.
* [x] Negative positions do not select a chapter that starts after zero.
* [x] Positions before the first chapter return `null`.
* [x] The player screen uses the helper for its chapter chip.

## Definition of Done

* Tests added or updated where appropriate.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* Docs/spec notes updated if the AudiobookShelf playback contract needs clarification.

## Technical Approach

Add an `internal` helper in `AudiobookPlayerScreen.kt` for current chapter resolution, use it from the chip row, and cover it from `AudiobookScreenTest`.

## Decision (ADR-lite)

**Context**: Chapter navigation is already absolute-time based and sorts chapters. The displayed current chapter should follow the same rule rather than a UI-only order assumption.

**Decision**: Centralize current chapter selection in a small pure helper.

**Consequences**: The player chip stays correct for unsorted chapter lists and while scrubbing, with no changes to playback commands.

## Out of Scope

* Changing chapter navigation commands.
* Changing AudiobookShelf repository chapter mapping.
* Adding a chapter list picker to the player.

## Technical Notes

* Relevant implementation: `app/src/main/java/com/nordic/mediahub/ui/AudiobookPlayerScreen.kt`
* Relevant tests: `app/src/test/java/com/nordic/mediahub/ui/AudiobookScreenTest.kt`
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`
