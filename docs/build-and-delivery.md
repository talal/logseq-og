# Build, tooling, and delivery

## Toolchain ownership

| Tool             | Source of truth                                    | Responsibility                                                                                               |
| ---------------- | -------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Clojure CLI      | `deps.edn`                                         | JVM/CLJS dependencies, source paths, test/bench/lint aliases.                                                |
| Shadow CLJS      | `shadow-cljs.edn`                                  | Browser modules, Electron Node script, publishing app, Node tests, Storybook CLJS module.                    |
| Babashka         | `bb.edn`, `scripts/src`                            | Preferred task catalog and orchestration across CLJS, Electron, mobile, publishing, validation, and linting. |
| Yarn             | multiple `package.json`/lockfiles                  | JS dependencies for root and independently managed subprojects.                                              |
| Gulp             | `gulpfile.js`                                      | Copy resources/vendor assets, compile/minify CSS, assemble `static` and `public`, launch packaging.          |
| PostCSS/Tailwind | root configs                                       | Main application stylesheet generation.                                                                      |
| Electron Forge   | `resources/forge.config.js`, `static/package.json` | macOS, Windows, and Linux packages; signing/notarization and GitHub publishing.                              |
| Capacitor        | `capacitor.config.ts`, native projects             | iOS/Android shells and native plugins.                                                                       |
| Parcel/Webpack   | package-local configs                              | React UI/Amplify globals and plugin SDK builds.                                                              |

## Shadow build graph

- `app`: browser target, entry `frontend.core/init`; lazy `code-editor`,
  `excalidraw`, and `tldraw` modules.
- `electron`: Node script at `static/electron.js`, entry `electron.core/main`.
- `test`: Node test bundle at `static/tests.js`.
- `publishing`: browser target, entry `frontend.publishing/init`, with parallel
  lazy modules.
- `stories-dev`: npm module feeding the UI package's Storybook.
- `gen-malli-kondo-config`: utility Node script for lint configuration.

React and ReactDOM are resolved to globals instead of bundled by Shadow. That
decision reduces duplication and supports JavaScript-produced UI globals, but it
makes HTML/resource assembly and compatible global versions part of the compiler
contract.

Shadow CLJS is declared through the Clojure CLI in `deps.edn`; the root package
does not duplicate that tool dependency. Similar skew still exists across root
React 17, `packages/ui` runtime React 18 declarations, React 17 type packages,
and externalized globals.

## Development flows

The clearest supported entry points are the Babashka tasks:

- `bb dev:electron-start`: run desktop asset/CLJS watches and open Electron
  concurrently.
- `bb dev:ios-app` / `bb dev:android-app`: watch app assets and run Capacitor
  against a reachable development server.
- `bb dev:publishing`: build publishing output, optionally in watch mode.
- `bb test`: compile and execute the main CLJS tests.
- `bb dev:lint`: aggregate repository linting.

Lower-level Yarn/Gulp scripts remain for Node-only asset and packaging steps.
Project workflows are orchestrated through Babashka.

## Asset pipeline

`resources/` is copied into `static/`. Gulp then copies selected prebuilt assets
from `node_modules`, builds Tailwind/PostCSS CSS, and optionally mirrors
assembled JS/CSS into `public/static` for Capacitor. Babashka orchestrates the
ClojureScript build before the Node-only Electron packaging step updates the
nested static package version from `frontend/version.cljs`, installs nested
dependencies if needed, and invokes Electron Forge.

This pipeline treats `static/` as both build output and a checked-in/nested
package. That dual role complicates cleaning, ownership, cache invalidation, and
reviews. A clean separation such as `resources/` -> ephemeral `build/desktop` ->
packaging would make provenance clearer.

## Platform delivery

Electron Forge configures Squirrel and WiX for Windows, DMG/ZIP for macOS,
AppImage/ZIP for Linux, code signing/notarization, the `logseq-og` protocol, and
GitHub prerelease publishing. Runtime entry is `static/electron.js` as declared
by the root/nested application package.

Capacitor packages `public`, uses a development server when
`LOGSEQ_APP_SERVER_URL` is set, and integrates native filesystem/sync, camera,
clipboard, keyboard, share, background task, haptics, splash, and
status/navigation bar plugins. The native Android and iOS projects are checked
in.

## Test and quality model

The tree contains approximately 73 CLJS test files plus Clojure/CLJC tests and
about 30 TypeScript Playwright files. Test layers include:

- Main Node-targeted CLJS unit/integration tests through Shadow CLJS.
- Library-local CLJS and `nbb-logseq` tests in `deps/*`.
- Playwright end-to-end tests, serialised to one worker and stopping after the
  first failure.
- Babashka validation of Malli schemas, storage/config formats, AST data,
  translations, file sync, namespace documentation, large vars, unused vars, and
  clj-kondo rules.
- Package-local TypeScript/format/lint/build checks.

No `.github/workflows` directory is present in this checkout, even though local
library READMEs refer to upstream workflow files. Consequently, this snapshot
does not provide one visible, authoritative CI matrix proving which combinations
are release gates. Reconstructing that matrix from commands alone is possible
but fragile.

## Reproducibility observations

- Multiple Yarn lockfiles intentionally isolate subprojects, but root
  postinstall does not install every package.
- Some packages build during `postinstall`, creating hidden ordering and side
  effects.
- Root dependencies use a mix of exact versions, ranges, local roots, and a
  vendored fork.
- Generated output and installed dependencies exist in the working tree
  directories, which can distort searches and disk-based metrics.
- Platform builds depend on credentials and native toolchains that are not
  expressible solely through the root task graph.
