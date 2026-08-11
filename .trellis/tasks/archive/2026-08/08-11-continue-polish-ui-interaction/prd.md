# Continue UI And Interaction Polish

## Goal

Continue improving Nordic's media UI and interaction predictability by addressing the remaining navigation/reset findings from the previous audit, prioritizing user-visible inconsistencies without changing the app's state-driven navigation model.

## What I Already Know

* Previous task fixed Music M1/M3/M5: source-aware music detail back, music selection reconciliation after refresh, and music config reset feedback.
* Previous audit deferred Video V4/V6, Audiobook A4, and Cross-module C1.
* The app currently keeps media screens state-driven and does not use Jetpack Navigation Compose for these flows.
* Existing constraints from the prior task still apply: keep system back and visible back aligned, avoid backend/API/cache schema changes, and update `CHANGELOG.md` for user-visible behavior.

## Requirements

* Preserve current screen architecture and media service contracts.
* Implement the Option C cross-module consistency pass selected by the user.
* Fix **A4** by adding a lightweight one-shot Audiobook explanation when config changes force Detail/selection state back to Home.
* Fix **V4** by reconciling Video type-filter state after library-load success paths replace the catalog, mirroring the main refresh path.
* Fix **V6** by adding a lightweight one-shot Video explanation when config changes clear detail/search/filter state.
* Address **C1** by keeping the reset-feedback behavior consistent with the already-fixed Music pattern without introducing a broad shared component unless the existing code makes it clearly smaller.
* Add or update focused tests for pure state-resolution logic where feasible.

## Acceptance Criteria

* [ ] Audiobook config changes that visibly collapse Detail/selection state show one local explanatory message, while first launch/no-op emissions do not.
* [ ] Video library-load success paths validate `selectedTypeFilter` against the loaded catalog before the user sees the resulting grid.
* [ ] Video config changes that visibly clear detail/search/filter state show one local explanatory message, while first launch/no-op emissions do not.
* [ ] Reset feedback copy and placement are consistent with Music's existing local explanation pattern.
* [ ] Existing behavior for unrelated media screens is preserved.
* [ ] Relevant unit tests cover state-resolution helpers or regression-prone logic.
* [ ] `CHANGELOG.md` records user-visible polish.
* [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition Of Done

* Tests added/updated where appropriate.
* Lint / Kotlin compile / unit tests pass.
* Docs or specs updated if a reusable interaction contract emerges.
* Task context files curated before implementation.

## Out Of Scope

* Replacing state-driven navigation with Jetpack Navigation Compose.
* Modifying Navidrome, AudiobookShelf, or Emby API clients.
* Changing playback engines or cache schema.
* Broad visual redesign unrelated to navigation/reset polish.

## Technical Approach

Use small state-resolution helpers where behavior is pure and testable. Keep feedback local to each media screen, aligned with Music's existing compact explanation pattern, and preserve current BackHandler/page-state architecture. Do not introduce a cross-screen routing abstraction for this pass.

## Decision (ADR-lite)

**Context**: The prior navigation audit found that Music had the highest-risk issues and fixed them first. Remaining deferred issues are mostly parity and consistency gaps in Audiobook and Video.

**Decision**: Implement Option C: A4, V4, V6, and C1 in one pass.

**Consequences**: This gives consistent reset behavior across media tabs and closes the one remaining Video catalog/filter coherence gap. The task remains intentionally scoped to state polish; larger navigation architecture changes stay out of scope.

## Technical Notes

* Prior audit: `.trellis/tasks/archive/2026-08/08-11-polish-ui-interaction/research/navigation-audit.md`.
* Prior decisions: `.trellis/tasks/archive/2026-08/08-11-polish-ui-interaction/info.md`.
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md`, especially Compose BackHandler and Music config-change state reset contracts as the precedent for reset feedback.
