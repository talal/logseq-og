---
name: logseq-repl
description: Start and coordinate Logseq development REPL workflows for the Desktop renderer `:app` and Electron main process `:electron` runtimes.
---

# Logseq OG REPL Workflow

Use this skill when the user needs a Logseq OG development REPL for:

- Desktop renderer `:app`
- Electron main process `:electron`
- both runtimes

This fork has two Shadow CLJS builds. It does not define a `:db-worker-node` build or ship `static/db-worker-node.js`; do not use upstream worker commands.

The workflow uses one shared state directory: `<repo>/tmp/logseq-repl/`.

## Scripts

Start the watcher and Desktop app:

```bash
<logseq-repl-skill-dir>/scripts/start-repl.sh
```

Use `--repo-root <path>` when invoking the script from another checkout:

```bash
<logseq-repl-skill-dir>/scripts/start-repl.sh --repo-root /path/to/logseq-og
```

Clean up processes started by the workflow:

```bash
<logseq-repl-skill-dir>/scripts/cleanup-repl.sh
```

Verify both REPL targets after startup:

```bash
<logseq-repl-skill-dir>/scripts/verify-repls.sh
```

`start-repl.sh` delegates to `scripts/start-repl.py`; use the shell wrapper unless you need to invoke the Python implementation directly.

`start-repl.sh` starts:

1. the repository `bb watch` task, which runs the asset watcher and Shadow watches for `:app` and `:electron`
2. the repository `bb electron-start` task, which launches the Desktop app

The Python wrapper waits for `Desktop watcher is ready.`, waits for exactly one `:app` runtime and at least one `:electron` runtime, then runs the noninteractive verification script. It exits after verification; the watcher and Desktop app remain running.

The verification script uses the Shadow Clojure nREPL on port `8701` through the repository's `bb clj-nrepl-eval` task and calls `shadow.cljs.devtools.api/cljs-eval` directly against each build. This avoids mutating the persistent evaluator's selected CLJS mode.

For interactive work, the fork's `shadow.user/cljs-repl` and `shadow.user/electron-repl` helpers select a live CLJS runtime in that persistent nREPL session.

## Standard Flow

Before starting:

```bash
<logseq-repl-skill-dir>/scripts/cleanup-repl.sh
```

Then start both runtimes:

```bash
<logseq-repl-skill-dir>/scripts/start-repl.sh
```

The script does not open an interactive terminal REPL. Select the runtime you need, then evaluate forms on the same nREPL port:

```bash
# Select the Desktop renderer runtime.
bb clj-nrepl-eval -p 8701 --reset-session "(shadow.user/cljs-repl)"

# Select the Electron main-process runtime instead.
bb clj-nrepl-eval -p 8701 --reset-session "(shadow.user/electron-repl)"

# Evaluate a form in the selected runtime.
bb clj-nrepl-eval -p 8701 "(prn {:runtime :current})"
```

Use `--reset-session` before switching runtimes. Evaluator sessions persist by host and port, so a later invocation continues in the selected CLJS runtime.

## Runtime Selection

Use the renderer `:app` REPL for:

- DOM and UI behavior
- frontend state, handlers, and routes
- editor, outliner, plugin, and page behavior
- graph parsing and renderer-initiated persistence

Use the Electron `:electron` REPL for:

- `BrowserWindow` creation and lifecycle
- IPC and preload integration
- filesystem access and file watchers
- native menus, shell, git, updater, and app configuration

Runtime reminder:

- `:app` is the Electron renderer runtime, even though its Shadow target is `:browser`
- `:electron` is the Electron main-process runtime

## Readiness Model

Keep these states separate:

1. watcher alive: `bb watch` is running
2. build ready: `bb watch` printed `Desktop watcher is ready.`
3. runtime attached: `:app` and `:electron` have live runtime connections

Check runtime counts from the repository root:

```bash
bb clj-nrepl-eval -p 8701 --reset-session <<'EOF'
(do
  (require '[shadow.cljs.devtools.api :as api])
  (println {:app (count (api/repl-runtimes :app))
            :electron (count (api/repl-runtimes :electron))}))
EOF
```

Interpretation:

- `:app` must equal `1` for an unambiguous Desktop renderer target
- `:electron` must be greater than `0`
- `0` means not ready, even if watcher logs look healthy
- more than one `:app` runtime means another renderer is attached; close the extra Desktop app before debugging renderer behavior

## Logs

Look here first:

- `<repo>/tmp/logseq-repl/shared-shadow-watch.log`
- `<repo>/tmp/logseq-repl/desktop-electron.log`
- the terminal output from `bb watch`
- the terminal output from `bb electron-start`
- platform-specific Electron logs when investigating native behavior

The watcher log includes both the asset watcher and Shadow CLJS build output.

## Port Audit

The fork uses these development ports:

```bash
ss -ltn '( sport = :8701 or sport = :3001 or sport = :9630 or sport = :9631 )'
```

Use `lsof` with the same ports when `ss` is unavailable. Interpret the result:

- no listeners: clean enough to start
- listeners after cleanup: resolve the external conflict first
- listeners after startup: expected when owned by this workflow

`cleanup-repl.sh` stops only processes recorded in the workflow PID files. It does not kill arbitrary processes based only on a port.

## Non-Interactive Verification Examples

Desktop `:app`:

```bash
bb clj-nrepl-eval -p 8701 --reset-session "(shadow.user/cljs-repl)"
bb clj-nrepl-eval -p 8701 <<'EOF'
(prn {:runtime :app
      :document? (some? js/document)
      :title (.-title js/document)})
EOF
```

Electron `:electron`:

```bash
bb clj-nrepl-eval -p 8701 --reset-session "(shadow.user/electron-repl)"
bb clj-nrepl-eval -p 8701 <<'EOF'
(prn {:runtime :electron
      :process? (some? js/process)
      :type (.-type js/process)})
EOF
```

The `shadow.user` helpers are also available from an editor-connected Clojure nREPL:

```clojure
(shadow.user/cljs-repl)
(shadow.user/electron-repl)
```

## Troubleshooting

Failure triage order:

1. inspect `tmp/logseq-repl/shared-shadow-watch.log`
2. inspect `tmp/logseq-repl/desktop-electron.log`
3. inspect the `bb watch` and `bb electron-start` terminals
4. inspect standard port listeners
5. inspect runtime counts with `shadow.cljs.devtools.api/repl-runtimes`

Common cases:

- `bb watch` exits early: inspect its log for asset or Shadow build errors
- `bb electron-start` says no watcher is running: start `bb watch` first, or rerun `start-repl.sh`
- `No available JS runtime`: the build may be ready but the Desktop app has not connected; check `:app` and `:electron` runtime counts
- multiple `:app` runtimes: close extra Desktop app instances
- ports already in use after cleanup: another development session owns them
- a direct `shadow-cljs cljs-repl` invocation starts a second Shadow server: use the repository nREPL helpers above instead

## Recommended Response Pattern

When helping a user connect to a REPL:

1. identify whether they need `:app`, `:electron`, or both
2. run `cleanup-repl.sh`
3. if standard ports remain occupied, resolve that conflict first
4. run `start-repl.sh`
5. verify runtime counts if selection fails
6. select the matching `shadow.user` helper
7. capture REPL output from the selected runtime
8. run `cleanup-repl.sh` when finished
