# Improve Emby Series Episode Detail

## Goal

Make the video flow closer to the Yamby reference by treating Emby series as browsable containers and episodes as the playable units. A series detail page should show related episodes with metadata and let the user play an episode directly.

## Requirements

* Extend Emby item mapping with episode relationship metadata when available.
* Do not expose direct stream URLs for `Series` items.
* Keep direct playback for `Movie`, `Episode`, and `Video` items through repository-built authenticated URLs.
* In `VideoDetailScreen`, show related episodes for a selected series when they are present in the loaded library list.
* Keep existing search, filters, spotlight shelves, and item grid working.
* Add focused repository tests for series stream suppression and episode metadata mapping.

## Acceptance Criteria

* [ ] Series detail pages are not presented with a primary play action unless a playable URL exists.
* [ ] Series detail pages show episode rows when matching episode items are loaded.
* [ ] Episode rows show season/episode metadata when Emby provides it.
* [ ] Tapping an episode row starts playback for that episode.
* [ ] Repository tests assert series stream URLs are null and episode relationship fields map correctly.
* [ ] Compile, unit tests, and lint pass.

## Definition of Done

* Code changes are committed.
* Relevant Emby spec updates are captured.
* Trellis task is archived and the session journal is recorded.

## Technical Approach

Use existing loaded `videos` as the source for related episode rows. Avoid adding new detail endpoints in this round. Extend `EmbyItemDto`/`VideoItem` with optional `SeriesId`, `SeriesName`, `ParentIndexNumber`, and `IndexNumber` fields, then filter episodes in Compose by `seriesId == selectedSeries.id` or fallback `seriesName == selectedSeries.title`.

## Out of Scope

* Fetching seasons/episodes lazily from additional Emby endpoints.
* Local watch history persistence.
* Plex/WebDAV behavior.
