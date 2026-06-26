# Comprehensive Code Review and Optimization

## Goal

Review the Nordic Media Hub Android app end to end, identify concrete correctness, maintainability, performance, and UX-quality risks, then implement focused optimizations that reduce real technical debt without broad rewrites.

## What I Already Know

* The repository is a single-module Android app under `app/`.
* The app uses Kotlin, Jetpack Compose, Material 3, Retrofit/OkHttp, DataStore, Coil, and Media3.
* Main functional areas are music/Navidrome, audiobooks/AudiobookShelf, video/Emby, configuration, and playback.
* Existing unit tests cover repository/auth/playback behavior and UI copy encoding.
* The working tree was clean before this task started.
* Project quality specs call out known risk areas: string-based error classification, duplicate utilities, shared Compose state components, stable lazy-list keys/content types, high-frequency playback recomposition, config readiness centralization, repository instance reuse, and Media3 queue consistency.
* There are 43 Kotlin source/test files under `app/src/main/java` and `app/src/test/java`.
* Initial scans found no `TODO`, `FIXME`, or `message?.contains(...)` hits.
* Repository construction sites are concentrated in `MainActivity`, `MusicScreenV2`, `AudiobookScreen`, and `VideoScreen`; most saved-config repositories appear to use `remember(config)` as required.

## Assumptions

* The review should prioritize high-signal fixes in app code, not wholesale architecture rewrites.
* The first pass should favor issues that can be verified with local Gradle tasks.
* External dependency upgrades are out of scope unless required to fix a concrete bug or build issue.

## Open Questions

* Final confirmation before implementation.

## Requirements

* Inspect the current Android app structure, build scripts, tests, and Trellis specs.
* Produce actionable review findings before making changes.
* Use a phased comprehensive review scope: inspect the full app, then implement only high-confidence, high-value fixes.
* Prioritize P0/P1 defects, low-risk performance waste, duplicate logic, maintainability risks, and missing tests for changed behavior.
* Keep MVP scope to current review findings only; do not add extra robustness work unless it directly supports a selected fix.
* Preserve existing user-facing behavior unless a defect is found.
* Avoid unrelated refactors and avoid reverting unrelated user work.
* Keep verification sequential on Windows.

## Acceptance Criteria

* [ ] Review findings are grounded in file/line references.
* [ ] Implemented changes address concrete defects, performance waste, duplication, or maintainability risks.
* [ ] Existing tests still pass, or any failures are explained with next actions.
* [ ] Relevant Gradle checks are run sequentially.
* [ ] No broad formatting churn or unrelated file changes are introduced.

## Definition of Done

* Tests added or updated where behavior changes.
* Compile, unit tests, and lint are run as appropriate.
* Any new project convention discovered is considered for `.trellis/spec/`.
* Commit plan is prepared for user confirmation if code changes are made.

## Out of Scope

* New product features.
* Large dependency upgrade campaign.
* Full UI redesign.
* Backend server changes.
* Rewriting all screens or replacing core architecture.
* Low-confidence speculative optimizations.
* Large cosmetic-only refactors without a review finding.
* Extra boundary/exception-path test expansion unrelated to implemented fixes.
* Follow-up issue catalog beyond concise notes in the final review summary.

## Decision (ADR-lite)

**Context**: "Comprehensive review and optimization" can become too broad if it includes every possible cleanup or redesign.

**Decision**: Use a phased comprehensive review. Review the app broadly, record findings with file/line references, and implement only high-confidence, high-value fixes.

**Consequences**: This should produce meaningful improvements while keeping risk and verification cost bounded. Some lower-priority cleanup may remain as documented follow-up rather than being implemented immediately.

## Technical Approach

1. Run a focused review pass across build scripts, app architecture surfaces, repositories/API mappings, playback engines, Compose screens, and existing tests.
2. Rank findings by severity and confidence.
3. Implement only the selected high-value fixes.
4. Add or update tests where a fix changes behavior or guards a regression.
5. Run the relevant Gradle verification commands sequentially on Windows.

## Implementation Plan

* Pass 1: Static review and findings list.
* Pass 2: Implement high-confidence fixes with narrowly scoped patches.
* Pass 3: Run compile/tests/lint as appropriate and iterate until green or clearly report blockers.

## Technical Notes

* Root build scripts: `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`.
* Main source tree: `app/src/main/java/com/nordic/mediahub`.
* Test source tree: `app/src/test/java/com/nordic/mediahub`.
* Key UI files: `MainActivity.kt`, `MusicScreenV2.kt`, `AudiobookScreen.kt`, `VideoScreen.kt`, player and queue composables.
* Key data/playback files: `NavidromeRepository.kt`, `AudiobookShelfRepository.kt`, `EmbyRepository.kt`, `ConfigRepository.kt`, `NavidromeMusicCacheRepository.kt`, `MusicPlaybackEngine.kt`, `AudiobookPlaybackEngine.kt`.
* Specs read so far: `.trellis/spec/backend/index.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/guides/index.md`.
* Verification commands from spec:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`
