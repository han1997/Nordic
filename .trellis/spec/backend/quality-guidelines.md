# Quality Guidelines

> Code quality standards for backend development.

---

## Overview

The Android client is verified with Gradle debug compilation, Android lint, and
debug assembly before handoff.

---

## Forbidden Patterns

<!-- Patterns that should never be used and why -->

(To be filled by the team)

---

## Required Patterns

- Put platform-specific Android framework attributes in a matching
  `values-vNN/` resource folder when the attribute API level is above `minSdk`.
  Example: `android:forceDarkAllowed` belongs in `values-v29/`, not the base
  `values/` theme.
- For Media3 APIs annotated with `androidx.media3.common.util.UnstableApi`, use
  `@androidx.annotation.OptIn(UnstableApi::class)` at the narrowest practical
  declaration. Android lint recognizes the AndroidX opt-in annotation reliably.

---

## Testing Requirements

- Run `.\gradlew.bat :app:compileDebugKotlin --no-daemon` after Kotlin changes.
- Run `.\gradlew.bat :app:lintDebug --no-daemon` before handoff when resources,
  manifest, dependencies, or Media3 usage changed.
- Run `.\gradlew.bat :app:assembleDebug --no-daemon` before handoff for broad
  app changes.

---

## Code Review Checklist

<!-- What reviewers should check -->

(To be filled by the team)
