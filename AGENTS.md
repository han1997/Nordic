<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->

# Long-Running Command Etiquette

Commands that can run longer than ~30s (Gradle builds, test suites, lint, installs) must follow this pattern so the UI never looks "stuck":

1. **Announce before running**: in the same message as the tool call, state what is being run and roughly how long it may take (e.g. "running full Gradle verification, may take 1-3 min").
2. **Report immediately after**: as soon as the result returns, output a one-line verdict (e.g. `BUILD SUCCESSFUL — compile/test/lint all green`) BEFORE doing anything else, then continue with next steps.
3. **Never go silent**: if a command needs to be retried or followed by analysis, say so explicitly instead of pausing output.

Note: the Gradle daemon intentionally stays resident in the background to reuse a warm JVM; a lingering daemon process is normal and does not block anything.
