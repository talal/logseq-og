---
name: logseq-debug-workflow
description: Debug Logseq bugs with the right runtime, concrete before/after evidence, and end-to-end reproduction steps.
---

# Logseq Debug Workflow

Use for any Logseq bug investigation.

## Core rule

Treat this as a debugging workflow, not a code-only change.

Before claiming a fix, you must:

1. Choose the correct _runtime repl_ and say why:
   - `:app` for renderer, DOM, UI, frontend state, graph-file parsing, and graph-file persistence
   - `:electron` for main-process, BrowserWindow, IPC, app config, filesystem access, file watchers, and native integrations
2. Reproduce the bug in the chosen _runtime REPL_ before editing code.
3. Capture concrete evidence from that runtime: REPL output, file contents, logs, or a failing test that matches the real runtime path.
4. Check relevant logs early.
5. Apply the smallest justified fix.
6. Re-run the same reproduction flow after the fix and capture evidence again.
7. Include restart/reload/reopen verification when the bug involves settings, startup, persistence, window creation, or cross-process behavior.

When a bug crosses the renderer/Electron boundary, use both REPLs and identify which side produced each piece of evidence.

## Runtime selection

Use the renderer `:app` REPL for:

- DOM and UI behavior
- frontend state and handlers
- graph-file parsing and in-memory graph updates
- editor, outliner, plugin, and route behavior
- persistence initiated by renderer-side state changes

Use the Electron `:electron` REPL for:

- `BrowserWindow` creation and lifecycle
- IPC handlers and preload integration
- filesystem reads and writes
- graph directory discovery and file watchers
- native menus, shell, git, updater, and app configuration

Start with `:app` when the behavior is visible in the renderer or driven by frontend state; use `:electron` when the operating-system boundary or window process is involved.

## Do not conclude early

Do **not** say the bug is fixed if any of these is missing:

- pre-fix reproduction evidence
- relevant log evidence, or an explicit statement that checked logs had nothing useful
- post-fix evidence from the same runtime/path
- required end-to-end lifecycle verification

Unit tests alone are **not** enough when this skill applies, unless the bug is truly unit-level and you explicitly justify that.

If the environment blocks full verification, report:

- the blocker
- what you tried
- which evidence is still missing
- the strongest partial evidence you have

## Debugging tools

### General

- Add labeled `prn` checkpoints when helpful.
- Use small REPL/eval checks.
- Use targeted tests to confirm behavior.
- Inspect only relevant inputs, branches, transformed values, outputs, and errors.
- For async flows, inspect both sides of the async boundary.

### File-graph checks

For file-backed graph behavior, record the exact graph directory and files used
by the reproduction. Inspect:

- file contents and timestamps before and after the operation
- graph path resolution and repository selection
- parser inputs and outputs
- file watcher events and duplicate notifications
- writes, backups, errors, and reload behavior

Do not substitute a different graph or a clean temporary graph when the bug
depends on existing files, configuration, or graph state.

### Logseq REPL

Check `logseq-repl` skill.

Using the REPL is not enough for `:app` verification. Also use the `playwright-cli` skill or the repository's Playwright `electron` project to verify the behavior in the Desktop renderer.

### Logs

Logs are evidence. Check them early for Electron, IPC, filesystem, watcher, async, or
persistence issues.

Start with:

- the terminal running `bb watch`
- the terminal running `bb electron-start`
- the platform-specific `electron-log` output for Logseq OG
- the graph files, backups, and error files involved in the reproduction

### Persistent processes with zmx

When a debugging command must outlive the current shell, use the `zmx` skill
instead of an untracked background process:

- use `zmx run NAME -d ...` for long-running non-interactive commands
- use `zmx tail NAME` to follow live output and `zmx history NAME` for evidence
- use `zmx attach NAME` and `zmx send NAME ...` for interactive prompts
- record the session name in the reproduction notes and kill it during cleanup

The managed `logseq-repl` startup workflow remains preferred for Logseq's
watcher and Desktop lifecycle because it also verifies build readiness and
runtime attachment. Use zmx as the lifecycle and output tool for commands that
workflow does not own.

### Required final output

The final response must include these sections or an equivalent structure:

1. **Runtime chosen** — which _runtime REPL_ you used and why
2. **Pre-fix reproduction** — reproduce bug in _runtime REPL_, exact steps and evidence
3. **Root cause** — concrete cause and relevant files/flow
4. **Fix applied** — short description of the change
5. **Post-fix verification** — same steps again with new evidence
6. **Additional verification** — tests/checks run, and what was not verified
7. **Gaps or blockers** — any missing evidence and why

## Quick checklist

Before ending, make sure the answer is yes to all:

- Did I reproduce the bug before fixing it?
- Did I show evidence, not just claim reproduction?
- Did I inspect relevant logs?
- Did I verify in the correct runtime?
- Did I rerun the same scenario after the fix?
- Did I include both before and after evidence in the final output?
- Did I avoid claiming completion if required evidence is missing?

## Verification reminders

- Never run tests, lint, build, or E2E verification in the background.
- Check logs before trusting REPL output alone.
- For persistence or watcher bugs, verify the full edit -> write -> reload flow.
- For REPL debugging, verify against the intended runtime, not a stale one.

Common checks: `bb test -v <namespace/testcase-name>`, `bb lint`.
