# Improve Video Browser Search For Episodes

## Goal

Improve Yamby-style video browsing by making the local video search find episodes through show-level metadata, not only episode titles. A user should be able to search a series name or season/episode code and still find matching Emby episode rows.

## What I already know

* The user wants autonomous improvement rounds without per-round confirmation.
* Recent completed rounds improved audiobook chapter ordering and video player handling for unknown duration.
* The repository is currently clean and has no active task.
* `VideoItem` already carries `seriesName`, `seasonNumber`, and `episodeNumber` from Emby.
* `VideoScreen` currently filters search matches with title, overview, type, and year only, so episodes can be hidden when the query is the show name or a common episode code.

## Assumptions (temporary)

* This round should be a local browser-search improvement, not an Emby API query change.
* Matching series metadata should work with the current in-memory catalog list.

## Open Questions

* None.

## Requirements (evolving)

* Video browser search must match `seriesName` for episodes and other items that provide it.
* Video browser search must match common season/episode tokens such as `S1`, `E2`, and `S1E2` when those numeric fields are present.
* Keep filtering local to the loaded catalog and preserve the existing type-filter behavior.
* Add focused unit tests for the search matcher.

## Acceptance Criteria (evolving)

* [x] Searching for an episode's series name returns that episode.
* [x] Searching for a compact season/episode code returns the matching episode.
* [x] Blank search queries continue to match all videos.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done (team quality bar)

* Tests added or updated where appropriate.
* Lint, type-check, and unit tests pass.
* Specs updated if new behavior or integration guidance is learned.
* Work is committed before Trellis archive and journal commits.

## Out of Scope (explicit)

* Large screen redesigns.
* New backend protocol integrations.
* Broad queue or playback rewrites unless inspection reveals a focused bug requiring them.
* Server-side Emby search or remote pagination changes.

## Technical Approach

Add an internal video search helper in `VideoScreen.kt`, use it from the existing `matchesSearch` extension, and cover it in `VideoScreenTest`. The helper will build a small set of human-searchable strings from existing `VideoItem` fields and apply the same case-insensitive substring behavior already used by the UI.

## Decision (ADR-lite)

**Context**: Emby episode titles often do not include the show name, but users expect a Yamby-like browser search for a show title to surface the episode rows already loaded in the catalog.

**Decision**: Extend local matching to include existing episode metadata (`seriesName`, `seasonNumber`, `episodeNumber`) rather than introducing a server-side search path.

**Consequences**: The change is low risk and works offline against loaded results. It does not solve searching content outside the currently loaded catalog page or selected library.

## Expansion Sweep

* Future evolution: server-side search across all libraries could come later if the loaded catalog is insufficient.
* Related scenarios: type filters should continue composing with search, so users can search a show name while filtered to episodes.
* Failure and edge cases: blank queries should remain match-all, and missing season/episode fields should not add misleading tokens.

## Technical Notes

* Relevant files inspected: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`.
* Relevant specs will be loaded before implementation.
