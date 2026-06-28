# Sync Emby Video Progress On Playback Close

## Goal

Keep Emby resume metadata current when a user watches video in Nordic and then closes the video player or switches to music/audiobook playback.

## What I already know

* `EmbyRepository` already reads `UserData.PlaybackPositionTicks`, `Played`, and `LastPlayedDate` into `VideoItem`.
* `VideoPlaybackEngine` resumes from `VideoItem.playbackPositionSeconds`.
* `MainActivity` stops video playback directly from the video close button and media handoff paths without reporting the current position to Emby.
* Audiobooks already have a close/sync pattern that captures current position before stopping local playback.
* Emby/Jellyfin session APIs conventionally use `POST Sessions/Playing/Progress` and `POST Sessions/Playing/Stopped` with `ItemId`, `PositionTicks`, and paused state fields.

## Requirements

* Add Emby repository/API support for reporting video playback progress and stopped position.
* Convert video position seconds to Emby ticks using the existing `10_000_000` ticks-per-second convention.
* Clamp reported positions to `0..durationSeconds` when duration is known, and to at least `0` when duration is unknown.
* Report video stopped progress before local video playback is cleared when the user closes the video player.
* Report video stopped progress before local video playback is cleared when starting music or audiobook playback while video is active.
* Do not block or reopen the video player if a background close sync fails.
* Preserve current video playback behavior when Emby config is not ready or no current video exists.

## Acceptance Criteria

* [ ] `EmbyRepository` sends `POST /Sessions/Playing/Stopped` with `X-Emby-Token` and a body containing item id and clamped position ticks.
* [ ] Progress position helpers clamp negative, over-duration, and unknown-duration positions correctly.
* [ ] Video close and media handoff paths capture position before stopping `VideoPlaybackEngine`.
* [ ] Existing video resume, browsing, and playback tests continue to pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Emby video progress sync is implemented and covered by focused repository/helper tests.
* The app shell uses the sync path for video close and handoff without surfacing disruptive background errors.
* Emby integration spec records the progress-reporting contract.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Extend `EmbyApi` and `EmbyRepository` with playback progress request models and methods. Add a small app-shell helper in `MainActivity` that snapshots `videoPlaybackState.video` and `positionSeconds`, launches the repository sync, then stops the player. Use this helper from video close plus music/audiobook handoff call sites.

## Decision (ADR-lite)

Context: Nordic already consumes Emby resume metadata, so failing to write progress back makes the continue-watching shelf stale after local viewing.

Decision: Implement stopped-position reporting as the MVP. Keep periodic in-session progress reporting out of scope for this round unless needed by tests or API behavior.

Consequences: Resume state improves for normal close/switch flows with low app-shell complexity. Unexpected process death can still lose progress until a future periodic progress task is added.

## Out of Scope

* Periodic video progress sync while playback is ongoing.
* Marking items played/unplayed through Emby user-data endpoints.
* Adding local video history persistence.
* Supporting Plex or WebDAV progress sync.
* Showing a visible error for background video progress sync failure.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/api/EmbyApi.kt`, `app/src/main/java/com/nordic/mediahub/data/EmbyRepository.kt`, `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/data/EmbyRepositoryTest.kt`, `app/src/test/java/com/nordic/mediahub/MainActivityTest.kt`.
* Relevant specs: `.trellis/spec/backend/emby-integration.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/quality-guidelines.md`.
