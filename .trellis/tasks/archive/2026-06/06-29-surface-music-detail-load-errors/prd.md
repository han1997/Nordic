# Surface Music Detail Load Errors

## Goal

Make Navidrome music detail navigation fail visibly when album or artist detail loading fails. The app should not silently convert a network/API failure into an empty album or empty artist state.

## What I already know

* `openAlbumDetail(...)` catches exceptions from `repo.getAlbumSongs(...)` with an empty catch block.
* `openArtistDetail(...)` catches exceptions from `repo.getArtistAlbums(...)` with an empty catch block.
* Playlist detail loading already sets a contextual `errorMsg` on failure.
* Existing quality guidance prefers contextual UI errors rather than misleading empty states.

## Requirements

* Album detail loading failures must set a contextual music error message.
* Artist detail loading failures must set a contextual music error message.
* Starting album or artist detail loading should clear any previous music error message.
* Add focused tests for the error-message helpers.

## Acceptance Criteria

* [x] Album detail load failures produce a non-empty contextual error message.
* [x] Artist detail load failures produce a non-empty contextual error message.
* [x] Opening album or artist detail clears stale music errors before loading.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass.

## Definition of Done

* Tests added or updated.
* Required Gradle checks pass sequentially on Windows.
* Specs updated if a reusable music detail loading contract is added.
* Work is committed before Trellis archive and journal commits.

## Out of Scope

* New retry UI.
* Repository API changes.
* Changing playlist detail loading behavior beyond keeping it as the reference pattern.

## Technical Approach

Add small internal helper functions in `MusicScreenV2.kt` for album-detail and artist-detail load error messages. Use those helpers in the existing catch blocks, and clear `errorMsg` at the start of album/artist detail navigation.

## Decision (ADR-lite)

**Context**: Silent catch blocks hide real Navidrome failures and make the UI look like valid empty content.

**Decision**: Surface contextual errors in the same screen-level `errorMsg` used by the rest of the Music tab.

**Consequences**: The change is small and consistent with playlist detail loading. It does not add retry controls or change repository exception wrapping.

## Technical Notes

* Relevant files: `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`, `app/src/test/java/com/nordic/mediahub/ui/MusicScreenV2Test.kt`.
* Relevant specs to load before coding: backend quality guidelines, especially Navidrome detail/playback conventions.
