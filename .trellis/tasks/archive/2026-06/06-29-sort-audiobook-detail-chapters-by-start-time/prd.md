# Sort Audiobook Detail Chapters by Start Time

## Goal

Make audiobook detail pages display chapters in playback timeline order, even when AudiobookShelf returns the chapter array out of order.

## What I Already Know

* Audiobook playback navigation resolves chapters by sorted `startSeconds`.
* The audiobook player current-chapter chip also resolves by sorted `startSeconds`.
* `AudiobookScreen` detail currently renders `item.chapters` directly, so the visible chapter list can disagree with playback order if the server response is unsorted.

## Requirements

* Sort detail-page chapters by `startSeconds` before rendering.
* Preserve stable ordering for chapters with the same `startSeconds`.
* Preserve empty-list behavior.
* Add focused unit coverage for unsorted chapters and same-start stability.

## Acceptance Criteria

* [x] An unsorted chapter list displays in ascending `startSeconds`.
* [x] Chapters with equal `startSeconds` keep their original relative order.
* [x] Empty chapter lists remain empty.
* [x] The detail screen uses the sorted helper for chapter rows.

## Definition of Done

* Tests added or updated where appropriate.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* AudiobookShelf spec updated if the UI chapter-order contract needs clarification.

## Technical Approach

Add an `internal` pure helper in `AudiobookScreen.kt`, use it before the chapter `LazyColumn` rows, and test it from `AudiobookScreenTest`.

## Decision (ADR-lite)

**Context**: Playback behavior already treats chapter starts as the authoritative timeline. The detail UI should present the same order so users can scan chapters predictably.

**Decision**: Sort at the UI helper boundary by `startSeconds` instead of mutating repository mapping.

**Consequences**: Detail presentation becomes resilient to unordered server payloads without changing domain data or playback commands.

## Out of Scope

* Changing chapter navigation.
* Changing AudiobookShelf repository DTO mapping.
* Adding chapter playback buttons in the detail page.

## Technical Notes

* Relevant implementation: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`
* Relevant tests: `app/src/test/java/com/nordic/mediahub/ui/AudiobookScreenTest.kt`
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`
