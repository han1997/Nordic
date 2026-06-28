# Clear Stale Music Detail State On Config Change

## Goal

Keep Navidrome music navigation consistent when the saved music server config changes. If the user switches server/account while already connected, album, artist, playlist, and detail-song state from the previous config should not remain visible under the new config.

## What I already know

* `MusicScreenV2` resets some state in `LaunchedEffect(savedConfig)`, including sorted albums, playlists, playlist songs, selected playlist, search state, and album sort.
* The same effect does not clear `selectedAlbum`, `albumDetailSongs`, `selectedArtist`, `artistAlbums`, or the current `libraryPage` when the new config is also ready.
* This can leave stale detail pages from a previous Navidrome server/account while fresh data is loading for the new config.
* Existing quality guidance treats cached/list state carefully and avoids showing stale data under changed semantics.

## Requirements

* When `savedConfig` changes, reset Music detail navigation to the home page before applying cached/fresh data.
* Clear selected album, selected artist, selected playlist, album detail songs, artist albums, playlist songs, sorted albums, playlists, search result/error/loading state, and pending search job.
* Preserve existing behavior for not-ready configs, including clearing library content and errors.
* Add focused helper tests for the detail-state reset decision.

## Acceptance Criteria

* [x] A config change resets detail pages to `Home`.
* [x] A config change clears album, artist, and playlist detail selections/lists.
* [x] Existing not-ready config behavior still clears music content.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Tests added or updated.
* Required Gradle checks pass sequentially on Windows.
* Quality spec updated if a reusable music config-change navigation contract is added.
* Work is committed before Trellis archive and journal commits.

## Out of Scope

* Changing Navidrome cache schema.
* Persisting navigation state across accounts.
* Adding UI prompts for config changes.

## Technical Approach

Make `MusicLibraryPage` internally visible for focused tests, add a small helper that resolves the music page after config changes, and use a single reset block in `LaunchedEffect(savedConfig)` to clear detail/navigation state for both ready and not-ready configs.

## Decision (ADR-lite)

**Context**: Detail state belongs to a specific Navidrome account/catalog. Keeping it while switching config can show stale album or artist information under the wrong account.

**Decision**: Treat saved-config changes as navigation-boundary changes and return the Music tab to Home while clearing detail selections/lists.

**Consequences**: The app favors correctness over preserving deep navigation across accounts. Cached/fresh home content still loads normally for the new ready config.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`, `app/src/test/java/com/nordic/mediahub/ui/MusicScreenV2Test.kt`.
* Relevant spec: `.trellis/spec/backend/quality-guidelines.md`.
