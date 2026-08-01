# Agent Development Guide

Conventions and instructions for coding agents.

## Commands

Clojure commands are defined primarily in `bb.edn`.
Node/Yarn related commands are defined in `package.json`.

- **Build:** `zig build`
- **Unit tests (ClojureScript):** `bb test`
- **E2E test suite:** `yarn test:e2e`
- **Linting (Clojure/ClojureScript)**: `bb cljs:lint && bb dev:lint`
- **Linting (JS/CSS)**: `yarn css:lint`
- **Formatting (Clojure/ClojureScript)**: `bb format`
- **Formatting (JS/CSS)**: `npx prettier --write .`
- **Formatting (other)**: `nix fmt`

## Repository structure

Logseq OG monorepo has a ClojureScript application with a shared renderer for browser,
Electron, publishing, and Capacitor mobile targets.

- `src/main/frontend/` — shared renderer, Rum UI, handlers, graph database integration, state, plugins, search, extensions, and mobile adapters
- `src/electron/electron/` — Electron main process compiled by Shadow CLJS.
- `src/main/electron/` — renderer-side Electron adapters
- `deps/` — Clojure local-root libraries
- `scripts/src/` — Babashka task implementations
- `resources/` — source assets, HTML/CSS inputs, packaging files, and Electron preload bridge at `resources/js/preload.js`
- `libs/` — TypeScript plugin SDK
- `packages/ui/` and `packages/amplify/` — independently built React/TypeScript bundles
- `tldraw/` — vendored Yarn workspace for whiteboard functionality
- `static/` and `public/` — assembled/generated runtime outputs
- `docs/` — repository analysis and project documentation

## Documentation map

The `docs/` directory is the source for repository explanations.

- [`docs/architecture.md`](docs/architecture.md) — runtime behavior and host boundaries
- [`docs/repository-map.md`](docs/repository-map.md) — source ownership and namespace orientation
- [`docs/build-and-delivery.md`](docs/build-and-delivery.md) — toolchain, tasks, artifacts, and delivery
- [`docs/assessment.md`](docs/assessment.md) — risks and longer-term recommendations

## Coding standards

- **Always lint and format the relevant files** when you change something before moving on
  to the next step.
- **Comment sparingly — code says _what_, comments say _why_.** Add a comment only when the
  reasoning is non-obvious and cannot be carried by a clear name or the code itself. Do
  not write narrating comments that restate the next line, do not pad logic with multi-line
  prose, and do not repeat the same rationale at several sites — put one concise note at the
  source of truth and let the others stand on their own. Tests whose names already describe
  intent need no explanatory comment. Reserve longer explanation for genuinely complex or
  non-obvious logic (e.g. a security check whose threat model isn't apparent), and keep even
  that as tight as it can be. Over-commenting is noise that ages badly and obscures the code
  it wraps.
- **Make the smallest coherent change.**
- **Every changed or added behaviour must have a test**. Do not add tests for pre-existing
  logic that was already present before, and do not test standard-library or third-party
  functions. The exception is deliberate behaviour or integration tests, which may cross
  those boundaries by design.
- When fixing a bug or regression, first write a test for it that fails, then change the
  code to fix the bug and make sure the test passes.
- When writing tests, prefer existing test namespaces and fixtures over introducing a new framework.
- When a change invalidates documented behavior or structure, update the relevant document in `docs/`.
- Put any files you generate (plan, reports, scratch output) under `files/` (ignored in `.gitignore`).
- Run proportionate validation, review the diff, and report commands that could not run because dependencies or platform toolchains are unavailable.

## Change boundaries

- Preserve public names, serialized keys, schema fields, IPC command shapes, and
  user-facing URLs unless the task explicitly includes a migration.
- Follow existing namespace naming and require aliases. Keep portable code
  CLJS-compatible; use `.cljc` only when both Clojure and ClojureScript paths are genuinely
  supported.
- Prefer established domain APIs, query helpers, schemas, and migration mechanisms over
  reaching into implementation internals.
- Give new listeners, timers, watchers, async loops, database listeners, and plugin hooks
  an explicit teardown path so hot reload and tests can clean them up.
- Use Malli/spec validation where a boundary already has a schema. Keep errors user-safe
  and follow existing logging conventions.

## Electron, mobile, and security

- Keep `nodeIntegration: false` and `contextIsolation: true` assumptions intact
  unless a platform change requires otherwise.
- Validate IPC payloads, file paths, URLs, and plugin package data at the main
  process boundary. Avoid passing arbitrary channel names or unrestricted paths
  from renderer code.
- Do not weaken navigation, protocol, CSP, sandbox, signing, or updater
  settings without documenting the threat model and testing the affected host.
- Mobile changes may affect both checked-in native projects and the web bundle;
  inspect `capacitor.config.ts` and the relevant `ios/` or `android/` project.
- Never commit credentials, signing material, graph data, generated caches, or
  local machine paths.

## Dependencies and generated assets

- Update the manifest and lockfile belonging to the package whose dependency changes.
  Keep externalized React globals, TypeScript types, and build targets compatible with their
  host packages.
- Do not edit `node_modules`, Shadow output, Gulp output, or other generated artifacts by
  hand. Change their source/configuration and regenerate them only when the task requires it.
- Before changing `static/` or `public/`, determine whether the file is source, assembled
  output, or a nested runtime package; prefer the owning source/config file.

## Documentation and handoff

Keep architectural documentation in `docs/` and link to concrete source files.
When handing off work, summarize the behavior changed, validation performed,
any generated artifacts touched, and any platform-specific checks that remain.

## Issue and pull request guidelines

- Never create an issue.
- Never create a pull request.
