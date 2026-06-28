# Reference App Notes

## Music: Yinliu-style Continuous Music

Observed from provided screenshots:

* Top-level music surfaces emphasize quick switching between discovery, artists, and albums.
* Horizontal shelves and grid cards put album art first, with title/artist copy below.
* A persistent mini player and separate bottom navigation keep playback reachable while browsing.
* Album browsing exposes quick filter/sort chips through compact icon controls.

Implications for this app:

* Preserve the existing bottom dock/player architecture.
* Prefer queue continuity and stable player state over adding decorative surfaces.
* When touching image-heavy shelves, use stable keys/content types and remembered slices.

## Audiobook: AudiobookShelf Official Model

Existing project spec defines the official contract:

* Start playback with `/api/items/{id}/play`.
* Play the returned session tracks, not arbitrary item URLs.
* Sync progress with absolute audiobook time.
* Close the session when leaving playback.

Implications for this app:

* Do not bypass `AudiobookShelfRepository.startPlayback`.
* Avoid clearing the visible session before final close succeeds if that would hide recoverable errors.
* Keep music/video playback stopped when audiobook playback starts.

## Video: Yamby-style Browsing and Player

Observed from provided screenshots:

* Home surfaces are grouped into purposeful sections such as continue watching, highest rated, and unplayed.
* Series/detail pages show poster, season/episode metadata, overview, and episode rows with thumbnail play affordances.
* Player is immersive, black-backed, and keeps controls close to the media.

Implications for this app:

* Use existing Emby catalog/item data first.
* Keep authenticated stream and image URLs inside `EmbyRepository`.
* Prioritize detail and playback polish without introducing local watch-history persistence in round one.
