# Improve Media Reference Playback

## Goal

Improve Nordic Media Hub toward the referenced media apps: music should feel closer to a continuous, stream-first music client; audiobooks should follow the official AudiobookShelf session and progress model; video should borrow Yamby-style browsing, detail, and playback polish. This first round must produce shippable improvements without requiring more user confirmation.

## Requirements

* Keep existing Navidrome, AudiobookShelf, and Emby integrations working.
* Music: improve continuity and performance around queue/player state rather than rebuilding the entire music tab.
* Audiobook: preserve the AudiobookShelf official playback session contract, including start, progress sync, and close flows.
* Video: improve Yamby-like browsing/detail/playback behavior using existing Emby data.
* Fix bugs discovered during implementation when they are in the touched media flows.
* Avoid large unrelated rewrites and avoid changing provider contracts without tests.

## Acceptance Criteria

* [ ] Music playback/queue state remains stable when switching to audiobook or video playback.
* [ ] Audiobook playback closes with final progress sync before clearing user-visible state, or surfaces a recoverable error.
* [ ] Video browsing/detail/player changes continue to use repository-provided authenticated URLs.
* [ ] Image-heavy media lists touched in this round use stable lazy-list identity where applicable.
* [ ] Repository or playback behavior changes are covered by focused unit tests when practical.
* [ ] `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass; run lint when UI/resource scope warrants it.

## Definition of Done

* Implementation is scoped to the media flows above.
* Compile and relevant tests pass.
* Trellis quality/spec review is completed.
* Work is committed or a commit plan is presented per project workflow.
* Finish-work is run after the round, then a new round can begin automatically.

## Technical Approach

First inspect the existing Compose screens, repositories, and Media3 playback engines. Prefer small fixes that strengthen current architecture:

* playback engines own player state and cleanup;
* repositories own authenticated media URL construction;
* Compose screens render state and pass commands;
* shared media state components are used for loading, empty, and error surfaces.

## Decision (ADR-lite)

**Context**: The request spans three media domains and references external apps, but the repo already has working integrations and recent video work.

**Decision**: Treat this as iterative rounds. In round one, prioritize concrete reliability/performance/feature gaps in existing flows instead of attempting a full visual clone of any reference app.

**Consequences**: Each round can be verified and finished cleanly. Larger visual parity work remains possible in later rounds after the core player/session behavior is stable.

## Out of Scope

* Plex and WebDAV implementation unless an existing touched path requires a small fix.
* Replacing the app design system wholesale.
* Persisting full video watch history unless existing Emby data already supports it cleanly.
* Adding new third-party dependencies unless there is no local/simple alternative.

## Research References

* [`research/reference-apps.md`](research/reference-apps.md) - Reference app patterns from the provided screenshots and request.

## Technical Notes

* Repo is a single Android app under `app/` using Kotlin, Jetpack Compose, Retrofit/OkHttp, Coil, and Media3.
* Relevant specs:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/spec/backend/error-handling.md`
  * `.trellis/spec/backend/audiobookshelf-integration.md`
  * `.trellis/spec/backend/emby-integration.md`
  * `.trellis/spec/backend/logging-guidelines.md`
* Initial compile check passed with only existing unused-parameter warnings in UI helpers.
