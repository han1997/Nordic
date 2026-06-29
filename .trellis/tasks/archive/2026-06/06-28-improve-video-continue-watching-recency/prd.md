# Improve video continue watching recency

## Goal

Make the Video tab's Emby continue-watching shelf behave more like a native Emby/Yamby browsing surface by prioritizing the most recently watched items instead of items with the largest resume position.

## Requirements

- Map Emby `UserData.LastPlayedDate` into the app-facing `VideoItem` model.
- Request the same `UserData` field set through the existing Emby item listing flow; do not add new endpoints or local persistence.
- Sort the continue-watching shelf by last-played recency when available.
- Keep the existing fallback behavior for incomplete Emby responses by sorting items with no last-played date behind dated items, then by resume position.
- Keep top-rated, unplayed, filtering, and series-detail behavior unchanged.

## Acceptance Criteria

- [ ] `VideoItem` exposes nullable last-played metadata mapped from `UserData.LastPlayedDate`.
- [ ] Repository tests assert the value maps from Emby JSON and `UserData` remains requested.
- [ ] A focused unit test covers continue-watching ordering with dated and undated items.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests added or updated for changed behavior.
- Lint/type-check/test gates pass.
- Specs updated if this round establishes a durable contract.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Extend `EmbyUserDataDto` with `lastPlayedDate`.
- Add `lastPlayedDate: String?` to `VideoItem`.
- Extract continue-watching selection/sorting into an `internal` pure helper in `VideoScreen.kt` so the UI and unit test share the same logic.
- Use ISO-like Emby timestamp strings for descending ordering; this preserves chronological ordering without adding a parser dependency.

## Decision (ADR-lite)

Context: The current shelf sorts by `playbackPositionSeconds`, which can promote old near-finished videos over recently resumed items.

Decision: Use Emby-provided last-played metadata as the primary sort key and keep resume position as a compatibility fallback.

Consequences: The shelf becomes closer to Emby/Yamby client behavior while remaining resilient to older or incomplete server responses.

## Out of Scope

- Persisting local video watch history.
- Reporting video playback progress back to Emby.
- Changing direct playback or external player behavior.
- Redesigning the whole Video tab.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, `error-handling.md`, `emby-integration.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`, `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`.
