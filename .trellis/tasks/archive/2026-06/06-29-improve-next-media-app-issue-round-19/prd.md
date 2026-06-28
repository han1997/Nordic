# Improve Next Media App Issue Round 19

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds tightened backend response mapping for Navidrome, Emby, and AudiobookShelf.
* `AudiobookShelfRepository` maps `coverPath` values through `media.coverPath?.toAbsoluteCoverUrl()` for summaries, details, and playback sessions.
* Empty or whitespace-only `coverPath` values can currently become malformed absolute cover URLs instead of absent cover art.

## Assumptions

* Missing, empty, and blank AudiobookShelf cover paths should be treated as absent artwork.
* Non-blank relative and absolute cover paths should keep the current URL normalization behavior.
* This is a backend repository mapping fix and does not require UI changes.

## Requirements

* Treat null, empty, and blank AudiobookShelf `coverPath` values as absent.
* Preserve relative cover-path normalization against the configured server base URL.
* Preserve absolute `http://` and `https://` cover URLs as-is.
* Apply the behavior to audiobook summaries, audiobook details, and playback sessions.
* Preserve existing music and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Blank AudiobookShelf summary `coverPath` values map to `null`.
* [x] Blank AudiobookShelf detail/session `coverPath` values map to `null`.
* [x] Non-blank relative and absolute cover paths still map correctly.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the cover-path mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Image loading UI changes.
* Changes outside AudiobookShelf cover-path normalization.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/audiobookshelf-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt` and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: replace direct nullable calls to `toAbsoluteCoverUrl()` with a nullable helper that rejects blank strings before normalizing non-blank paths.
