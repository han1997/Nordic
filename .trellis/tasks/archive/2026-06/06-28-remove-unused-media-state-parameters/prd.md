# Remove unused media state parameters

## Goal

Clean up compile-time warnings from media UI wrapper composables so Gradle output stays focused on real regressions.

## Requirements

- Remove unused `colorScheme` parameters from local media empty-state wrapper composables.
- Update all call sites in the same files.
- Do not change rendered UI, copy, state handling, or layout behavior.

## Acceptance Criteria

- [ ] The previous unused-parameter warnings for `AudiobookScreen.kt` and `MusicScreenV2.kt` are gone.
- [ ] `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass sequentially.

## Definition of Done

- Tests/checks pass.
- No spec update unless a durable convention changes.
- Task work committed before finish-work archives and journals the session.

## Technical Approach

- Remove `colorScheme: ColorScheme` from `AudiobookEmptyState(...)`.
- Remove `colorScheme: ColorScheme` from `MusicDetailEmptyState(...)`.
- Update only direct local call sites.

## Out of Scope

- Broad UI refactoring.
- Text/copy changes.
- Design changes.

## Technical Notes

- Relevant specs read: `.trellis/spec/backend/index.md`, `directory-structure.md`, `quality-guidelines.md`, and `guides/code-reuse-thinking-guide.md`.
- Relevant files: `app/src/main/java/com/nordic/mediahub/ui/AudiobookScreen.kt`, `app/src/main/java/com/nordic/mediahub/ui/MusicScreenV2.kt`.
