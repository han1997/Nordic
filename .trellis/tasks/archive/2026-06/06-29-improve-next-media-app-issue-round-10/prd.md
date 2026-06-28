# Improve Next Media App Issue Round 10

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds improved repository response correctness across AudiobookShelf, Emby, and Navidrome.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `EmbyRepository` already treats playable video item types case-insensitively, but library filtering compares `CollectionType` and fallback `Type == "CollectionFolder"` case-sensitively.
* A mixed-case Emby response such as `CollectionType = "Movies"` or `Type = "collectionfolder"` can hide an otherwise valid video library.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Make Emby video library filtering case-insensitive for supported `CollectionType` values.
* Make the blank-collection fallback case-insensitive for `Type == "CollectionFolder"`.
* Preserve filtering of known non-video libraries.
* Keep the change scoped to Emby repository library filtering and focused regression tests.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Mixed-case video `CollectionType` values are included.
* [x] Blank-collection fallback accepts `CollectionFolder` regardless of case.
* [x] Non-video libraries remain excluded.
* [x] Focused tests cover the changed behavior.
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

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/emby-integration.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`.
* Expected implementation is a small predicate/helper inside `EmbyRepository` plus an `EmbyRepositoryTest` regression.
