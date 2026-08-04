# Agent Development Guide

Conventions and instructions for coding agents.

## Repository structure

Logseq OG monorepo has a ClojureScript application with a shared renderer for
browser and Electron targets, plus publishing and other build products.

- `src/main/frontend/` — shared renderer, Rum UI, handlers, graph database integration, state, search, and extensions
- `src/electron/electron/` — Electron main process compiled by Shadow CLJS.
- `src/main/electron/` — renderer-side Electron adapters
- `deps/` — Clojure local-root libraries
- `scripts/src/` — Babashka task implementations
- `resources/` — source assets, HTML/CSS inputs, packaging files, and Electron preload bridge at `resources/js/preload.js`
- `packages/ui/` — independently built React/TypeScript bundle
- `static/` — assembled/generated runtime output and Electron package
- `docs/` — repository analysis and project documentation

## Documentation map

The `docs/` directory is the source for repository explanations.

- `docs/architecture.md` — runtime behavior and host boundaries
- `docs/repository-map.md` — source ownership and namespace orientation
- `docs/build-and-delivery.md` — toolchain, tasks, artifacts, and delivery
- `docs/assessment.md` — risks and longer-term recommendations

## Core rules

- Never create Git commits unless explicitly asked.
- Never push to Git remotes.
- Never create a GitHub issue.
- Never create a GitHub pull request.
- **Do not install tools globally.** Use `package.json`, `deps.edn`, `bb.edn`, or `nix/devShell.nix`.
- **Do not run Electron packaging or build tasks** they are slow to run. Verify your changes using tests.
- Do not edit `node_modules`, Shadow output, Gulp output, or other generated artifacts by hand. Change their source/configuration and regenerate them only when the task requires it.
- Before changing `static/`, determine whether the file is source, assembled output, or a nested runtime package; prefer the owning source/config file.

## Commands

Do not use `nix develop --command` or `npx`. This repo uses direnv which loads
the Nix devShell and prepends the repository's `node_modules/.bin` directory to
`PATH`, so use local executables directly.

- **ClojureScript tests:** `bb test`
  - Use `bb test:compile` and `bb test:run` to isolate compilation from execution.
- **Browser/Electron E2E tests:** `playwright test`
  - Run the targeted spec since full test suite is slow to run: `playwright test e2e-tests/<name>.spec.ts`
  - Do not pass `--browser` or browser-specific environment variables to Playwright.
- **Linting (Clojure/ClojureScript):** `bb lint`
- **Linting (JavaScript/TypeScript):** `yarn lint`
- **Linting (CSS):** `yarn css:lint`
- **Formatting (Clojure/ClojureScript):** `bb fmt`
- **Formatting checks (Clojure/ClojureScript):** `bb fmt:check`
- **Clojure/ClojureScript parenthesis repair:** `bb clj-paren-repair <files>`
- **Formatting (JavaScript/TypeScript/CSS):** `yarn fmt`
- **Formatting (other):** `treefmt`

## Coding standards

- **Make the smallest coherent change.**
- Do not add backward compatibility unless explicitly requested.
- Do not introduce default values to mask invalid state.
- **Every changed or added behaviour must have a test**. Do not add tests for pre-existing logic that was already present before, and do not test standard-library or third-party functions. The exception is deliberate behaviour or integration tests, which may cross those boundaries by design.
- When fixing a bug or regression, first write a test for it that fails, then change the code to fix the bug and make sure the test passes.
- When writing tests, prefer existing test namespaces and fixtures over introducing a new framework.
- **Follow a REPL-driven workflow for Clojure where practical.**
  - Use `logseq-repl` skill for Logseq runtime behavior and `clojure-eval` skill for standalone Clojure evaluation.
  - Validate behavior in the REPL in addition to the relevant test suite.
  - Prefer evaluating small, focused expressions.
- **Do not try to manually repair Clojure parenthesis errors.** Use `bb clj-paren-repair` on the file instead. If the tool doesn't work, report to the user that they need to fix the delimiter error manually. This tool automatically formats files with cljfmt when it processes them.
- **Always lint and format the relevant files** when you change something before moving on to the next step.
- **Comment sparingly — code says _what_, comments say _why_.** Add a comment only when the reasoning is non-obvious and cannot be carried by a clear name or the code itself. Do not write narrating comments that restate the next line, do not pad logic with multi-line prose, and do not repeat the same rationale at several sites — put one concise note at the source of truth and let the others stand on their own. Tests whose names already describe intent need no explanatory comment. Reserve longer explanation for genuinely complex or non-obvious logic (e.g. a security check whose threat model isn't apparent), and keep even that as tight as it can be. Over-commenting is noise that ages badly and obscures the code it wraps.
- When a change invalidates documented behavior or structure, update the relevant document in `docs/`.
- Put any files you generate (plan, reports, scratch output) under `scratch/`.
- Run proportionate validation, review the diff, and report commands that could not run because dependencies or platform toolchains are unavailable.

## Change boundaries

- Preserve public names, serialized keys, schema fields, IPC command shapes, and user-facing URLs unless the task explicitly includes a migration.
- Follow existing namespace naming and require aliases. Keep portable code CLJS-compatible; use `.cljc` only when both Clojure and ClojureScript paths are genuinely supported.
- Prefer established domain APIs, query helpers, schemas, and migration mechanisms over reaching into implementation internals.
- Give new listeners, timers, watchers, async loops, and database listeners an explicit teardown path so hot reload and tests can clean them up.
- Use Malli/spec validation where a boundary already has a schema. Keep errors user-safe and follow existing logging conventions.

## Electron and security

- Keep `nodeIntegration: false` and `contextIsolation: true` assumptions intact unless a platform change requires otherwise.
- Validate IPC payloads, file paths, URLs, and graph-local theme manifests at the main process boundary. Avoid passing arbitrary channel names or unrestricted paths from renderer code.
- Do not weaken navigation, protocol, CSP, sandbox, signing, or updater settings without documenting the threat model and testing the affected host.
- Never commit credentials, signing material, graph data, generated caches, or local machine paths.

## Documentation and handoff

Keep architectural documentation in `docs/` and link to concrete source files.
When handing off work, summarize the behavior changed, validation performed, any
generated artifacts touched, and any platform-specific checks that remain.
