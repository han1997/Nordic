# Reset Stale Video Type Filter After Refresh

## Goal

Keep Emby video browsing filters coherent after catalog refreshes. If the user has selected a type filter such as Episodes and the refreshed catalog no longer contains that type, the UI should reset to All instead of leaving an invisible active filter that produces an empty-looking library.

## What I already know

* `VideoScreen` builds visible type filters from the current `videos` list.
* `selectedTypeFilter` is preserved across normal catalog refreshes.
* If the selected filter becomes unavailable after refresh, its chip is no longer visible but it can still filter `visibleVideos`.
* Library switching already resets the type filter to All; catalog refresh should reconcile the preserved filter.

## Requirements

* Add a small helper that keeps a selected video type filter only when it is All or still matches at least one refreshed video.
* Use the helper after Emby catalog refreshes update the video list.
* Add focused unit tests covering kept, reset, and All-filter behavior.

## Acceptance Criteria

* [x] Refresh keeps an Episodes filter when the refreshed catalog still contains episodes.
* [x] Refresh resets an Episodes filter to All when the refreshed catalog has no episodes.
* [x] Refresh keeps All selected even for an empty catalog.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Tests added or updated.
* Required Gradle checks pass sequentially on Windows.
* Emby integration spec updated for the filter-refresh contract.
* Work is committed before Trellis archive and journal commits.

## Out of Scope

* New video filter UI.
* Server-side filtering or search.
* Changes to library-switch behavior, which already resets filters.

## Technical Approach

Make `VideoTypeFilter` internally visible for focused tests, add `resolveVideoTypeFilterAfterCatalogRefresh(...)` near the other video UI helpers, and call it from `refreshVideo(...)` after loading `catalog.items`.

## Decision (ADR-lite)

**Context**: A hidden active filter after refresh makes a non-empty library appear empty and leaves the user with no selected chip explaining why.

**Decision**: Preserve filters only when valid for the refreshed catalog, otherwise reset to All.

**Consequences**: Refresh remains predictable and local to loaded catalog state. It does not add persistent filter preferences.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`, `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`.
* Relevant spec: `.trellis/spec/backend/emby-integration.md`.
