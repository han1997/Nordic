# Stabilize Pending Music Queue Starts

## Goal

Make music queue startup more reliable while the Media3 controller is still connecting, so continuous queue playback starts from the same item the UI shows.

## Requirements

* When queuing songs before the controller is connected, clamp `startIndex` once and use the same safe index for `currentSong`, `queueIndex`, and the eventual pending queue start.
* Keep pending single-song playback and pending queue playback mutually exclusive.
* Do not let a play/pause toggle while a queue is pending replace the queue with a single-song pending request.
* Preserve existing active-controller queue behavior.
* Add focused unit coverage for queue start-index resolution.

## Acceptance Criteria

* [x] Negative pending queue start index resolves to the first song.
* [x] Too-large pending queue start index resolves to the last song.
* [x] Empty queue start resolution is handled safely.
* [x] Pending `playQueue(...)` state uses the resolved start index consistently.
* [x] Pending queue requests are not overwritten by `togglePlayPause()` before controller connection.

## Definition of Done

* `:app:compileDebugKotlin` passes.
* `:app:testDebugUnitTest` passes.
* `:app:lintDebug` passes.
* Spec update only if a new queue contract is discovered; existing quality spec already covers pending queue consistency.
* Work commit is created before task archive and journal commits.

## Technical Approach

Add a small internal helper to resolve a safe queue start index for non-empty queues. Use it in `playQueue(...)`, especially the controller-unavailable branch, and make pending single-song and queue requests clear each other so the latest explicit playback request wins.

Update `togglePlayPause()` so it does not create a pending single-song request when a queue is already pending.

## Decision (ADR-lite)

**Context**: `playQueue(...)` currently stores the raw `startIndex` while the UI state clamps `queueIndex`; `currentSong` is read with the raw index. If the controller is not connected and the start index is out of bounds, the UI can show no current song or the eventual start can diverge from the visible queue index.

**Decision**: Resolve the safe queue start index before updating pending state, and keep pending single-song and queue requests mutually exclusive.

**Consequences**: Queue startup is deterministic during controller connection without changing Media3 behavior after the controller is available.

## Out of Scope

* Changing active Media3 queue mutation behavior.
* Changing shuffle/repeat semantics.
* Changing queue sheet layout or copy.
* Adding Android instrumentation tests for MediaController connection timing.

## Technical Notes

* Target code: `app/src/main/java/com/nordic/mediahub/playback/MusicPlaybackEngine.kt`.
* Target tests: `app/src/test/java/com/nordic/mediahub/playback/MusicPlaybackEngineTest.kt`.
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md` music playback queue management section already requires pending queue and state consistency before controller connection.
