# Restrict video series episode fallback matching

## Goal

Keep Yamby-style Emby series details from mixing unrelated episodes into a selected series. Episodes should match the selected series by `seriesId` when that field is available, and use `seriesName` only as a fallback for incomplete episode metadata.

## What I Already Know

* The long-running goal asks to improve video behavior with Yamby as a reference.
* `VideoScreen` derives related episodes from the already-loaded Emby library item list.
* The Emby integration spec says: match episodes by `seriesId == selectedSeries.id`, with `seriesName == selectedSeries.title` as a fallback for incomplete responses.
* Current `relatedEpisodesFor(...)` includes an episode when either `seriesId` matches or `seriesName` matches, even if `seriesId` is present but points to another series.

## Requirements

* Keep exact `seriesId == selectedSeries.id` matching.
* Use `seriesName == selectedSeries.title` only when the episode `seriesId` is missing or blank.
* Exclude non-episode items from related episodes.
* Preserve existing episode sort order by season number, episode number, then title.
* Cover matching and sorting with focused unit tests.

## Acceptance Criteria

* [x] Episode with matching `seriesId` is included.
* [x] Episode with missing/blank `seriesId` and matching `seriesName` is included.
* [x] Episode with a different non-blank `seriesId` is excluded even when `seriesName` matches.
* [x] Non-episode items are excluded.
* [x] Related episodes sort by season, episode, then title.
* [x] Focused unit tests pass.

## Definition of Done

* Tests added or updated where behavior changes.
* Kotlin compile, unit tests, and lint pass.
* Specs or notes updated if behavior changes.
* Trellis finish workflow is run at the end of the round.

## Technical Approach

Expose the existing related-episode resolver as an `internal` helper in `VideoScreen.kt`, tighten the fallback condition to only use `seriesName` when `seriesId` is blank, and add focused `VideoScreenTest` coverage.

## Out of Scope

* Repository mapping changes.
* New Emby API calls.
* Series detail layout redesign.
* Playback changes.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`.
* Relevant spec: `.trellis/spec/backend/emby-integration.md`.
