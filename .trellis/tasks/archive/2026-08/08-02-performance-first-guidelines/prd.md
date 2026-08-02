# Update Performance-First Development Guidelines

## Goal

Record the product/development convention that media UI should prioritize page readability and runtime performance over always-visible playback or navigation chrome.

## Requirements

- Add a concrete Compose quality guideline for performance-first persistent UI.
- Capture that paused/still states do not always need a floating playback bar or navigation surface when those controls reduce content visibility.
- Make the guideline reviewable by adding it to the code review checklist.
- Keep the change scoped to Trellis specs; no app implementation change is required in this task.

## Acceptance Criteria

- `.trellis/spec/backend/quality-guidelines.md` includes an actionable rule for transient playback/navigation chrome.
- The rule explains why hidden/collapsed controls can be preferable while static.
- The quality checklist includes a performance-first item for persistent overlays and fast-changing media UI.
