# Improve Audiobook Chapter Navigation

## Goal

Bring the audiobook player closer to the official AudiobookShelf listening experience by making chapters actionable during playback. Users should be able to jump to previous/next chapters without leaving the full-screen player.

## Requirements

* Add chapter navigation commands to `AudiobookPlaybackEngine`.
* Previous chapter behavior:
  * if playback is more than a few seconds into the current chapter, jump to the current chapter start;
  * otherwise jump to the previous chapter start when one exists.
* Next chapter behavior jumps to the next chapter start when one exists.
* Keep all seeks in absolute audiobook seconds so progress sync remains correct.
* Add previous/next chapter buttons around the existing play/pause control.
* Keep controls disabled when no session/chapters are available.
* Add unit tests for the pure chapter navigation rules.

## Acceptance Criteria

* [ ] `AudiobookPlaybackEngine` exposes previous/next chapter commands.
* [ ] `AudiobookPlayerScreen` renders previous/next chapter controls around play/pause.
* [ ] Chapter seeks call the existing absolute-position `seekTo(...)` path.
* [ ] Unit tests cover current-chapter restart threshold, previous chapter jump, next chapter jump, and missing chapter cases.
* [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Code changes are committed.
* AudiobookShelf integration spec is updated if this adds a durable playback contract.
* Trellis task is archived and session journal is recorded.

## Technical Approach

Implement pure chapter-resolution helpers in `AudiobookPlaybackEngine.kt` and have engine commands call the existing `seekTo(positionSeconds)` method. This keeps the current absolute-position contract intact.

## Out of Scope

* Editing AudiobookShelf chapter data.
* Persisting custom bookmarks.
* Changing progress sync cadence.
