# Weekly stabilization and roadmap execution

## Goal

Execute the next week of Nordic development with a stability-first sequence: clean up stale task state, harden the new AudiobookShelf integration, add a minimal regression-test baseline, reduce playback orchestration risk, then start one real video-service MVP.

## What I already know

* The user asked to begin work based on the one-week direction review.
* The recently completed AudiobookShelf integration added auth, library browsing, details, playback sessions, progress sync, and a dedicated player screen.
* `MainScreen` currently owns tab state, music playback, audiobook playback, lyrics loading, and audiobook progress sync.
* `AudiobookPlaybackEngine` uses the same `MusicPlaybackService` / MediaSession service as music playback.
* `AudiobookShelfRepository` exposes `startPlayback`, `syncProgress`, and `closeSession`, but sync/close response bodies/status are not currently validated.
* `VideoScreen` still renders fixed placeholder video cards.
* There is no `app/src/test` or `app/src/androidTest` directory yet, so regression coverage is effectively absent.
* Active Trellis task `06-18-fix-audiobook-consistency` is stale because its PRD assumes AudiobookShelf is still placeholder-only. This task leaves that directory untouched and treats the consistency concern as superseded by this stabilization slice.
* The working tree contains unrelated dirty Trellis/bootstrap/agent files and `DESIGN.md`; these should not be mixed into implementation commits unless explicitly scoped.

## Assumptions

* This task should cover the first actionable slice of the week plan, not all possible future work.
* The first slice should prioritize stability and testability over new user-facing features.
* Video work should be planned but not implemented in the first slice unless AudiobookShelf stabilization finishes quickly.
* Existing music behavior must not regress while hardening AudiobookShelf.

## Requirements

* Classify and resolve project/task state enough that new work is not confused with stale `fix-audiobook-consistency`.
* Harden AudiobookShelf playback coordination:
  * starting music must close/clear any active audiobook playback session;
  * starting audiobook playback must avoid stale music/audiobook UI overlap;
  * closing audiobook playback must sync the last position and close the AudiobookShelf session predictably.
* Validate AudiobookShelf progress/session HTTP results instead of fire-and-forget calls.
* Add a minimal Android unit-test baseline for high-risk pure/repository logic.
* Keep all implementation commits scoped away from unrelated dirty bootstrap/config files.
* Limit this task to the stabilization/test slice; do not implement the first real Video MVP here.
* Preserve the existing build checks:
  * `:app:compileDebugKotlin`
  * `:app:lintDebug`
  * `:app:testDebugUnitTest`
  * `:app:assembleDebug` when playback wiring changes.

## Acceptance Criteria

* [x] Stale AudiobookShelf consistency task is either archived/replaced or explicitly left untouched with a clear reason.
* [x] AudiobookShelf `syncProgress` and `closeSession` fail visibly or predictably on non-success responses.
* [x] Music and audiobook playback transitions do not leave both players active in UI state.
* [x] Closing audiobook playback performs a final progress sync/close flow with the current absolute position.
* [x] At least one unit-test suite exists under `app/src/test`.
* [x] Tests cover at minimum URL normalization and one AudiobookShelf playback/progress mapping edge.
* [x] Compile, lint, unit test, and assemble checks pass.

## Definition of Done

* Code changes are committed in scoped work commits.
* Tests are added where practical for pure or repository-level behavior.
* `.trellis/spec/` is updated if a new playback/session convention is learned.
* The task is archived and the session journal records the work.

## Out of Scope

* Full Video MVP implementation in this first slice.
* OpenID login for AudiobookShelf.
* Audiobook bookmarks, podcasts, ebooks, or personalized shelves.
* Large Compose/ViewModel architecture migration.
* Broad cleanup of Trellis bootstrap files, `.claude/`, `.codex/`, `.agents/`, or `DESIGN.md`.

## Technical Approach

* Treat this as a stabilization sprint slice.
* Keep changes narrow and test-driven where possible:
  * repository response validation;
  * playback transition coordination;
  * low-level unit tests.
* Avoid introducing a full DI framework or navigation framework this week.
* If orchestration extraction is needed, prefer a small local coordinator over a broad app architecture rewrite.

## Decision (ADR-lite)

### Context

The app just gained a real AudiobookShelf integration, but the implementation landed before tests and before playback coordination was separated from `MainScreen`. Continuing directly into Video risks compounding untested playback/session behavior.

### Decision

Start the week with AudiobookShelf stabilization and regression coverage. Defer Video MVP implementation until the core audio session flows have a test baseline and predictable lifecycle behavior.

### Consequences

* Short-term visible feature velocity is lower.
* The app gets a safer base for further media integrations.
* Video can start later with fewer cross-player state risks.

## Implementation Plan

* PR1: Task/state cleanup and test scaffolding.
* PR2: AudiobookShelf progress/session response validation and final close/sync behavior.
* PR3: Playback transition hardening between music and audiobooks.
* PR4: Minimal unit tests for URL normalization, token/audio URL handling, progress mapping, and absolute-position behavior.
* PR5: Re-evaluate whether to start Emby or WebDAV video MVP next.

## Open Questions

* None.

## Technical Notes

* `MainScreen`: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
* Audiobook repository: `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
* Audiobook player engine: `app/src/main/java/com/nordic/mediahub/playback/AudiobookPlaybackEngine.kt`
* Video placeholder screen: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
* Audiobook integration spec: `.trellis/spec/backend/audiobookshelf-integration.md`
* User chose option 1: first implement only the stabilization/test slice. Video MVP is out of scope for this task.
