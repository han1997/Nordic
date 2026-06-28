# Improve Next Media App Issue Round 18

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds tightened Navidrome and Emby backend response mapping.
* `AudiobookShelfRepository.bearerToken()` correctly treats blank login tokens as `AudiobookShelfApiException.Kind.AUTH` when the login response contains a `user` object.
* `AudiobookShelfLoginResponse.user` is modeled as non-null, so a 200 login response with missing or null `user` can cause a generic null failure before token validation.

## Assumptions

* Missing or null AudiobookShelf login `user` should be treated like a missing token and surface as an authentication failure.
* This is a backend repository contract fix and does not require UI changes.

## Requirements

* Treat missing and null AudiobookShelf login `user` values as `AudiobookShelfApiException.Kind.AUTH`.
* Preserve the existing token fallback from `user.token` to `user.accessToken`.
* Preserve successful login behavior when either token field is non-blank.
* Preserve existing music and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Missing/null AudiobookShelf login `user` responses throw `AudiobookShelfApiException.Kind.AUTH`.
* [x] Blank/missing AudiobookShelf login token responses still throw `AudiobookShelfApiException.Kind.AUTH`.
* [x] `user.accessToken` fallback still authenticates later repository calls.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the login response mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Persistent AudiobookShelf token storage or login UX changes.
* Changes outside AudiobookShelf login response validation.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/audiobookshelf-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/AudiobookShelfApi.kt`, `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/AudiobookShelfRepositoryTest.kt`.
* Expected implementation: make `AudiobookShelfLoginResponse.user` nullable, validate token lookup through a nullable user, and keep the existing `token` then `accessToken` fallback order.
