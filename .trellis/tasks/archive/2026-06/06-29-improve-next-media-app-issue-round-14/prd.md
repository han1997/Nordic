# Improve Next Media App Issue Round 14

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds improved stream refresh behavior and AudiobookShelf library filtering.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `AudiobookShelfRepository.toAbsoluteAudioUrl(...)` currently decides whether to append the bearer token with `absolute.contains("token=")`.
* A raw substring check can incorrectly skip token appending when `token=` appears in the path or another non-query location.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Detect an existing AudiobookShelf audio token by inspecting URL query parameter names, not by raw substring search.
* Append `token=<bearer token>` when an audio URL has no `token` query parameter, even if `token=` appears in the path.
* Preserve URLs that already contain a `token` query parameter without adding a duplicate.
* Add focused repository coverage for the path-substring regression.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Audio URLs with no `token` query parameter receive the bearer token.
* [x] Audio URLs whose path contains `token=` still receive the bearer token.
* [x] Audio URLs that already contain a `token` query parameter are not duplicated.
* [x] Focused tests cover the token detection decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Dependency upgrades unless required by the selected issue.
* Changes to playback session, progress sync, or library browsing behavior.

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/audiobookshelf-integration.md`.
* Expected implementation is a small structured URL handling change in `AudiobookShelfRepository.kt` plus MockWebServer tests.
