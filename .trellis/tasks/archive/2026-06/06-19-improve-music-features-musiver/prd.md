# Improve Music Features From Musiver

## Goal

Use the Musiver project (`https://github.com/liuyincs/musiver`) as product and implementation reference to improve the Nordic app's music section, while fitting the existing Navidrome-based Android architecture.

## What I already know

* User wants to reference Musiver and improve the music portion of this app.
* This repo is a Kotlin/Jetpack Compose Android media hub.
* Current music features are centered on Navidrome config, refresh/cache, music home, songs/artists/albums/search pages, and playback integration.
* Musiver's public repository explicitly says it does not contain app source code, so it can guide product behavior but not implementation.
* Musiver highlights complete playback, metadata browsing, artwork/lyrics/info display, multi-server/multi-route support, and a unified UX across private media services.
* Musiver `v2.0.1-beta.10` release notes emphasize lyrics search/override, multi-artist display/navigation, album sorting by added/release date, endpoint health/routing, and queue metadata improvements.

## Assumptions (temporary)

* "音流" refers to the Musiver project at `https://github.com/liuyincs/musiver`.
* The MVP should improve the existing Navidrome music experience rather than adding an unrelated music provider.
* We should prefer changes that can be verified with local unit tests and Android Gradle checks.

## Open Questions

* None.

## Requirements (evolving)

* Inspect the current Nordic music module and identify realistic feature gaps.
* Research Musiver's music features and map useful ideas to this app's constraints.
* Keep music refresh/cache behavior config-scoped and avoid credential leakage.
* Do not copy Musiver code/assets because the reference repo has no application code and no declared license metadata.
* MVP scope is album browsing and sorting inspired by Musiver's album list improvements.
* Add a first-class album library page reachable from the music tab.
* Add album sort controls that use Navidrome/Subsonic album list APIs where possible.
* Album sort MVP includes:
  * Recently added.
  * Release year/date.
  * Album name.
* Album browsing entry should be lightweight: keep the existing top tabs unchanged and expose album browsing from the home album section's "All" action.

## Acceptance Criteria (evolving)

* [ ] PRD identifies the chosen MVP music feature set.
* [ ] Requirements distinguish MVP from out-of-scope future enhancements.
* [ ] Technical approach lists affected files and verification commands.
* [ ] Music tab has an album browsing surface, not only recent album sections on the home page.
* [ ] Album sort choices are explicit and testable.
* [ ] Albums can be sorted by recently added, release year/date, and album name.
* [ ] Repository tests assert the Navidrome album list API is called with the expected `type` values for each sort mode.
* [ ] Top music tabs remain unchanged; album browsing is reached from the home album section.

## Definition of Done (team quality bar)

* Tests added/updated where behavior changes.
* Kotlin compile, unit tests, lint, and debug assemble pass as appropriate.
* Docs/spec notes updated if a reusable behavior contract changes.
* No unrelated user changes are reverted.

## Out of Scope (explicit)

* Replacing the app with Musiver.
* Copying Musiver code or assets without checking license/compatibility.
* Adding a new backend service unless selected explicitly.
* Replacing the top-level music tabs with a dedicated album tab.
* Implementing lyrics search/override.
* Implementing multi-artist navigation.
* Implementing multi-server route health or route rebinding.

## Technical Notes

* Current likely files: `MusicScreenV2.kt`, `MusicPlayerScreen.kt`, `MusicPlaybackEngine.kt`, `NavidromeRepository.kt`, `NavidromeApi.kt`, `NavidromeMusicCacheRepository.kt`.
* Existing spec layer: `.trellis/spec/backend/index.md` and music/cache guidance in `.trellis/spec/backend/database-guidelines.md`.
* Research reference to add: Musiver GitHub repository feature/architecture notes.

## Research References

* [`research/musiver-feature-reference.md`](research/musiver-feature-reference.md) - Musiver README/release-note feature map and Nordic applicability.

## Research Notes

### What Musiver suggests

* Player/library polish should focus on lyrics, metadata navigation, album sorting, server health, and queue consistency.
* Multi-server/multi-route support is a larger architecture direction, not a narrow music-screen polish task.

### Constraints from Nordic

* Nordic currently supports Navidrome music only, with separate Emby video and AudiobookShelf audiobook domains.
* Current `NavidromeSong` stores one artist string, not artist ids or multiple artists.
* Lyrics retrieval exists, but there is no user-facing lyrics search result picker or override persistence.
* `getAlbumList2` already supports different list types and paging, making album sort modes relatively low-risk.

### Feasible MVP approaches

**Approach A: Album Browsing & Sorting** (Recommended for first pass)

* Add an album library page and sort controls inspired by Musiver's added/release sorting.
* Pros: Fits existing Subsonic/Navidrome APIs, low risk, clear UI improvement.
* Cons: Less dramatic than lyrics search.

**Approach B: Lyrics Search & Manual Override**

* Add a player lyric search/replace flow and local override persistence.
* Pros: High user-visible value, aligns strongly with Musiver release notes.
* Cons: Requires new persistence behavior and careful UI/error design.

**Approach C: Multi-Artist Navigation**

* Improve song/artist metadata display and allow artist navigation from tracks/player.
* Pros: Better metadata browsing and Musiver parity.
* Cons: Current model lacks artist ids on songs; API support may vary.

## Decision (ADR-lite)

**Context**: Musiver offers several music-library improvements, but Nordic already has core playback, lyrics display, songs, artists, albums, and search. The lowest-risk improvement with clear user value is exposing albums as a first-class browsable library with sort controls.

**Decision**: Implement Approach A: Album Browsing & Sorting as the MVP.

**Consequences**: The implementation should focus on Navidrome album-list API support, UI navigation, cache/state shape, and regression tests. Lyrics search/override, multi-artist navigation, and multi-server routing remain future work.

## Technical Approach

* Add a Navidrome album sort model mapped to Subsonic/Navidrome `getAlbumList2` list types.
* Expose recently added, release year/date, and album name as the MVP sort modes.
* Add repository tests for sort-mode-to-request mapping.
* Add a dedicated album browsing UI surface that reuses existing album cards/rows and opens the existing album detail page.
* Keep the entry point on the home screen, not as a new or replacement top tab.
