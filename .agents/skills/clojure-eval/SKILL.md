---
name: clojure-eval
description: Evaluate Clojure code via the repository nREPL. Use this when you need to test code, check if edited files compile, verify function behavior, or interact with a running REPL session.
---

# Clojure REPL Evaluation

## When to Use This Skill

Use this skill when you need to:

- **Verify that edited Clojure files compile and load correctly**
- Test function behavior interactively
- Check the current state of the REPL
- Debug code by evaluating expressions
- Require or load namespaces for testing
- Validate that code changes work

## How It Works

This fork exposes `clj-nrepl-eval` as the repository Babashka task: `bb clj-nrepl-eval`. It evaluates Clojure code against an nREPL server and persists session state between evaluations. Each host:port combination maintains its own session file.

For Logseq's Shadow CLJS development session, use the `logseq-repl` skill to start `bb watch` and the Desktop app first. The Shadow Clojure nREPL listens on port `8701`.

## Instructions

### 0. Start a Logseq development REPL when needed

If no nREPL is available, use the `logseq-repl` skill:

```bash
.agents/skills/logseq-repl/scripts/cleanup-repl.sh
.agents/skills/logseq-repl/scripts/start-repl.sh
```

Then use port `8701` for the Shadow Clojure nREPL. The startup script verifies that the renderer and Electron CLJS runtimes are attached; it does not replace this Clojure evaluator for namespace or build-tool evaluation.

### 1. Discover and select an nREPL server

First, discover nREPL servers in the current directory:

```bash
bb clj-nrepl-eval --discover-ports
```

This can show Clojure, Babashka, and Shadow CLJS nREPL servers. Use the port reported for the server that owns the code under test. In the fork's standard Logseq workflow this is `8701`.

If several servers are listed, select the one matching the current repository before evaluating code.

### 2. Evaluate Clojure code

Use `-p`/`--port` to specify the port. Heredocs avoid shell escaping issues:

```bash
bb clj-nrepl-eval -p <PORT> <<'EOF'
(+ 1 2 3)
EOF
```

For multiple expressions:

```bash
bb clj-nrepl-eval -p <PORT> <<'EOF'
(def x 10)
(+ x 20)
EOF
```

### 3. Display nREPL sessions

Discover all nREPL servers in the current project:

```bash
bb clj-nrepl-eval --discover-ports
```

Check previously connected sessions:

```bash
bb clj-nrepl-eval --connected-ports
```

### 4. Common patterns

**Require a namespace (always use `:reload` to pick up changes):**

```bash
bb clj-nrepl-eval -p <PORT> "(require '[my.namespace :as ns] :reload)"
```

**Test a function after requiring it:**

```bash
bb clj-nrepl-eval -p <PORT> "(ns/my-function arg1 arg2)"
```

**Check whether a file compiles:**

```bash
bb clj-nrepl-eval -p <PORT> "(require 'my.namespace :reload)"
```

**Multiple expressions:**

```bash
bb clj-nrepl-eval -p <PORT> "(def x 10) (* x 2) (+ x 5)"
```

**Complex multiline code:**

```bash
bb clj-nrepl-eval -p <PORT> <<'EOF'
(def x 10)
(* x 2)
(+ x 5)
EOF
```

**Custom timeout in milliseconds:**

```bash
bb clj-nrepl-eval -p <PORT> --timeout 5000 "(long-running-fn)"
```

**Reset the evaluator session:**

```bash
bb clj-nrepl-eval -p <PORT> --reset-session
bb clj-nrepl-eval -p <PORT> --reset-session "(def x 1)"
```

## Available Options

- `-p, --port PORT` — nREPL port (required)
- `-H, --host HOST` — host (default: `127.0.0.1`)
- `-t, --timeout MILLISECONDS` — timeout (default: `120000`)
- `-r, --reset-session` — reset the persistent evaluator session
- `-c, --connected-ports` — list previously connected nREPL sessions
- `-d, --discover-ports` — discover nREPL servers
- `-h, --help` — show command help

## Important Notes

- Prefer heredocs for multiline expressions.
- Sessions persist until the nREPL server restarts.
- `--reset-session` resets the evaluator session; it does not remove vars or namespaces already loaded by the server.
- The evaluator repairs missing or mismatched delimiters automatically.
- Always use `:reload` when requiring changed namespaces.
- The default timeout is two minutes; increase it for long operations.
- Command-line code takes precedence over stdin.

## Typical Workflow

1. Start the Logseq workflow if the code under test needs a live Shadow session.
2. Discover nREPL servers:
   ```bash
   bb clj-nrepl-eval --discover-ports
   ```
3. Evaluate or reload the namespace:
   ```bash
   bb clj-nrepl-eval -p 8701 "(require '[my.ns :as ns] :reload)"
   ```
4. Evaluate the focused function or expression:
   ```bash
   bb clj-nrepl-eval -p 8701 "(ns/my-fn ...)"
   ```
5. Iterate: make changes, require with `:reload`, and re-run the focused check.
