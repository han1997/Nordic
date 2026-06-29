# Sync Remote and Verify Code

## Goal

Bring the local `main` branch up to date with the remote repository, resolve any merge or rebase conflicts without losing the local video playback work, and verify the Android app remains buildable and testable.

## Requirements

* Confirm the working tree is clean before syncing.
* Fetch remote changes and integrate them into local `main`.
* Resolve any conflicts in favor of a coherent final codebase, preserving both remote changes and the local video playback parity work when compatible.
* Run the project verification gates after sync.
* Report the final git state, commits involved, and verification outcome.

## Acceptance Criteria

- [ ] Local branch has integrated the latest remote changes.
- [ ] Any conflicts are resolved and committed.
- [ ] `git status` is clean except for Trellis-managed task or journal state during workflow steps.
- [ ] `:app:compileDebugKotlin` passes.
- [ ] `:app:testDebugUnitTest` passes.
- [ ] `:app:lintDebug` passes.
- [ ] `:app:assembleDebug` passes if playback or merge resolution touches app code.

## Definition of Done

* Remote sync completed.
* Conflicts resolved if present.
* Verification gates pass.
* Work is committed where needed.
* Trellis task is archived and session is recorded.

## Technical Approach

1. Inspect remotes and branch tracking state.
2. Fetch from the configured remote.
3. Integrate remote changes into local `main` using the repository's current git defaults unless conflict handling requires a more explicit merge/rebase.
4. Resolve conflicts by reading both sides and the surrounding code.
5. Run Gradle verification sequentially on Windows.

## Decision (ADR-lite)

**Context**: The local branch has recent commits that may not exist on the remote.
**Decision**: Preserve local commits and integrate remote changes into the same branch; do not force-push or reset history.
**Consequences**: If histories diverge, a merge or rebase may be required, and conflict resolution must be verified with the Android build gates.

## Out of Scope

* Pushing to remote.
* Reverting local feature work unless the user explicitly asks.
* Making unrelated product changes discovered during sync.

## Technical Notes

* Verification commands come from `.trellis/spec/backend/index.md`.
* Current branch before task creation was `main` with a clean working tree.
