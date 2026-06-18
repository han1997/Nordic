# Complete Nordic project development

## Goal

Bring Nordic Media Hub from a music-first Android client with a new AudiobookShelf integration into a more complete, stable, polished self-hosted media hub. Work must be staged into shippable slices rather than attempted as one unbounded change.

## What I already know

* User wants the whole project developed, including optimization, stabilization, new features, page debugging/fixes, and visual polish.
* Product purpose: Android client for self-hosted music (Navidrome), audiobooks (AudiobookShelf), and video (Emby/Plex/WebDAV).
* Design register is `product`: design serves media-management tasks, with consistent native controls, restrained color, predictable navigation, and content-first surfaces.
* Existing design system: "The Listening Room" with alpha-as-depth, restrained violet/cyan accents, native fonts, pill controls, no over-decorated card grids, no gratuitous motion.
* Music is the most mature domain: Navidrome repository, cache, search, lyrics, queue, player, Media3 service.
* AudiobookShelf now has real auth/library/detail/playback/session/progress flow plus repository/playback tests.
* Video is currently configuration UI plus fixed placeholder cards, not a real service integration.
* `MainScreen` still owns a lot of orchestration: tab state, music playback, audiobook playback, lyrics loading, periodic progress sync, and overlay player state.
* Current tests cover only the initial AudiobookShelf stabilization slice.
* Active stale task `06-18-fix-audiobook-consistency` remains in the tree but no longer matches current implementation.
* Working tree contains unrelated Trellis/bootstrap/agent files and `DESIGN.md`; implementation commits must not silently absorb them.

## Assumptions

* "Complete the project" means move through staged production milestones, not finish every possible media-server feature in one commit.
* The first new-feature priority should be the missing video vertical slice, because music and audiobooks already have real backend integrations.
* Optimization and polish should happen alongside feature work, but only where it directly reduces user-facing risk or enables the next slice.
* Existing music and audiobook behavior must remain green after every slice.

## Requirements (evolving)

### Cross-cutting

* Keep the app buildable after each slice.
* Preserve and extend the quality baseline:
  * `:app:compileDebugKotlin`
  * `:app:lintDebug`
  * `:app:testDebugUnitTest`
  * `:app:assembleDebug` for playback or app-shell changes.
* Avoid committing unrelated dirty files unless explicitly scoped.
* Update `.trellis/spec/` when a new integration contract or UI convention is established.

### Stability and Optimization

* Reduce `MainScreen` orchestration risk by extracting small coordinators or state holders where useful.
* Keep music/audiobook playback transitions predictable.
* Add tests for new repository mapping, URL building, and edge cases.
* Standardize error handling with typed exceptions instead of string-based classification.

### Video Feature Completion

* Replace fixed placeholder video cards with an Emby read-only MVP.
* Use the existing `VideoServerConfig` fields for Emby server URL, username/password, and optional API key.
* Show real Emby libraries/items/thumbnails for the selected provider.
* Keep playback out of scope for the first video MVP; focus on real browsing and state handling.

### UI Debugging, Fixes, and Polish

* Keep product UI familiar, dense enough for repeated use, and consistent across Music, Audiobook, and Video.
* Remove fake data once a domain has real integration.
* Improve empty, loading, error, and configured-but-empty states.
* Use existing design language: alpha surfaces, restrained accent, stable dimensions, press-scale feedback, and content-first thumbnails/covers.
* Avoid nested cards, decorative motion, glassmorphism as default, side-stripe borders, and inconsistent controls.

## Acceptance Criteria (evolving)

* [x] Emby read-only MVP replaces placeholder video cards.
* [x] Emby repository/API tests cover auth or URL/image mapping.
* [x] Music and AudiobookShelf existing checks still pass after video changes.
* [x] At least one major UI inconsistency or placeholder state is removed.
* [x] Main app orchestration is not made worse; if new cross-media state is added, it is isolated behind a small state holder or coordinator.
* [x] Compile, lint, unit tests, and assemble pass.

## Definition of Done

* Requirements for the current slice are captured in this PRD.
* Implementation is committed in scoped logical commits.
* Tests are added or updated for changed behavior.
* Lint/build/test checks pass.
* Specs are updated when contracts change.
* Task is archived and journal is recorded after the slice is done.

## Out of Scope (for the first implementation slice)

* Implementing Emby, Plex, and WebDAV all at once.
* Full account/session management for every provider.
* Full video playback.
* Large architecture migration to full ViewModel/DI/navigation frameworks.
* Broad cleanup of Trellis bootstrap files, `.claude/`, `.agents/`, `.codex/`, `.opencode/`, or `DESIGN.md`.

## Technical Approach

This should become a milestone series:

1. **Video MVP slice**: implement Emby read-only browsing, add repository/API layer, replace placeholder cards with real data, add tests, polish loading/error/empty states.
2. **App-shell simplification slice**: extract playback/app coordination from `MainScreen` only where it is actively blocking reliability.
3. **UI polish slice**: align Music, Audiobook, and Video surfaces around shared config cards, action groups, empty states, loading states, and media card geometry.
4. **Provider expansion slice**: after the first video provider works, add the next provider using the same contract and test pattern.

## Feasible First-Slice Options

### Option A: Emby Read-Only MVP (Selected)

* Add Emby API/repository support using the existing `serverUrl + username/password + apiKey` config model.
* Load real libraries and video items.
* Render real titles, durations, and thumbnails.
* Defer full playback until browsing and auth are stable.
* Best match for the current README promise and familiar media-server model.

### Option B: WebDAV File Browser MVP

* Treat WebDAV as file browsing first.
* Show directories and playable media files.
* Easier direct playback path, weaker metadata/thumbnails.
* Good if local-file-like access matters more than media-library polish.

### Option C: UI Polish First

* Do not add a backend provider yet.
* Remove placeholders, improve empty states, align screen polish, and reduce app-shell complexity.
* Lower integration risk, but does not materially complete the missing Video feature.

## Decision (ADR-lite)

### Context

Video is the least complete media domain. Music and AudiobookShelf already have real repositories and playback flows, while `VideoScreen` still renders fixed placeholder cards.

### Decision

Implement Emby read-only browsing as the first project-completion slice. Use the existing video config model, add an Emby API/repository layer, and replace placeholder video cards with real library/item data. Defer playback until browsing/auth/error states are stable.

### Consequences

* The app gains a real third media-domain integration without expanding into all video providers at once.
* The first slice remains testable and bounded.
* Playback, Plex, and WebDAV can reuse the provider contract later.

## Open Questions

* None.

## Technical Notes

* App shell: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
* Video placeholder: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
* Config model: `app/src/main/java/com/nordic/mediahub/data/ServerConfig.kt`
* Config persistence: `app/src/main/java/com/nordic/mediahub/data/ConfigRepository.kt`
* Music repository pattern: `app/src/main/java/com/nordic/mediahub/data/NavidromeRepository.kt`
* Audiobook repository pattern: `app/src/main/java/com/nordic/mediahub/data/AudiobookShelfRepository.kt`
* Existing tests: `app/src/test/java/com/nordic/mediahub/`
* Design context: `PRODUCT.md`, `DESIGN.md`, impeccable product register.
* User selected option 1: Emby read-only MVP.
