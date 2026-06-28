# Improve Next Media App Issue Round 17

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds fixed Navidrome blank cover-art ids and Emby password-login access-token validation.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `EmbyRepository` authentication still assumes Emby user ids are always present and non-blank.
* `EmbyUserDto.id` and `EmbyAuthenticateResponse.user` are modeled as non-null, so malformed or incomplete auth responses can fall into generic null failures instead of typed auth errors.

## Assumptions

* Missing, null, or blank Emby auth user ids should be treated as authentication failures.
* This is a backend repository contract fix and does not require UI changes.

## Requirements

* Treat missing, null, and blank Emby auth user ids as `EmbyApiException.Kind.AUTH`.
* Apply the validation to both API-key user lookup and username/password authentication.
* Preserve successful API-key login when a matching or fallback user has a non-blank id.
* Preserve successful username/password login when `User.Id` and `AccessToken` are both valid.
* Preserve existing music, audiobook, and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Missing/null/blank Emby API-key user ids throw `EmbyApiException.Kind.AUTH` when no usable user remains.
* [x] Missing/null/blank Emby password-login `User.Id` values throw `EmbyApiException.Kind.AUTH`.
* [x] Valid API-key and password-login responses still load catalog data.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the auth user-id validation decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Persistent Emby token storage or login UX changes.
* Changes outside Emby auth response validation.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/emby-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`.
* Expected implementation: make auth user ids nullable in the DTO, select only users with non-blank ids, and validate password-login `User.Id` before creating `EmbySession`.
