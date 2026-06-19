# Hide Bottom Dock While Scrolling

## Goal

When users scroll any main content page, the combined playback controls and bottom navigation dock should get out of the way so media content has more visible space. The dock should return automatically when scrolling has been idle briefly.

## Requirements

* Hide the `PolishedPlaybackDock` immediately on user-driven page scroll.
* Show the dock again after scroll input has been idle for a short, predictable delay.
* Keep the dock visible when switching tabs or returning from full-screen player states.
* Do not require changes in each individual page list unless the app shell approach cannot catch their scroll events.

## Acceptance Criteria

* [x] Music, audiobook, and video pages hide the dock during scroll.
* [x] The dock reappears automatically after scrolling stops.
* [x] Opening/closing music or audiobook player screens keeps the shell in a consistent visible state.
* [x] Kotlin compilation passes.

## Definition of Done

* Implemented with existing Compose patterns and animation timing.
* Verified with project Kotlin compile or the closest available quality gate.
* No unrelated dirty files are reverted or included.

## Technical Approach

Use a `NestedScrollConnection` at the `MainScreen` content shell to detect user scroll input from nested page lists. Hide the bottom dock on drag, keep it hidden through fling, then reveal it after a short idle delay. The dock remains hosted through `Scaffold(bottomBar)`, but the transition is wrapped in a private animation container that combines fade, bottom-anchored slide, subtle scale, and vertical size change to avoid an abrupt shell transition.

## Decision

**Context**: The dock is shared by all primary tabs and already lives at the app shell level.
**Decision**: Implement scroll-aware visibility at the app shell instead of hoisting scroll state from every tab.
**Consequences**: This keeps the change small and consistent, but it only responds to scrollables participating in Compose nested scroll, which matches the current LazyColumn-based pages.

## Out of Scope

* Redesigning the playback dock.
* Reworking page-specific scrolling layouts.
* Fixing all existing mojibake strings outside the minimum needed for this task.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`, `app/src/main/java/com/nordic/mediahub/ui/PlaybackDock.kt`.
* Product design context: content should lead and persistent chrome should recede during content browsing.
