# App-wide Performance and Smoothness Optimization

## Goal

Improve perceived smoothness across Nordic Media Hub beyond the previous music-list pass, with emphasis on app-wide scrolling, image-heavy media cards, repository reuse, and avoiding avoidable work during page switching and refresh flows.

## What I Already Know

* User asked for broad performance and smoothness optimization.
* The previous completed task optimized music list/image scrolling with stable lazy-list keys/content types, remembered preview slices, and removed music artwork log spam.
* The app is a Kotlin/Jetpack Compose Android app with Music, Audiobook, and Video surfaces.
* Product context: the UI should stay content-first, lightweight, restrained, and consistent across media types.
* Design/performance guidance: measure or inspect actual bottlenecks, target perceptible UI work, and avoid risky broad rewrites.

## Findings From Initial Repo Inspection

* `VideoScreen.kt` has image-heavy `LazyColumn`/`LazyRow` surfaces; lists have keys but are missing `contentType`.
* `VideoScreen.kt` creates `EmbyRepository(targetConfig)` inside refresh even though a remembered repository exists for saved config.
* `AudiobookScreen.kt` creates `AudiobookShelfRepository(targetConfig)` inside refresh even though a remembered repository exists for saved config.
* `AudiobookScreen.kt` renders the library selector with a regular `Row`, which can overflow and composes every library chip at once.
* `AudiobookScreen.kt` summary/chapter lazy items have keys but no `contentType`.
* `VideoThumbnail` and `AudiobookCover` use `AsyncImage` without local failure fallback state, so failed image attempts keep showing the image path rather than switching to a cheap fallback surface.
* `MusicQueueSheet.kt` already uses duplicate-safe position-aware keys, but its queue rows can still add `contentType`.
* `MainActivity.kt` already remembers repositories by config and uses lifecycle collection, but it has recurring audiobook progress sync and lyrics loading that should not be changed unless they prove to be the dominant bottleneck.

## Assumptions

* This second optimization pass should prioritize non-music surfaces because music list scrolling was already handled.
* Low-risk Compose-level changes are preferable before changing cache schemas, server API behavior, or playback timing.
* Repository contract changes are out of scope unless inspection proves they are necessary.

## Open Questions

* None for this MVP.

## Requirements (Evolving)

* Prioritize video and audiobook list/image smoothness for this pass.
* Add lazy-list `contentType` to video, audiobook, and queue list rows where appropriate.
* Convert audiobook library selection to a lazy horizontal list so many libraries do not all compose at once or overflow.
* Add lightweight image failure fallback state for video thumbnails and audiobook covers.
* Reuse remembered repositories during refresh flows when the saved config is current, only constructing a temporary repository for unsaved config values.
* Preserve current user-facing behavior and existing visual design.
* Avoid changing playback queue semantics, API contracts, or cache schema unless explicitly needed.
* Prefer low-risk changes that reduce recomposition, allocation, image work, or unnecessary repository creation.
* Verify with the app's Gradle gates.

## Acceptance Criteria (Evolving)

* [x] Video, audiobook, and queue lazy lists use stable keys and `contentType` where data identity exists.
* [x] Video thumbnails and audiobook covers use cheap fallback behavior after image load failure.
* [x] Video and audiobook refresh flows avoid unnecessary repository construction when the remembered repository is valid.
* [x] Audiobook library selector remains usable with many libraries and does not eagerly compose all chips in a plain row.
* [x] Existing compile, unit test, and lint gates pass.
* [x] Any reusable convention discovered is captured in `.trellis/spec/`.

## Definition of Done

* Relevant specs read before coding.
* Focused implementation committed.
* `.\gradlew.bat :app:compileDebugKotlin --no-daemon` passes.
* `.\gradlew.bat :app:testDebugUnitTest --no-daemon` passes.
* `.\gradlew.bat :app:lintDebug --no-daemon` passes.
* Spec update reviewed and applied only if useful.

## Out of Scope

* Replacing Compose, Coil, Retrofit/OkHttp, Media3, or the app navigation model.
* Adding a performance monitoring SDK.
* Large cache schema redesign.
* Changing server API behavior or playback queue semantics.
* Broad visual redesign unrelated to performance.

## Technical Notes

* Task created at `.trellis/tasks/06-20-app-wide-performance-smoothness-optimization`.
* Trellis context loaded with `trellis-start` and `trellis-brainstorm`.
* Impeccable product and optimize references loaded.
* Initial inspection covered `MainActivity.kt`, `VideoScreen.kt`, `AudiobookScreen.kt`, `MusicQueueSheet.kt`, and existing music optimization changes.
* Implemented video and audiobook lazy-list `contentType` values, plus queue row `contentType`.
* Converted audiobook library chips from eager `Row` composition to `LazyRow`.
* Added image failure fallback state for `VideoThumbnail` and `AudiobookCover`.
* Reused remembered `EmbyRepository` and `AudiobookShelfRepository` when refresh config matches saved config.
* Updated `.trellis/spec/backend/quality-guidelines.md` to generalize repository reuse across media repositories.
* Verification passed:
  * `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
  * `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
  * `.\gradlew.bat :app:lintDebug --no-daemon`

## Decision

**Context**: Music list/image scrolling was optimized in the previous task, so the next visible hotspots are video and audiobook media surfaces.

**Decision**: Prioritize video + audiobook list/image smoothness for this MVP.

**Consequences**: This pass stays low risk and UI-focused, improving scrolling/composition behavior without changing playback, API, or cache contracts.
