# Improve Next Media App Issue Round 16

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds fixed refreshed media stream URLs, AudiobookShelf media URL edge cases, and Navidrome blank cover-art ids.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* This round should select one concrete issue from repository inspection and keep the change narrow enough to implement, verify, commit, archive, journal, and continue automatically.
* `EmbyRepository` password login checks `response.accessToken.isBlank()`, but `EmbyAuthenticateResponse.accessToken` is modeled as a non-null `String`.
* If an Emby-compatible server returns a 200 authentication response with `AccessToken` missing or null, Gson can leave the Kotlin field null and the repository can throw a generic null crash instead of the typed auth error the Emby contract expects.

## Assumptions

* Missing or null `AccessToken` should be treated like a blank access token.
* This is a backend repository contract fix and does not require UI changes.

## Requirements

* Treat blank, missing, and null Emby password-login `AccessToken` values as authentication failures.
* Preserve successful password login behavior when a non-blank access token is returned.
* Preserve API-key login behavior.
* Surface the failure as `EmbyApiException.Kind.AUTH`, not a generic wrapped exception.
* Preserve existing music, audiobook, and video behavior outside the selected issue.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Missing/null Emby password-login `AccessToken` responses throw `EmbyApiException.Kind.AUTH`.
* [x] Blank Emby password-login `AccessToken` responses still throw `EmbyApiException.Kind.AUTH`.
* [x] Successful password login with a non-blank token still loads catalog data.
* [x] The implementation addresses the selected issue without broad unrelated refactors.
* [x] Focused repository tests cover the auth response mapping decision.
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
* Changes outside the selected narrow issue.
* Persistent Emby token storage or login UX changes.

## Technical Notes

* Relevant specs include `.trellis/spec/backend/index.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`, and `.trellis/spec/backend/emby-integration.md`.
* Relevant code: `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, and `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`.
* Expected implementation: make the authentication DTO token nullable, check `isNullOrBlank()`, then only create `EmbySession` with a non-null token after validation.
