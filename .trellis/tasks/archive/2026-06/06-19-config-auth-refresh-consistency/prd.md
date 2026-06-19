# Fix Config Security And Auth Refresh Consistency

## Goal

Resolve the code review findings around local settings tracking, Navidrome refresh data-source consistency, and server readiness validation so config changes do not leak credentials or sync against mismatched credentials.

## Requirements

* Remove `.claude/settings.local.json` from version control tracking while preserving the local file for the developer.
* Ensure Navidrome music refresh uses a repository created from the same `targetConfig` being refreshed, including the save-new-config path.
* Add a regression test covering the saved-config refresh behavior so data from an old config cannot be cached under a new config.
* Align Navidrome and AudiobookShelf readiness with Video readiness style: require URL plus username and password.

## Acceptance Criteria

* [ ] `.claude/settings.local.json` is no longer tracked by git and local sensitive contents are not staged.
* [ ] `refreshMusicData(targetConfig)` cannot use a repository built from a different config.
* [ ] Unit tests cover the config/repository mismatch regression.
* [ ] Readiness checks for Navidrome and AudiobookShelf reject blank passwords.
* [ ] Kotlin compile, unit tests, lint, and debug assemble pass.

## Definition of Done

* Relevant tests added or updated.
* Gradle verification passes sequentially on Windows.
* No unrelated user changes are reverted.
* Commit plan is presented before committing.

## Technical Approach

* Add `.claude/settings.local.json` to `.gitignore` and remove it from the index with `git rm --cached`.
* Decouple music refresh from the composable's remembered `navidromeRepository` by using a repository factory keyed to the provided config.
* Extract a small testable refresh helper if needed so the repository-factory behavior can be unit tested without Compose UI instrumentation.
* Update `isReadyForMusicSync()` and `isReadyForAudiobookSync()` to require passwords.

## Out of Scope

* Reworking DataStore credential storage.
* Adding encrypted secrets storage.
* Changing Video/Plex/WebDAV behavior beyond readiness parity.

## Technical Notes

* Key files: `MusicScreenV2.kt`, `ServerConfig.kt`, `AudiobookShelfAuth.kt`, Navidrome tests.
* Existing spec layer: `.trellis/spec/backend/index.md` and linked quality/error/database/logging guidelines.
