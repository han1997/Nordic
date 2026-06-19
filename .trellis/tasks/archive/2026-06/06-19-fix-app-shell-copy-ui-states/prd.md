# Fix App shell copy and unify UI states

## Goal

Make the Android app shell and top-level media pages feel production-ready by replacing visible mojibake text/symbols with readable UI copy, and by consolidating repeated loading, error, empty, and section state surfaces into shared Compose primitives.

## What I already know

* User asked to handle App shell/page garbled text and unified UI states.
* The app is a Kotlin/Compose Android client with top-level Music, Audiobook, and Video tabs.
* `ConfigCards.kt` was cleaned in the previous Emby slice, but other screens still contain mojibake in labels, status text, and glyph buttons.
* `MainActivity.kt`, `PlaybackDock.kt`, `MusicScreenV2.kt`, `MusicPlayerScreen.kt`, `AudiobookScreen.kt`, `AudiobookPlayerScreen.kt`, and `VideoScreen.kt` all contain user-visible state/copy surfaces.
* Existing design system is product-register UI: content-first, alpha-as-depth, restrained accents, stable controls, no nested cards, no decorative motion.
* Existing app uses simple text glyphs for icons instead of an icon dependency; this slice should not add a new icon library unless necessary.
* There are unrelated dirty files in `.agents/`, `.claude/`, `.codex/`, `.opencode/`, `.trellis/` bootstrap files, `AGENTS.md`, and `DESIGN.md`; do not include them in scoped commits.

## Assumptions

* First slice should focus on top-level visible surfaces and players, not every backend exception string.
* Playback behavior, Emby repository behavior, Navidrome cache behavior, and AudiobookShelf session behavior must not change.
* Chinese UI copy is acceptable and preferred because the existing product copy is Chinese.
* Text glyph icons are acceptable if they are readable and stable; the main problem is broken mojibake, not lack of vector icons.

## Requirements

* Replace visible mojibake copy and broken glyphs in:
  * App shell / `MainActivity.kt`
  * Playback dock / bottom navigation
  * Music home/search/detail/player/queue top-level UI
  * Audiobook list/detail/player UI
  * Video browsing UI
* Introduce shared Compose primitives for repeated UI states:
  * message/error/empty state card
  * loading state card or loading rows
  * section header where useful
  * reusable compact back/action glyphs only where it reduces duplication without over-abstracting
* Keep shared UI primitives `internal` when reused across files.
* Use existing design tokens:
  * `surfaceVariant.copy(alpha = 0.72f)` for prominent empty states
  * `surfaceVariant.copy(alpha = 0.76f)` for loading states
  * error container for error states
  * pill controls and press-scale behavior where already established
* Keep state semantics consistent:
  * unconfigured -> empty/setup message
  * configured but loading -> loading state
  * configured with request failure -> error card
  * configured with no content -> empty content message
  * content plus refresh failure -> non-destructive refresh warning/error while preserving content
* Preserve existing playback and repository behavior.

## Acceptance Criteria

* [x] No visible mojibake strings remain in the main UI files touched by this slice.
* [x] Bottom navigation labels and primary player controls render as readable symbols/text.
* [x] Music, Audiobook, and Video use shared UI state surfaces for empty/loading/error states where practical.
* [x] App still compiles after copy and component extraction.
* [x] Existing unit tests still pass.
* [x] `:app:lintDebug` and `:app:assembleDebug` pass.

## Definition of Done

* Implementation is committed in scoped logical commits.
* Tests/build checks pass:
  * `:app:compileDebugKotlin`
  * `:app:testDebugUnitTest`
  * `:app:lintDebug`
  * `:app:assembleDebug`
* Specs are updated if a reusable UI-state convention is established.
* Task is archived and journal recorded after completion.

## Out of Scope

* Adding a new icon dependency.
* Rewriting full navigation architecture or introducing ViewModel/DI.
* Changing playback behavior, queue semantics, or provider API contracts.
* Fixing every backend exception message if it is not visible in normal UI flow.
* Broad cleanup of unrelated Trellis/bootstrap/agent dirty files.

## Technical Approach

1. Add a shared UI state component file under `ui/` with `internal` empty/loading/error/message primitives.
2. Replace repeated one-off state cards in Music, Audiobook, and Video with shared primitives.
3. Replace broken mojibake literals and glyphs with readable Chinese copy and stable symbols.
4. Keep layout/elevation consistent with the existing design system.
5. Run compile, unit tests, lint, and assemble.

## Decision (ADR-lite)

### Context

The app already has feature-specific state cards, but they use repeated code and several old literals are mojibake. Leaving each page to define its own loading/error/empty UI increases visual drift and makes future copy fixes repetitive.

### Decision

Use a small shared Compose primitive layer for state surfaces while keeping domain-specific cards and rows local to each screen. Do not add a new icon dependency in this slice.

### Consequences

* Top-level pages get more consistent behavior and appearance.
* Future screens can reuse the same state surfaces.
* This avoids a broad architecture rewrite while still reducing duplication.

## Open Questions

* None for this MVP.

## Technical Notes

* App shell: `app/src/main/java/com/nordic/mediahub/MainActivity.kt`
* Bottom dock: `app/src/main/java/com/nordic/mediahub/ui/PlaybackDock.kt`
* Music: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`, `MusicPlayerScreen.kt`, `MusicHomeSections.kt`, `MusicQueueSheet.kt`
* Audiobook: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`, `AudiobookPlayerScreen.kt`
* Video: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`
* Shared UI primitives: `app/src/main/java/com/nordic/mediahub/ui/AnimatedComponents.kt` and new `MediaStateComponents.kt`
* Relevant specs: `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/guides/code-reuse-thinking-guide.md`, `.trellis/spec/guides/cross-layer-thinking-guide.md`
