# Improve Next Media App Issue Round 21

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

This round hardens AudiobookShelf playback-session mapping so compatible servers that omit or null optional playback list fields do not crash `startPlayback()`.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Round 20 fixed the same Gson boundary issue for expanded item detail metadata and chapter lists.
* `AudiobookShelfPlaybackSessionDto.chapters` and `audioTracks` are currently non-null Kotlin list properties.
* `AudiobookShelfRepository.startPlayback()` maps `session.chapters.map { ... }` and `session.audioTracks.mapNotNull { ... }` directly.
* Gson can leave omitted or explicit-null list fields as runtime nulls even when the Kotlin property is declared non-null.
* The app already treats sessions with no playable tracks as a playback state error in the playback engine; repository mapping should return an empty list instead of crashing first.

## Assumptions

* Missing or null playback-session `chapters` should behave like an empty chapter list.
* Missing or null playback-session `audioTracks` should behave like an empty playable track list.
* Present chapter and audio track mapping must remain unchanged.
* This is a backend repository/API mapping fix and does not require UI changes.

## Requirements

* Treat missing/null AudiobookShelf playback-session `chapters` as an empty `AudiobookPlaybackSession.chapters` list.
* Treat missing/null AudiobookShelf playback-session `audioTracks` as an empty `AudiobookPlaybackSession.audioTracks` list.
* Preserve mapping for present playback-session chapters and playable audio tracks, including relative audio URL token handling.
* Preserve existing music and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Playback-session responses with missing/null `chapters` map to an empty chapter list.
* [x] Playback-session responses with missing/null `audioTracks` map to an empty audio track list.
* [x] Present playback-session chapters and audio tracks still map correctly.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the missing-list mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* UI redesigns or player surface changes.
* New server integrations.
* Changes to playback no-track error presentation.
* Changes outside AudiobookShelf playback-session list-field mapping.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/audiobookshelf-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`, `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: make playback-session list DTO fields nullable where Gson may omit them, then map with `.orEmpty()` in `startPlayback()`.
