# Reset Stale Video State On Config Change

## Goal

Keep the video browser consistent when the saved video server config changes. If the user switches Emby server/account while already connected, libraries, catalog items, selected detail video, search text, and type filter state from the previous config should not remain visible or be reused under the new config.

## What I already know

* `VideoScreen` refreshes the catalog when `savedConfig` changes and clears state only when the new config is not ready.
* Ready-to-ready config changes currently call `refreshVideo(savedConfig, selectedLibraryId)` while old `videos`, `selectedVideo`, `searchQuery`, and type filter state can remain visible during loading.
* Passing the old `selectedLibraryId` into the new repository can request a stale library id from the new server/account.
* Existing helper tests cover selected video reconciliation after catalog refresh, stale type filter reset after refresh, continue-watching shelf filtering, and search matching.
* Music and Audiobook now treat saved config changes as account/catalog boundaries.

## Requirements

* Treat saved video config changes as catalog boundaries.
* When `savedConfig` changes, clear `libraries`, `selectedLibraryId`, `videos`, `selectedVideo`, `searchQuery`, and reset `selectedTypeFilter` to `All` before loading the new config.
* For ready saved configs, refresh the new catalog without passing a stale previous-server library id.
* Prevent in-flight catalog refresh or library-selection responses from the previous config from writing stale data after the config boundary.
* Preserve normal same-config refresh behavior, including selected-video reconciliation and type-filter reconciliation within the same catalog.
* Add focused helper tests for the config-change filter/detail reset decisions where practical.

## Acceptance Criteria

* [x] A saved video config change clears selected video detail state before loading the new config.
* [x] A saved video config change clears libraries, selected library, videos, search text, and resets type filter to `All`.
* [x] Ready config refresh after a saved config change does not use the previous config's selected library id.
* [x] Stale async responses from the previous config cannot repopulate video catalog state after a config change.
* [x] Existing not-ready config behavior still clears video content and errors.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Tests added or updated.
* Required Gradle checks pass sequentially on Windows.
* Emby/video spec updated if a reusable config-change contract is added.
* Work is committed before Trellis archive and journal commits.

## Out of Scope

* Changing Emby auth or repository endpoints.
* Persisting deep video navigation across accounts.
* Redesigning the video browser UI.
* Changing video playback engine behavior.

## Technical Approach

Add small helper(s) for config-change reset decisions, add a config-boundary state reset/version inside `VideoScreen`, refresh ready saved configs with no previous library id, and guard async catalog/list writes with the captured version. Keep existing same-config refresh helpers intact.

## Decision (ADR-lite)

**Context**: Video catalog and detail state belongs to a specific server/account/library. Preserving it across saved config changes can show stale posters/details under the wrong account and can request stale library ids from a new server.

**Decision**: Treat saved video config changes as catalog boundaries: clear browser/detail/filter/search state, load the new config from its default catalog selection, and ignore stale async writes from the previous config.

**Consequences**: The app favors correctness over preserving deep video navigation across accounts. Manual refreshes within the same config still reconcile selected detail and type filters against the refreshed catalog.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/ui/VideoScreen.kt`, `app/src/test/java/com/nordic/mediahub/ui/VideoScreenTest.kt`.
* Relevant specs: `.trellis/spec/backend/emby-integration.md`, `.trellis/spec/backend/quality-guidelines.md`.
