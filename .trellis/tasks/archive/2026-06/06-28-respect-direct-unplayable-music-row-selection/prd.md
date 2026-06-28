# Respect Direct Unplayable Music Row Selection

## Goal

Make direct music row selection behave like an explicit attempt to play the clicked song. If the clicked row has no stream URL, the app should surface that song error instead of silently skipping to the next playable track in the surrounding queue.

## What I already know

* `MusicScreenV2` uses a single `onSongSelected(list, index)` callback for both direct row clicks and bulk play actions.
* `MainActivity` routes that callback to `MusicPlaybackEngine.playQueue(...)`.
* `resolvePlayableMusicQueue(...)` currently maps an unplayable requested start index to the next playable song, or a previous playable fallback.
* That fallback is useful for bulk play, but conflicts with the quality spec: direct song-row clicks should attempt the clicked row so the playback engine can surface the specific song error.

## Requirements

* Preserve bulk play behavior: play-all actions still start at the first playable song and queues still filter unplayable entries.
* Add an explicit direct-selection mode for music row/card clicks where an unplayable requested start is not remapped to another song.
* Surface a contextual unplayable-song error for direct selection of a missing/blank `streamUrl`.
* Preserve queue playback for direct clicks on playable songs.
* Add focused tests for playable queue resolution and first-playable/bulk behavior.

## Acceptance Criteria

* [ ] `resolvePlayableMusicQueue(..., allowUnplayableStartFallback = false)` returns no queue when the requested start song is unplayable even if later songs are playable.
* [ ] Default/bulk queue resolution still maps an unplayable start to the next playable song.
* [ ] Direct song/card click call sites pass strict direct-selection mode.
* [ ] Bulk play-all call sites keep fallback-friendly mode.
* [ ] Focused music playback/UI tests pass.
* [ ] Kotlin compile, unit tests, and Android lint pass sequentially.

## Definition of Done

* Music playback engine supports explicit fallback policy.
* Music UI distinguishes direct selection from bulk play when invoking the app-shell callback.
* Quality spec records the direct-vs-bulk fallback distinction.
* Work is committed, task is archived, and the session journal is recorded.

## Technical Approach

Extend the existing callback and queue resolver with a boolean fallback policy rather than adding a parallel playback path. This keeps queue filtering centralized in `MusicPlaybackEngine` while giving UI call sites enough intent to preserve direct-click semantics.

## Decision (ADR-lite)

Context: Bulk playback should be resilient when list metadata includes unavailable tracks, but direct row selection is a precise user action.

Decision: Keep fallback enabled by default for bulk queue resolution and disable fallback for direct row/card clicks.

Consequences: Play-all remains forgiving, while tapping an unavailable track no longer starts a different song unexpectedly.

## Out of Scope

* Adding per-row disabled states for missing stream URLs.
* Changing repository stream URL mapping.
* Changing Media3 service architecture.
* Adding instrumentation tests.

## Technical Notes

* Relevant code: `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`, `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`, `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Relevant tests: `app/src/test/java/com/nordic/mediahub/playback/MusicPlaybackEngineTest.kt`, `app/src/test/java/com/nordic/mediahub/ui/MusicScreenV2Test.kt`.
* Relevant specs: `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/backend/directory-structure.md`.
