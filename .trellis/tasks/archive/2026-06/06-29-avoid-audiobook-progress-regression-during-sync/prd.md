# Avoid Audiobook Progress Regression During Sync

## Goal

Prevent AudiobookShelf progress from moving backward when local Media3 state briefly reports a lower position than the resumed audiobook session baseline.

## What I Already Know

* `resolveAudiobookProgressSyncBaselineSeconds` initializes the audiobook sync baseline from playback state, session start time, and session current time.
* The periodic audiobook sync loop currently sends `currentState.positionSeconds` directly, so an early or stale zero-position player state can send `currentTime=0` even when the baseline is a resumed position.
* The video periodic sync loop already reports `maxOf(currentState.positionSeconds, lastSyncedPosition)` to avoid this regression.
* Manual audiobook close flows also snapshot raw playback state position before syncing and closing the session.

## Requirements

* Periodic audiobook sync must report at least the last successfully synced baseline.
* Periodic audiobook sync must advance `lastSyncedPosition` only after a successful sync, preserving retry behavior.
* Manual and background audiobook close flows must use the resume-aware baseline when a session exists.
* Add focused unit coverage for monotonic periodic sync position resolution.

## Acceptance Criteria

* [x] When state position is `0` and last synced baseline is `120`, the periodic sync position resolves to `120`.
* [x] When state position is ahead of baseline, the periodic sync position resolves to state position.
* [x] Negative values resolve to `0`.
* [x] Close flows no longer pass a raw lower playback position when session resume/current time is higher.

## Definition of Done

* Tests added or updated where appropriate.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* AudiobookShelf spec updated if this clarifies the progress-sync contract.

## Technical Approach

Add a pure app-shell helper for monotonic periodic audiobook sync positions, use it in the periodic loop, and use `resolveAudiobookProgressSyncBaselineSeconds` in close flows when a session is available.

## Decision (ADR-lite)

**Context**: AudiobookShelf resume state is remote state. Sending a lower `currentTime` during startup or close can regress the user's saved progress.

**Decision**: Treat the initialized sync baseline as a lower bound for later sync/close positions until the local player reports a higher absolute position.

**Consequences**: Resume metadata remains monotonic during local startup lag, while legitimate forward progress still syncs normally.

## Out of Scope

* Changing AudiobookShelf repository payload shape.
* Changing video progress behavior.
* Adding a persistent local progress store.

## Technical Notes

* Relevant implementation: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
* Relevant tests: `app/src/test/java/com/nordic/mediahub/MainActivityTest.kt`
* Relevant spec: `.trellis/spec/backend/audiobookshelf-integration.md`
