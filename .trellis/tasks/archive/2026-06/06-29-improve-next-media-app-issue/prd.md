# Reset Stale Audiobook State On Config Change

## Goal

Keep AudiobookShelf browsing consistent when the saved audiobook server config changes. If the user switches server/account while already connected, libraries, item lists, and item detail state from the previous config should not remain visible or be reused under the new config.

## What I already know

* `AudiobookScreen` already reconciles selected library and selected item after a normal refresh.
* `LaunchedEffect(savedConfig)` only copies `savedConfig` into the editable form state.
* The second config effect refreshes ready configs, but it only clears library/item/detail state when the new config is not ready.
* A ready-to-ready config change can therefore show old AudiobookShelf content while the new account refresh is loading.
* If a new server uses the same item id, `resolveAudiobookSelectedItemAfterLibraryRefresh(...)` can preserve the previous server's full detail object instead of forcing a fresh detail selection.
* Existing tests already cover audiobook library selection, detail reconciliation, and page resolution helpers.

## Requirements

* Treat saved AudiobookShelf config changes as browsing boundaries.
* When `savedConfig` changes, reset `libraryPage` to `Home` before loading the new account.
* Clear `libraries`, `selectedLibraryId`, `items`, `selectedItem`, and stale errors for both ready and not-ready new configs.
* Prevent in-flight library refresh, library selection, or detail loads from the previous config from writing stale data after the config boundary.
* Preserve existing manual refresh behavior within the same config: normal refreshes may continue reconciling the selected library/item.
* Add focused helper tests for the config-change page reset decision.

## Acceptance Criteria

* [x] A saved AudiobookShelf config change returns the audiobook browser to `Home`.
* [x] A saved AudiobookShelf config change clears libraries, item lists, selected library, and selected detail item before loading the new config.
* [x] Stale async responses from the previous config cannot repopulate audiobook list/detail state after a config change.
* [x] Existing not-ready config behavior still clears audiobook content and errors.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Tests added or updated.
* Required Gradle checks pass sequentially on Windows.
* Quality or AudiobookShelf spec updated if a reusable config-change contract is added.
* Work is committed before Trellis archive and journal commits.

## Out of Scope

* Changing AudiobookShelf auth or repository endpoints.
* Persisting deep audiobook navigation across accounts.
* Redesigning the audiobook browsing UI.
* Changing playback session handoff behavior.

## Technical Approach

Add a small `resolveAudiobookLibraryPageAfterConfigChange(...)` helper returning `Home`, add config-boundary state reset/versioning inside `AudiobookScreen`, and guard async refresh/detail/list writes with the captured version. Keep existing same-config refresh reconciliation helpers intact.

## Decision (ADR-lite)

**Context**: Audiobook library and detail state belongs to a specific AudiobookShelf account/catalog. Preserving it across saved config changes can show old account content under a new account and can retain stale details when ids collide.

**Decision**: Treat saved AudiobookShelf config changes as state boundaries, return to Home, clear account-scoped browsing/detail state, and ignore stale async writes from the previous config.

**Consequences**: The app favors correctness over preserving deep audiobook navigation across accounts. Manual refreshes within the same config still preserve selection when the item remains in the refreshed library.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`, `app/src/test/java/com/nordic/mediahub/ui/AudiobookScreenTest.kt`.
* Relevant specs: `.trellis/spec/backend/audiobookshelf-integration.md`, `.trellis/spec/backend/quality-guidelines.md`.
