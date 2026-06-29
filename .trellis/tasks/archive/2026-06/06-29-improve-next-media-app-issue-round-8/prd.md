# Improve Next Media App Issue Round 8

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds focused on AudiobookShelf response correctness and pagination.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `.trellis/spec/backend/emby-integration.md` requires empty Emby response bodies to throw `EmbyApiException(kind = API)`.
* `EmbyRepository.requireBody(...)` handles null Retrofit bodies, but request-time Gson `EOFException` from an empty 200 JSON body can escape before `requireBody(...)` runs and be wrapped as a generic `Exception`.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Convert empty Emby body responses from body-bearing endpoints into `EmbyApiException.Kind.API`.
* Cover API-key user lookup, password authentication, media library loading, and video item loading.
* Preserve existing HTTP error handling and playback progress success handling.
* Keep the implementation scoped to Emby repository response validation and focused regression tests.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Empty API-key user lookup responses throw `EmbyApiException.Kind.API`.
* [x] Empty password authentication responses throw `EmbyApiException.Kind.API`.
* [x] Empty media library responses throw `EmbyApiException.Kind.API`.
* [x] Empty video item-list responses throw `EmbyApiException.Kind.API`.
* [x] Existing HTTP error behavior remains unchanged.
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

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/emby-integration.md`.
* Expected implementation shape mirrors the recent AudiobookShelf empty-body fix, but scoped to `EmbyRepository` request calls.
