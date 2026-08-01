root_dir := justfile_directory()

[private]
default:
    @just --list

# Package desktop apps using Electron Forge
[group('Release')]
package:
    yarn release-electron

# Format all code
[group('Quality')]
format:
    @echo "Formatting Clojure/ClojureScript code..."
    bb format || echo "Add a bb format task if needed"

    @echo "Formatting JavaScript, TypeScript, and CSS..."
    npx prettier --write .

    @echo "Formatting Nix code..."
    alejandra .

# Lint all code
[group('Quality')]
lint: check-version
    @echo "Linting CSS..."
    npx stylelint "src/**/*.css"

    @echo "Linting Clojure/ClojureScript code..."
    bb cljs:lint

# Run all tests (including E2E)
[group('Quality')]
test:
    @echo "Running ClojureScript unit tests..."
    bb cljs:test
    bb cljs:run-test

    @echo "Running Playwright E2E tests..."
    yarn e2e-test

# Check that version matches the VERSION file
[group('Quality')]
check-version:
    #!/usr/bin/env bash
    set -euo pipefail

    echo "Checking if versions match the VERSION file..."

    EXPECTED=$(cat VERSION | tr -d '[:space:]')
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

# Clean up workspace
[group('Utility')]
clean:
    git clean -dxf -e .jj -e .envrc

# -----------------------------------------------------
# Development Watchers
# -----------------------------------------------------

# Watch everything (Gulp + ClojureScript)
[group('Development')]
watch:
    @echo "Starting watch for both JS/Gulp and ClojureScript..."
    yarn gulp:watch & bb cljs:watch & wait

# Watch app
[group('Development')]
app-watch:
    yarn gulp:watch & bb cljs:app-watch & wait

# Watch electron
[group('Development')]
electron-watch:
    yarn gulp:watch & bb cljs:electron-watch & wait

# -----------------------------------------------------
# Release Builds
# -----------------------------------------------------

# Build complete release
[group('Release')]
release:
    yarn gulp:build
    bb cljs:release

# Build app release
[group('Release')]
release-app:
    yarn gulp:build
    bb cljs:release-app

# Build dev release app
[group('Release')]
dev-release-app:
    yarn gulp:build
    bb cljs:dev-release-app

# -----------------------------------------------------
# Mobile Deployments
# -----------------------------------------------------

# Run Android release
[group('Mobile')]
run-android-release:
    yarn clean
    just release-app
    rm -rf ./public/static
    rm -rf ./static/js/*.map
    mv static ./public
    npx cap sync android
    npx cap run android

# Run iOS release
[group('Mobile')]
run-ios-release:
    yarn clean
    just release-app
    rm -rf ./public/static
    rm -rf ./static/js/*.map
    mv static ./public
    npx cap sync ios
    npx cap run ios
