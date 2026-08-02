---
name: updating-node-dependency
description: Safely upgrade a specific Node.js package in this Yarn-based application monorepo while preserving manifest and yarn.lock consistency, peer-dependency compatibility, and build behavior. Use whenever a user asks to update, bump, modernize, or replace a Node dependency, including requests involving package.json, yarn.lock, Yarn workspaces, yarn outdated, or dependency-related build failures. Do not use this skill for a Node.js runtime migration or an intentional all-dependencies modernization.
---

# Updating a Node Dependency

Upgrade the requested package in the smallest coherent change. Keep the package
manager, manifest, and lockfile consistent; preserve the dependency's role
(dependencies, devDependencies, optionalDependencies, peerDependencies, or an
override/resolution); and validate the code paths that consume it.

## Rules

1. Scope the work to the named package and the minimum directly coupled changes.
   Do not update every outdated package, regenerate unrelated tooling, or change
   the Node.js runtime unless the user explicitly asks for that.
2. Do not use a bulk update command without a package filter. Treat Yarn's
   `--force`, `--ignore-engines`, and equivalent bypasses as diagnostic signals,
   not permanent fixes.
3. Never hand-edit a lockfile. Use the repository's package manager to resolve
   it, then review the resulting lockfile diff for unrelated churn.
4. Use Yarn and preserve the existing `yarn.lock` format. This repository
   currently uses Yarn Classic 1.22.22; do not switch Yarn generations or adopt
   another package manager as part of a dependency update.
5. Use a stable release unless the user explicitly requests a prerelease. For a
   major update, inspect the package's official migration notes and check its
   engine, peer, export, and API changes before editing files.
6. Do not silently remove an incompatible package, downgrade an unrelated
   package, or broaden a version range. Explain any necessary companion change.
7. Follow repository-local instructions. Run the relevant formatter and linter
   for every changed source or configuration file, and add or update a focused
   test when the upgrade changes deliberate application behavior.

## Workflow

### 1. Establish the package boundary

Before changing anything:

- Read the repository's `AGENTS.md`, `CONTRIBUTING.md`, and package scripts when
  present. Note required validation commands and restrictions on manifests.
- Check `git status` and preserve unrelated user changes.
- Confirm the repository's Yarn version and identify the authoritative
  `yarn.lock` for the package. This repository has multiple nested manifests and
  lockfiles, so resolve the owning package directory from repository
  instructions and CI before running Yarn. Never update a neighboring lockfile
  accidentally.
- Find every relevant manifest and workspace containing the package. Search
  `package.json` files, workspace configuration, `resolutions`, and
  package-specific build configuration. A package may be direct in one workspace
  and transitive or peer-constrained in another.
- Record the current declared range, installed version, dependency section, Yarn
  version, Node engine requirement, and whether the working tree already has
  lockfile or manifest drift.

Use narrow inventory commands appropriate to the repository, for example:

```bash
rg --files -g 'package.json' -g 'yarn.lock' -g '.yarnrc*'
rg -n '"the-package"|resolutions|packageManager' \
  --glob 'package.json' --glob '!node_modules/**'
yarn why the-package
```

Do not assume the root manifest is the only owner. For a workspace package,
inspect both its local manifest and the root workspace configuration.

### 2. Choose and assess the target

Use the version requested by the user. If no version is specified, determine the
current stable release from Yarn's registry metadata with
`yarn info
the-package version` and state the target before applying it. Do not
rely on memory for a changing version number.

Compare the target with the installed version and classify the update as patch,
minor, or major. For a major update—or any package with native code, a compiler,
bundler, framework, test runner, plugin API, or security-sensitive behavior:

- Read the official release notes, migration guide, and compatibility table.
- Check `engines`, `peerDependencies`, exports, supported module formats, and
  known changes affecting this repository.
- Identify coupled packages that must move together (for example a plugin and
  its host, a renderer and its types, or a framework and its adapter). Include
  only those packages in the upgrade scope and explain why.

If the package is not a direct dependency, determine whether the correct action
is to update its direct parent, add an explicit constraint, or use an existing
override/resolution. Do not add a direct dependency merely to pin a transitive
package without checking the repository's policy and compatibility.

### 3. Apply one targeted update

Update the owning manifest while preserving its dependency section and existing
range style. Run the targeted Yarn command from the owning package directory, or
scope it to the correct workspace:

```bash
yarn add the-package@<target>
yarn add --dev the-package@<target>
yarn workspace <workspace> add the-package@<target>
```

Use `yarn add --dev` for a devDependency and `yarn add` for a runtime
dependency. If a targeted command would move the package to the wrong dependency
section, make the smallest manifest edit allowed by repository conventions, then
run Yarn to regenerate the lockfile. Never use a broad `yarn upgrade --latest`
invocation without a package filter.

If resolution fails, inspect the peer-dependency and engine error, the package's
published metadata, and the existing overrides before trying anything else.
Resolve the actual incompatibility by selecting a compatible target or updating
the necessary coupled package. Do not suppress the error with a force flag.

### 4. Verify the dependency graph and diff

After the package manager finishes:

- Confirm the manifest, lockfile, and workspace metadata agree.
- Verify the resolved version with `yarn why` and check for invalid or unmet
  peers.
- Review `git diff` for the intended manifest entry, required transitive
  changes, integrity data, and no accidental upgrades or formatting churn.
- Check that the lockfile remains reproducible using the repository's frozen or
  CI-style install mode, `yarn install --frozen-lockfile`, when practical.

Do not discard unrelated changes in a dirty worktree. If Yarn rewrites broad
lockfile sections, determine whether they are required. If not, stop and use a
narrower workspace command or ask for direction rather than committing noisy
churn.

### 5. Validate affected behavior

Run the smallest complete validation set that covers the package's consumers,
then broaden it when the package is foundational or the repository requires a
full suite. Typical checks include:

- the package's focused unit or integration tests;
- type checking, linting, and formatting for changed files;
- the affected build, bundle, native compilation, or packaging command;
- relevant browser, Electron, mobile, or end-to-end smoke tests;
- the full test/build matrix for major, security, native, or central toolchain
  upgrades.

For a package with changed runtime behavior, add a regression or integration
test before declaring success. Do not write tests for the third-party package
itself. Record commands that could not run and why (missing dependencies,
platform toolchain, network, credentials, or unrelated pre-existing failure).

### 6. Hand off clearly

Report the old and new versions, owning manifest/workspace, direct or coupled
changes, lockfile impact, validation performed, and any remaining compatibility
risk. Keep the change reviewable and do not create a commit, issue, or pull
request unless the user explicitly requests it.

## Common cases

### A package has a peer-dependency conflict

Inspect the complete peer chain and identify which installed package violates
it. Prefer a compatible version range or the smallest coupled upgrade. Keep peer
warnings visible; do not hide them with `--force` or `--ignore-engines`.

### The requested package is only transitive

Trace it to its direct parent and check whether the parent has released a fix.
Update the parent when possible. Use an override/resolution only when the
repository already supports that mechanism and the target is demonstrably
compatible; validate the resulting graph and document the reason.

### The update is a security fix

Confirm the advisory and affected range, prefer the narrowest patched stable
version, and run tests for every code path exposed by the vulnerable package. Do
not expand the work into unrelated modernization unless required to reach a
patched compatible version.

### The package has native bindings

Check Node and platform support, ABI/prebuild availability, postinstall steps,
and packaging targets. Run the relevant native build and at least one runtime
smoke test on the affected host. A successful install alone is insufficient.

### The package is a build or test tool

Validate the generated artifact or test execution, not just installation. For
loaders, plugins, formatters, and bundlers, check the configuration path and a
representative real input so export or option changes are detected.
