# Improve Music Shuffle Playback

## Goal

Improve the music player toward the Yinliu-style continuous listening reference by adding first-class shuffle playback controls to the existing Media3 queue/player flow.

## Requirements

* Add shuffle state to `MusicPlaybackState`.
* Add a `MusicPlaybackEngine.toggleShuffleMode()` command that delegates to Media3 shuffle mode.
* Keep queue state and queue index published from Media3, not from UI shadow state.
* Add a shuffle control to `MusicPlayerScreen` next to the existing repeat/queue controls.
* Wire `MainActivity` so the player UI can read and toggle shuffle state.
* Preserve existing repeat, seek, next/previous, queue sheet, and pending queue behavior.
* Add focused tests for any new pure helper logic if needed.

## Acceptance Criteria

* [ ] Player UI shows a shuffle control and marks it active when Media3 shuffle mode is enabled.
* [ ] Tapping the shuffle control toggles `MediaController.shuffleModeEnabled`.
* [ ] `MusicPlaybackState.shuffleModeEnabled` updates from Media3 listener callbacks and `publishPlayerState()`.
* [ ] Existing music queue tests still pass.
* [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Code changes are committed.
* Relevant music playback spec/quality guidance is updated if a reusable contract is introduced.
* Trellis task is archived and the session journal is recorded.

## Technical Approach

Use Media3's built-in `shuffleModeEnabled` rather than manually randomizing queue order. This keeps the queue source of truth inside the player engine and avoids UI-side queue divergence.

## Out of Scope

* Persisting shuffle preference across app launches.
* Smart/random recommendations.
* Changing Navidrome library loading or cache behavior.
