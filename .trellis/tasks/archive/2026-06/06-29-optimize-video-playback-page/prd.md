# Optimize Video Playback Page

## Goal

Polish the in-app video playback page toward a Yamby-like, media-first experience: immersive black-backed playback, lighter chrome, clearer controls, and better use of Nordic's existing product design language.

## What I Already Know

* User requested `$impeccable 视频播放页面优化，参考yamby`.
* This is a product UI task for the Android app, not a marketing/brand surface.
* Impeccable context says Nordic should be content-first, lightweight, modern, and avoid over-decorated cards, default Material flatness, and dark "tech tool" styling.
* Existing video playback is owned by `VideoPlaybackEngine` and rendered by `VideoPlayerScreen`.
* `MainActivity` shows `VideoPlayerScreen` as a full-screen overlay when video playback is active.
* Current `VideoPlayerScreen` is functional but basic: black root, top title/subtitle/close, centered `SurfaceView`, bottom scrubber and play/skip controls.
* Existing Yamby/Emby research emphasizes immersive player polish, practical controls, minimal chrome, and controls close to the media.

## Assumptions

* "参考 Yamby" means matching the feel and interaction priorities of a mature media client, not cloning exact visuals.
* MVP should improve the playback page only; browsing grid/detail behavior stays out of scope unless needed for playback context.
* Keep current Media3 playback architecture and `VideoPlaybackState` contract unless a small UI helper is needed.
* Do not reintroduce old local player features that were removed during the remote sync unless they fit the current remote architecture.

## Open Questions

* None.

## Requirements (Evolving)

* Preserve existing video playback behavior: surface attach/detach, play/pause, scrubber seek, 10s back, 30s forward, close, buffering/error copy.
* Make the player feel more immersive and media-first.
* Keep controls readable without permanently covering too much video.
* Use Nordic product design tokens/patterns: tinted surfaces, restrained accent, pill controls, press-scale feedback, no nested cards.
* Keep text within bounds on phone-sized screens.
* Avoid adding new server/API requirements.

## Acceptance Criteria (Evolving)

* [ ] Video playback remains full-screen and works through the existing `VideoPlaybackEngine`.
* [ ] Controls are visually clearer and closer to a polished media client.
* [ ] Timeline, time labels, play/pause, skip back/forward, and close remain available.
* [ ] Empty/no-video, buffering, and error states remain understandable.
* [ ] `:app:compileDebugKotlin` passes.
* [ ] `:app:testDebugUnitTest` passes.
* [ ] `:app:lintDebug` passes.
* [ ] `:app:assembleDebug` passes because playback UI is touched.

## Definition of Done

* Requirements confirmed.
* Code implemented in the existing Compose/Media3 architecture.
* Relevant helper tests added or updated if pure UI logic changes.
* Gradle verification gates pass sequentially on Windows.
* Work committed and task archived.

## Research References

* `.trellis/tasks/archive/2026-06/06-26-media-feature-parity-round4/research/video-emby-yamby.md` — Yamby/Emby clients prioritize practical player controls, subtitle/audio controls, gestures, PIP, and media-first playback.
* `.trellis/tasks/archive/2026-06/06-28-improve-media-reference-playback/research/reference-apps.md` — Yamby-style player is immersive, black-backed, and keeps controls close to the media.
* `.trellis/tasks/archive/2026-06/06-28-improve-yamby-style-video-features/prd.md` — Previous Yamby work already added/targeted browse/detail polish; this task should focus on the playback page.

## Feasible Approaches

**Approach A: Visual Player Polish (Recommended)**

* Make the existing controls feel intentional: unobtrusive top overlay, bottom control shelf over the video, stronger timeline readability, icon-like controls, better loading/error treatment.
* Pros: high UX value, low architecture risk, fits current remote playback API.
* Cons: does not add new playback capabilities.

**Approach B: Interaction Polish**

* Add tap-to-show/hide controls and double-tap play/pause or seek gestures.
* Pros: more player-like and closer to mobile media conventions.
* Cons: higher interaction/test risk; gesture conflicts can make controls harder to use.

**Approach C: Feature Controls**

* Add speed, audio/subtitle, or next episode controls.
* Pros: closer to Yamby/Emby power-user parity.
* Cons: current remote playback architecture lacks track-selection surface; this is broader than a page polish task.

## Decision (ADR-lite)

**Context**: The user wants the video playback page optimized with Yamby as the reference. The current player works but feels basic and should improve without destabilizing the just-synced playback architecture.

**Decision**: Use Approach A, Visual Player Polish. Optimize the current `VideoPlayerScreen` layout, overlay treatment, controls, timeline, and player states while preserving existing playback behavior and callbacks.

**Consequences**: This round improves perceived quality and media-client ergonomics with low architecture risk. Gesture controls, track/subtitle controls, PIP, and next-episode logic remain future work.

## Technical Notes

* Primary target: `app/src/main/java/com/nordic/mediahub/ui/VideoPlayerScreen.kt`.
* Wiring target if needed: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`.
* Existing pure helpers: `resolveVideoPlayerTimeline(...)`, `formatVideoPlayerDurationLabel(...)`.
* Existing tests likely relevant: `app/src/test/java/com/nordic/mediahub/ui/VideoPlayerScreenTest.kt` and `app/src/test/java/com/nordic/mediahub/playback/VideoPlaybackEngineTest.kt`.

## Out of Scope

* Replacing Media3/ExoPlayer.
* Adding a new video provider.
* Rebuilding the video browse/detail page.
* Adding server-side transcoding, track selection, PIP, or next-episode queue unless explicitly selected.
