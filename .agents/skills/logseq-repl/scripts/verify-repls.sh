#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_REPO_ROOT="$(cd "$SCRIPT_DIR/../../../.." && pwd)"
REPO_ROOT="${REPO_ROOT:-$DEFAULT_REPO_ROOT}"
NREPL_PORT="${LOGSEQ_REPL_PORT:-8701}"

usage() {
  cat <<'EOF'
Verify that the Logseq OG Shadow CLJS REPL targets are usable.

Usage:
  verify-repls.sh [options]

Options:
  --repo-root <path>    Logseq repository root (default: auto-detect from script location)
  --port <port>         Shadow nREPL port (default: $LOGSEQ_REPL_PORT or 8701)
  -h, --help            Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo-root)
      shift
      REPO_ROOT="${1:?missing value for --repo-root}"
      ;;
    --port)
      shift
      NREPL_PORT="${1:?missing value for --port}"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

if [[ ! -d "$REPO_ROOT" ]]; then
  echo "Error: repo root not found: $REPO_ROOT" >&2
  exit 1
fi

if ! [[ "$NREPL_PORT" =~ ^[0-9]+$ ]]; then
  echo "Error: nREPL port must be an integer: $NREPL_PORT" >&2
  exit 1
fi

if ! command -v bb >/dev/null 2>&1; then
  echo "Error: bb not found in PATH" >&2
  exit 1
fi

verify_target() {
  local target="$1"
  local eval_form="$2"
  local marker="$3"

  echo "Checking :$target ..."

  local repl_output
  pushd "$REPO_ROOT" >/dev/null
  if ! repl_output="$(printf '%s\n' "$eval_form" | bb clj-nrepl-eval --port "$NREPL_PORT" --reset-session 2>&1)"; then
    popd >/dev/null
    echo "Error: REPL verification failed for :$target." >&2
    echo "--- :$target output ---" >&2
    echo "$repl_output" >&2
    echo "-----------------------" >&2
    return 1
  fi
  popd >/dev/null

  if [[ "$repl_output" != *"$marker"* ]]; then
    echo "Error: REPL verification returned an unexpected result for :$target." >&2
    echo "--- :$target output ---" >&2
    echo "$repl_output" >&2
    echo "-----------------------" >&2
    return 1
  fi

  echo "--- :$target result ---"
  echo "$repl_output"
  echo "-----------------------"
  echo "REPL verification passed for :$target"
}

echo "Verifying Shadow CLJS REPL targets ..."
verify_target app \
  '(do (require (quote [shadow.cljs.devtools.api :as api])) (println (api/cljs-eval :app "(prn {:runtime :app :document? (some? js/document)})" {})))' \
  "{:runtime :app"
verify_target electron \
  '(do (require (quote [shadow.cljs.devtools.api :as api])) (println (api/cljs-eval :electron "(prn {:runtime :electron :process? (some? js/process) :type (some-> js/process .-type)})" {})))' \
  "{:runtime :electron"
echo "All Shadow CLJS REPL targets verified."
