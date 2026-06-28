# Do Not Reopen Audiobook Player After Background Close Failure

## Goal

Keep music/video handoff stable when an AudiobookShelf session close fails in the background. Starting music or video should stop audiobook playback and attempt to sync/close the ABS session, but a close failure must not reopen the audiobook player on top of the newly selected media.

## What I already know

* `MainActivity.closeAudiobookPlayback()` is used before starting music and video playback.
* It currently stops the audiobook engine immediately, then launches `syncAndCloseSession(...)`.
* If that background close fails, it sets `audiobookPlaybackError` and `showAudiobookPlayer = true`.
* `AudiobookPlayerScreen` is shown whenever `showAudiobookPlayer || audiobookPlaybackError != null`, so a background close failure can revive an empty/stopped audiobook player after music or video has started.
* Manual player close uses `closeAudiobookPlaybackAfterSync()`, which keeps the player visible on close failure so the user can retry or see the issue.

## Requirements

* Keep background handoff behavior non-blocking: when switching to music or video, stop audiobook playback and attempt `syncAndCloseSession(...)`.
* Do not reopen or show the audiobook player when that background close fails after the handoff.
* Preserve manual close behavior: if the user explicitly closes the audiobook player and close sync fails, keep/reopen the audiobook player with the error.
* Add focused unit coverage for the failure-presentation decision.
* Update the AudiobookShelf spec to distinguish manual close failures from background handoff close failures.

## Acceptance Criteria

* [ ] Background handoff close failure resolves to `showPlayer = false` and no player error presentation.
* [ ] Manual close failure resolves to `showPlayer = true` and presents the error message.
* [ ] Existing music/video handoff still calls the background close path before starting new media.
* [ ] Focused app-shell tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* App-shell close failure handling is updated.
* Unit tests cover background and manual close failure presentation.
* AudiobookShelf integration spec records the handoff rule.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Add a small pure resolver for audiobook close-failure presentation. Use it in both the background handoff close path and the manual close path so the behavior is explicit and covered by unit tests.

## Decision (ADR-lite)

Context: ABS close failures are important, but a background close failure during media switching should not steal focus from the user’s newly selected music or video.

Decision: Manual close failures remain visible. Background handoff close failures are non-presenting: the app does not reopen the stopped audiobook player for them.

Consequences: Media handoff remains stable. A future global notification/snackbar could surface background close failures without taking over the playback UI.

## Out of Scope

* Adding a global snackbar or notification system.
* Changing repository `syncAndCloseSession(...)` behavior.
* Changing playback engine stop semantics.
* Adding instrumentation tests.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/MainActivityTest.kt`.
* Relevant specs: `.trellis/spec/backend/audiobookshelf-integration.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/directory-structure.md`.
