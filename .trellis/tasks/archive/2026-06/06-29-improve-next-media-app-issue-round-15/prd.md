# Improve Next Media App Issue Round 15

## Goal

Continue improving the Nordic Android media app in small, verifiable rounds, using 音流 as music inspiration, official AudiobookShelf behavior for audiobooks, and Yamby as video inspiration.

## What I Already Know

* The standing goal is to keep improving features, performance, and bugs without stopping for confirmation between rounds.
* Recent rounds improved stream URL refresh behavior and AudiobookShelf URL/library edge cases.
* The repository is a single Kotlin/Jetpack Compose Android app with backend guidance under `.trellis/spec/backend/`.
* `NavidromeRepository` converts non-null `coverArt` values into authenticated `getCoverArt.view` URLs for albums, playlists, and songs.
* Empty or blank `coverArt` ids should not become broken `getCoverArt.view?id=` URLs.

## Assumptions

* This round should stay narrow enough to implement, test, verify, commit, archive, journal, and continue automatically.
* The concrete issue should be selected from repository inspection rather than user clarification.

## Requirements

* Treat null, empty, and blank Navidrome cover-art ids as absent.
* Keep non-blank cover-art ids converted into authenticated `getCoverArt.view` URLs.
* Apply the behavior consistently to album summaries, playlist summaries/details, and song mappings with fallback cover art.
* Add focused repository tests for blank cover-art ids.

## Acceptance Criteria

* [x] The selected issue is documented in this PRD before implementation.
* [x] Blank album `coverArt` values map to `null`, not `getCoverArt.view?id=`.
* [x] Blank playlist `coverArt` fallback values do not create song cover URLs.
* [x] Non-blank cover-art ids still map to authenticated cover-art URLs.
* [x] Focused tests cover the mapping decision.
* [x] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

* Tests added or updated where appropriate.
* Lint, typecheck, and unit tests are green.
* Specs are updated if the round produces reusable project knowledge.
* Work is committed before task archive and journal commits.

## Out of Scope

* Large UI redesigns.
* New server integrations.
* Dependency upgrades unless required by the selected issue.
* Changes to stream URL generation, lyrics parsing, or music playback controls.

## Technical Notes

* Relevant specs: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`.
* Expected implementation is a small shared cover-art mapping helper inside `NavidromeRepository.kt` plus MockWebServer repository tests.
