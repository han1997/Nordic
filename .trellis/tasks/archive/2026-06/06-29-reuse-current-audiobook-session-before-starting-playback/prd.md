# Reuse Current Audiobook Session Before Starting Playback

## Goal

Prevent duplicate or leaked AudiobookShelf playback sessions when the user starts audiobook playback while another audiobook session is already active.

## What I already know

* `MainActivity` starts audiobook playback through `audiobookRepository?.startPlayback(item.id)`.
* Starting music or video calls `closeAudiobookPlayback()` first, which snapshots the active session, stops local playback, and syncs/closes the AudiobookShelf session in the background.
* Starting another audiobook currently does not close the previous audiobook session before calling `/play` for the new item.
* Tapping the same audiobook while its session is already active can create a new playback session instead of reopening the existing player.
* `.trellis/spec/backend/audiobookshelf-integration.md` says ABS playback must use official `/play`, `/sync`, and `/close` session flows.

## Requirements

* If the requested audiobook is already the active session, reuse the current session and show the audiobook player instead of calling `startPlayback(...)` again.
* If a different audiobook session is active, close/sync the current session using the existing background close path before starting the new item.
* Preserve existing behavior when no audiobook session is active: call `startPlayback(item.id)` and play the returned session.
* Preserve background close failure behavior: a handoff close failure must not reopen the stopped player or block the new playback attempt.
* Keep music/video handoff behavior intact when starting audiobook playback.

## Acceptance Criteria

* [ ] A pure helper resolves same-item requests to current-session reuse.
* [ ] A pure helper resolves different active-session requests to close-before-start.
* [ ] `onPlayAudiobook` reuses the current session without making a new `/play` request when the item id matches.
* [ ] `onPlayAudiobook` closes the previous active session before starting a different audiobook.
* [ ] Focused `MainActivityTest` coverage passes.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Audiobook session start behavior is updated and covered by focused tests.
* AudiobookShelf integration spec records same-session reuse and different-session close-before-start behavior.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Add an internal app-shell helper that maps `(currentSession, requestedLibraryItemId)` to one of three actions: start a new session, reuse the current session, or close the current session before starting another. Use it in `MainActivity` before launching the `startPlayback(...)` request.

## Decision (ADR-lite)

Context: AudiobookShelf playback sessions are server-side resources. Starting a second session without closing the first can leave progress stale or sessions open.

Decision: Reuse the active session for same-item requests and use the existing background close/sync path before starting a different audiobook.

Consequences: Same-book taps become fast and idempotent. Different-book starts keep momentum while still closing the previous ABS session in the background.

## Out of Scope

* Blocking new audiobook start until previous session close succeeds.
* Adding a visible error for background close failure during audiobook-to-audiobook handoff.
* Changing AudiobookShelf repository endpoints.
* Changing audiobook list/detail UI.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/MainActivityTest.kt`.
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`.
