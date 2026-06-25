# UI performance review and optimization

## Goal

Review the Android app UI for perceived lag, identify the main rendering and state-update bottlenecks, and implement targeted optimizations that improve smoothness without changing user-visible behavior unnecessarily.

## What I already know

* The user reports that the current software UI still feels laggy.
* This is a single-module Android project using Kotlin and Jetpack Compose.
* Likely high-risk UI surfaces live under `app/src/main/java/com/nordic/mediahub/ui/`, including `MusicScreenV2.kt`, `MusicPlayerScreen.kt`, `AudiobookScreen.kt`, `AudiobookPlayerScreen.kt`, `VideoScreen.kt`, `PlaybackDock.kt`, and `AnimatedComponents.kt`.
* There was a prior archived task related to app-wide performance smoothness optimization, so some earlier performance work already exists.

## Assumptions (temporary)

* The user wants code review plus direct optimization work in the same task.
* The main pain is runtime UI smoothness, not build speed or startup time.
* Preserving current product behavior is more important than visual redesign.

## Open Questions

* Which screen or interaction currently contributes the most to the perceived lag?

## Requirements (evolving)

* Audit the main UI rendering paths for unnecessary recomposition, unstable state propagation, expensive layout work, and avoidable allocations.
* Identify the concrete hotspots responsible for the laggiest user interactions.
* Implement safe optimizations in the most impactful hotspots.
* Keep existing behavior and visual structure intact unless a change is required for performance.
* Verify the app still builds and relevant tests still pass after changes.

## Acceptance Criteria (evolving)

* [x] Hot UI paths have been reviewed with concrete findings tied to code locations.
* [x] At least the most impactful performance issues found in this pass are optimized in code.
* [x] The project builds successfully after the optimization changes.
* [x] Relevant automated checks are run and reported.

## Verification Results

* [x] `.\gradlew.bat :app:compileDebugKotlin --no-daemon`
* [x] `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
* [x] `.\gradlew.bat :app:lintDebug --no-daemon`
* [x] `.\gradlew.bat :app:assembleDebug --no-daemon`

## Definition of Done (team quality bar)

* Tests added/updated where appropriate
* Lint / typecheck / CI-equivalent checks green for changed scope
* Docs/notes updated if behavior or conventions change
* Rollback risk considered for non-trivial performance changes

## Out of Scope (explicit)

* Broad UI redesign unrelated to runtime performance
* Large architecture rewrites unless a smaller targeted fix is insufficient
* Backend or network optimization not directly affecting UI smoothness in this pass

## Technical Notes

* Repo inspected: Android Gradle project, no frontend web package.
* Likely work area: `app/src/main/java/com/nordic/mediahub/ui/`
* Prior related archive entry seen in recent commits: `06-20-app-wide-performance-smoothness-optimization`
