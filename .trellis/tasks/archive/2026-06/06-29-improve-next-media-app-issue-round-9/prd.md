# Improve Next Media App Issue Round 9

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds improved AudiobookShelf and Emby repository response correctness.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `NavidromeRepository` currently has typed `HTTP` and `SUBSONIC` failures, but empty successful response bodies are classified as `HTTP` or can be wrapped as generic exceptions when Gson throws `EOFException` before a `Response` is returned.
* AudiobookShelf and Emby now treat empty body responses as typed API errors; music should preserve the same boundary clarity.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Add a typed Navidrome API/body error classification for empty Subsonic responses.
* Convert null Retrofit bodies and converter-time empty-body `EOFException` into `NavidromeApiException.Kind.API`.
* Route Navidrome Subsonic repository calls through a shared validator so the behavior is consistent across music browsing, playlists, search, artists, albums, songs, and lyrics lookup.
* Preserve existing HTTP and Subsonic protocol error behavior.
* Add focused regression coverage for typed empty-body handling and unchanged HTTP classification.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Empty Subsonic 200 response bodies throw `NavidromeApiException.Kind.API`.
* [x] Converter-time empty-body `EOFException` is not wrapped as a generic `Exception`.
* [x] HTTP failures still throw `NavidromeApiException.Kind.HTTP`.
* [x] Subsonic `status != ok` failures still throw `NavidromeApiException.Kind.SUBSONIC`.
* [x] Navidrome Subsonic call sites use the shared validator rather than direct `Response.requireResponse()` calls.
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

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`.
* Expected implementation shape mirrors the repository response-body helpers recently added for AudiobookShelf and Emby, but uses Navidrome's Subsonic response validation.
