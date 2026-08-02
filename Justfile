[private]
default:
    @just --list

# Open Electron using the watcher from `bb watch`.
[group('Development')]
electron:
    bb electron-start

# Build assets and start the desktop asset and ClojureScript watchers.
[group('Development')]
watch:
    bb watch

# Build the publishing application. Pass --dev for watch mode.
[group('Development')]
publishing *args:
    bb dev:publishing {{ args }}

# Run ClojureScript unit tests.
[group('Quality')]
test:
    bb test

# Run Playwright end-to-end tests.
[group('Quality')]
test-e2e:
    yarn test:e2e

# Run all tests.
[group('Quality')]
test-all: test test-e2e

# Lint all code.
[group('Quality')]
lint: check-version
    bb fmt:check
    bb lint
    yarn fmt:check
    yarn lint
    yarn css:lint

# Format all code.
[group('Quality')]
fmt:
    bb fmt
    yarn fmt
    nix fmt

# Check that project versions match VERSION file.
[group('Quality')]
check-version:
    #!/usr/bin/env bash
    set -euo pipefail

    echo "Checking if versions match the VERSION file..."

    EXPECTED=$(tr -d '[:space:]' < VERSION)
    PKG_VER=$(jq -r '.version' package.json)
    if [ "$EXPECTED" != "$PKG_VER" ]; then
      echo "Error: package.json version ($PKG_VER) does not match VERSION ($EXPECTED)"
      exit 1
    fi

    CLJS_VER=$(sed -n 's/.*(defonce version "\([^"]*\)").*/\1/p' src/main/frontend/version.cljs)
    if [ "$EXPECTED" != "$CLJS_VER" ]; then
      echo "Error: version.cljs version ($CLJS_VER) does not match VERSION ($EXPECTED)"
      exit 1
    fi

    echo "Versions match!"

# Package the Electron application.
[group('Release')]
package: check-version
    bb release:electron

# Build all production browser, publishing, and Electron assets.
[group('Release')]
release: check-version
    bb build:release

# Build the production browser application.
[group('Release')]
release-app: check-version
    bb build:app

# Build the development browser application.
[group('Release')]
dev-release-app: check-version
    bb build:dev-app

# Remove generated static assets.
[group('Utility')]
clean:
    yarn clean

# Remove all ignored and untracked files. This is intentionally destructive.
[group('Utility')]
purge:
    git clean -dxf -e .jj -e .envrc
