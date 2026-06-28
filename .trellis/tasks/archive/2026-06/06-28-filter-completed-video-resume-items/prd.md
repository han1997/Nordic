# Filter Completed Video Resume Items

## Goal

Make the video continue-watching shelf behave more like a real resume shelf by excluding items whose saved resume position has already reached the known video duration.

## Requirements

* Keep excluding items marked `isPlayed == true`.
* Keep excluding items with `playbackPositionSeconds <= 0`.
* When `durationSeconds > 0`, exclude items whose resume position is greater than or equal to the duration.
* When duration is unknown (`durationSeconds <= 0`), keep the existing resume eligibility behavior.
* Preserve the current sorting by `lastPlayedDate`, resume position fallback, and title.

## Acceptance Criteria

* [x] Continue-watching shelf excludes played items.
* [x] Continue-watching shelf excludes not-started items.
* [x] Continue-watching shelf excludes unplayed items whose resume position is at or beyond known duration.
* [x] Continue-watching shelf still includes resume items when duration is unknown.
* [x] Existing recency-first sorting behavior remains covered by tests.

## Definition of Done

* `:app:compileDebugKotlin` passes.
* `:app:testDebugUnitTest` passes.
* `:app:lintDebug` passes.
* Emby spec updated because this changes the video browsing UI contract.
* Work commit is created before task archive and journal commits.

## Technical Approach

Update `continueWatchingShelf(...)` in `VideoScreen.kt` to use an eligibility predicate that requires a positive resume position, not played, and not complete when duration is known. Add focused unit tests to `VideoScreenTest`.

## Decision (ADR-lite)

**Context**: Emby can return resume metadata that is at or beyond item duration while `Played` is still false. Showing those items in continue watching creates stale resume cards.

**Decision**: Treat `playbackPositionSeconds >= durationSeconds` as completed for shelf eligibility when duration is known.

**Consequences**: Continue watching remains useful for actual resume targets while incomplete server metadata still works when duration is unknown.

## Out of Scope

* Changing Emby repository mapping or playback resume clamping.
* Changing top-rated or unplayed shelf behavior.
* Persisting local playback history.
* Changing video detail or player UI layout.

## Technical Notes

* Target code: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`.
* Target tests: `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`.
* Relevant spec: `.trellis/spec/backend/emby-integration.md` video browsing UI continue-watching contract.
