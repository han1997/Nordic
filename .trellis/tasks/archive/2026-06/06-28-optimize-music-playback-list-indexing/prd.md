# Optimize Music Playback List Indexing

## Goal

Improve music playback reliability and list performance by replacing `indexOf(song)` click resolution with indexed lazy-list rendering. This moves the app closer to a continuous music client where tapping any list row starts exactly the intended queue item.

## Requirements

* Replace playback click paths in `MusicScreenV2` that compute indexes with `list.indexOf(song)`.
* Use `itemsIndexed` where click handling needs a song index.
* Use position-aware keys for lists that may contain duplicate song ids, especially playlist/search/detail queues.
* Preserve existing UI appearance and navigation behavior.
* Avoid changing repository/cache contracts.

## Acceptance Criteria

* [ ] `MusicScreenV2.kt` no longer uses `indexOf(song)` for playback callbacks.
* [ ] Songs page, album detail, search songs, and playlist detail pass the clicked item index directly from `itemsIndexed`.
* [ ] Keys for position-sensitive playback lists include index plus song identity.
* [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Code changes are committed.
* Spec updates are made if a durable list-indexing contract is added.
* Trellis task is archived and journal is recorded.

## Technical Approach

This is a UI-only optimization. Keep queue construction in the existing `onSongSelected(list, index)` path and update only the lazy-list rendering sites that need an index.

## Out of Scope

* Repository changes.
* Queue engine changes.
* Visual redesign.
