# Improve Next Media App Issue Round 13

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds fixed stale stream reuse in music and video playback.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `AudiobookShelfRepository.getLibraries()` filters libraries with `dto.mediaType != "book"`, which is case-sensitive.
* AudiobookShelf-compatible responses should not drop valid book libraries only because `mediaType` casing differs.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Treat AudiobookShelf library `mediaType` values equal to `book` case-insensitively as audiobook libraries.
* Continue excluding non-book library media types.
* Add focused repository coverage for case-insensitive book library filtering.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] `getLibraries()` includes `mediaType` values such as `book`, `Book`, and `BOOK`.
* [x] `getLibraries()` still excludes non-book library media types.
* [x] Focused tests cover the library filtering decision.
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
* Changes to AudiobookShelf playback session or progress sync behavior.

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/audiobookshelf-integration.md`.
* Expected implementation is a one-line repository filter change plus a MockWebServer regression test.
