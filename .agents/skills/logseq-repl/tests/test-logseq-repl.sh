#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILL_DIR="$(cd "$TEST_DIR/.." && pwd)"
START_SCRIPT="$SKILL_DIR/scripts/start-repl.sh"
START_PY_SCRIPT="$SKILL_DIR/scripts/start-repl.py"
CLEANUP_SCRIPT="$SKILL_DIR/scripts/cleanup-repl.sh"
VERIFY_SCRIPT="$SKILL_DIR/scripts/verify-repls.sh"
COMMON_SCRIPT="$SKILL_DIR/scripts/common.sh"
SKILL_FILE="$SKILL_DIR/SKILL.md"
ORIGINAL_PATH="$PATH"

# shellcheck disable=SC1091
source "$TEST_DIR/test-lib.sh"

PASS_COUNT=0
FAIL_COUNT=0

create_fake_env() {
  TEST_ROOT="$(mktemp -d)"
  REPO_ROOT="$TEST_ROOT/repo"
  BIN_DIR="$TEST_ROOT/bin"
  CMD_LOG="$TEST_ROOT/commands.log"

  mkdir -p "$REPO_ROOT/static" "$BIN_DIR"
  : > "$CMD_LOG"

  cat > "$BIN_DIR/bb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

echo "bb $*" >> "$FAKE_CMD_LOG"

case "${1:-}" in
  watch)
    echo "Waiting for the initial desktop build..."
    echo "Desktop watcher is ready."
    while true; do sleep 1; done
    ;;
  electron-start)
    echo "Starting Electron..."
    echo "02:00:00.000 Logseq App(1.0.0) Starting..."
    echo "shadow-cljs - #4 ready!"
    while true; do sleep 1; done
    ;;
  clj-nrepl-eval)
    input="$(cat)"

    if [[ "$input" == *"repl-runtimes :app"* ]]; then
      echo "${FAKE_APP_RUNTIME_COUNT:-1}"
      exit 0
    fi

    if [[ "$input" == *"repl-runtimes :electron"* ]]; then
      echo "${FAKE_ELECTRON_RUNTIME_COUNT:-1}"
      exit 0
    fi

    if [[ "$input" == *"shadow.user/cljs-repl"* ]]; then
      echo "=> [:selected :app]"
      exit 0
    fi

    if [[ "$input" == *"shadow.user/electron-repl"* ]]; then
      echo "=> [:selected :electron]"
      exit 0
    fi

    if [[ "$input" == *":runtime :app"* ]]; then
      echo 'cljs.user=> {:runtime :app, :document? true}'
      exit 0
    fi

    if [[ "$input" == *":runtime :electron"* ]]; then
      echo 'cljs.user=> {:runtime :electron, :process? true, :type "browser"}'
      exit 0
    fi

    echo "Unexpected nREPL form: $input" >&2
    exit 1
    ;;
  *)
    echo "Unexpected bb command: $*" >&2
    exit 1
    ;;
esac
EOF

  cat > "$BIN_DIR/ss" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF

  chmod +x "$BIN_DIR/bb" "$BIN_DIR/ss"

  export PATH="$BIN_DIR:$ORIGINAL_PATH"
  export FAKE_CMD_LOG="$CMD_LOG"
  export FAKE_APP_RUNTIME_COUNT="${FAKE_APP_RUNTIME_COUNT:-1}"
  export FAKE_ELECTRON_RUNTIME_COUNT="${FAKE_ELECTRON_RUNTIME_COUNT:-1}"
}

cleanup_fake_env() {
  if [[ -n "${REPO_ROOT:-}" ]]; then
    local pid_file pid
    for pid_file in "$REPO_ROOT"/tmp/logseq-repl/*.pid; do
      [[ -e "$pid_file" ]] || continue
      pid="$(tr -d '[:space:]' < "$pid_file")"
      if [[ "$pid" =~ ^[0-9]+$ ]]; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    done
  fi

  PATH="$ORIGINAL_PATH"
  unset FAKE_CMD_LOG FAKE_APP_RUNTIME_COUNT FAKE_ELECTRON_RUNTIME_COUNT || true

  if [[ -n "${TEST_ROOT:-}" && -d "$TEST_ROOT" ]]; then
    rm -rf "$TEST_ROOT"
  fi
}

scripts_exist_test() {
  assert_file_exists "$START_SCRIPT"
  assert_file_exists "$START_PY_SCRIPT"
  assert_file_exists "$CLEANUP_SCRIPT"
  assert_file_exists "$VERIFY_SCRIPT"
  assert_file_exists "$COMMON_SCRIPT"
}

start_launches_fork_repl_processes_without_attaching_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  bash "$START_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/start.log" 2>&1

  assert_contains "Verifying Shadow CLJS REPL targets ..." "$TEST_ROOT/start.log"
  assert_contains "REPL verification passed for :app" "$TEST_ROOT/start.log"
  assert_contains "REPL verification passed for :electron" "$TEST_ROOT/start.log"
  assert_contains "Startup complete. Attach to the needed REPL manually." "$TEST_ROOT/start.log"
  assert_contains "bb clj-nrepl-eval -p 8701 --reset-session" "$TEST_ROOT/start.log"
  assert_contains "bb watch" "$CMD_LOG"
  assert_contains "bb electron-start" "$CMD_LOG"
  assert_file_exists "$REPO_ROOT/tmp/logseq-repl/shared-shadow-watch.pid"
  assert_file_exists "$REPO_ROOT/tmp/logseq-repl/desktop-electron.pid"
  assert_file_exists "$REPO_ROOT/tmp/logseq-repl/shared-shadow-watch.log"
  assert_file_exists "$REPO_ROOT/tmp/logseq-repl/desktop-electron.log"
}

verify_script_checks_both_targets_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  bash "$VERIFY_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/verify.log" 2>&1

  assert_contains "Verifying Shadow CLJS REPL targets ..." "$TEST_ROOT/verify.log"
  assert_contains "REPL verification passed for :app" "$TEST_ROOT/verify.log"
  assert_contains "REPL verification passed for :electron" "$TEST_ROOT/verify.log"
  assert_contains "All Shadow CLJS REPL targets verified." "$TEST_ROOT/verify.log"
  assert_contains "bb clj-nrepl-eval" "$CMD_LOG"
}

verify_script_fails_when_target_repl_fails_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  cat > "$BIN_DIR/bb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

input="$(cat)"
if [[ "$input" == *"shadow.user/electron-repl"* ]]; then
  echo "=> [:selected :electron]"
  exit 0
fi
if [[ "$input" == *":runtime :electron"* ]]; then
  echo "No available JS runtime" >&2
  exit 1
fi
if [[ "$input" == *"shadow.user/cljs-repl"* ]]; then
  echo "=> [:selected :app]"
  exit 0
fi
if [[ "$input" == *":runtime :app"* ]]; then
  echo 'cljs.user=> {:runtime :app, :document? true}'
  exit 0
fi
exit 0
EOF
  chmod +x "$BIN_DIR/bb"

  if bash "$VERIFY_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/verify.log" 2>&1; then
    fail "expected verify script to fail when one target REPL fails"
  fi

  assert_contains "Error: REPL verification failed for :electron." "$TEST_ROOT/verify.log"
  assert_contains "No available JS runtime" "$TEST_ROOT/verify.log"
}

start_reuses_running_processes_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  bash "$START_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/first.log" 2>&1
  bash "$START_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/second.log" 2>&1

  assert_equals "1" "$(grep -c '^bb watch$' "$CMD_LOG")"
  assert_equals "1" "$(grep -c '^bb electron-start$' "$CMD_LOG")"
  assert_contains "Reusing desktop watcher" "$TEST_ROOT/second.log"
  assert_contains "Reusing Desktop dev app" "$TEST_ROOT/second.log"
}

start_rejects_removed_worker_options_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  if bash "$START_SCRIPT" --repo-root "$REPO_ROOT" --repo demo > "$TEST_ROOT/start.log" 2>&1; then
    fail "expected worker-only options to be rejected by the fork workflow"
  fi

  assert_contains "unrecognized arguments: --repo demo" "$TEST_ROOT/start.log"
}

start_fails_when_standard_port_is_occupied_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  cat > "$BIN_DIR/ss" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "LISTEN 0 1000 127.0.0.1:8701 0.0.0.0:*"
EOF
  chmod +x "$BIN_DIR/ss"

  if bash "$START_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/start.log" 2>&1; then
    fail "expected start script to fail when a standard port is occupied"
  fi

  assert_contains "Port 8701 is already listening" "$TEST_ROOT/start.log"
  assert_contains "standard Logseq development ports are occupied" "$TEST_ROOT/start.log"
  assert_not_contains_text "bb watch" "$CMD_LOG"
}

start_fails_when_app_runtime_is_ambiguous_test() {
  create_fake_env
  trap cleanup_fake_env RETURN
  export FAKE_APP_RUNTIME_COUNT=2

  if bash "$START_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/start.log" 2>&1; then
    fail "expected start script to fail when more than one :app runtime exists"
  fi

  assert_contains "Expected exactly one live :app runtime" "$TEST_ROOT/start.log"
}

cleanup_stops_all_fork_repl_processes_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  bash "$START_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/start.log" 2>&1

  local watch_pid desktop_pid
  watch_pid="$(tr -d '[:space:]' < "$REPO_ROOT/tmp/logseq-repl/shared-shadow-watch.pid")"
  desktop_pid="$(tr -d '[:space:]' < "$REPO_ROOT/tmp/logseq-repl/desktop-electron.pid")"

  bash "$CLEANUP_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/cleanup.log" 2>&1

  if kill -0 "$watch_pid" 2>/dev/null; then
    fail "expected desktop watcher to stop"
  fi

  if kill -0 "$desktop_pid" 2>/dev/null; then
    fail "expected Desktop dev app to stop"
  fi

  assert_not_exists "$REPO_ROOT/tmp/logseq-repl/shared-shadow-watch.pid"
  assert_not_exists "$REPO_ROOT/tmp/logseq-repl/desktop-electron.pid"
  assert_contains "Cleanup done." "$TEST_ROOT/cleanup.log"
}

cleanup_removes_legacy_desktop_state_files_test() {
  create_fake_env
  trap cleanup_fake_env RETURN

  mkdir -p "$REPO_ROOT/tmp/desktop-app-repl" "$REPO_ROOT/tmp/logseq-repl"
  sleep 30 &
  local legacy_pid=$!
  echo "$legacy_pid" > "$REPO_ROOT/tmp/desktop-app-repl/desktop-electron.pid"

  bash "$CLEANUP_SCRIPT" --repo-root "$REPO_ROOT" > "$TEST_ROOT/cleanup.log" 2>&1

  if kill -0 "$legacy_pid" 2>/dev/null; then
    fail "expected legacy desktop pid to stop"
  fi

  assert_not_exists "$REPO_ROOT/tmp/desktop-app-repl/desktop-electron.pid"
}

help_and_docs_describe_fork_scripts_test() {
  local temp_dir start_help cleanup_help
  temp_dir="$(mktemp -d)"
  start_help="$temp_dir/start-help.txt"
  cleanup_help="$temp_dir/cleanup-help.txt"

  bash "$START_SCRIPT" --help > "$start_help"
  bash "$CLEANUP_SCRIPT" --help > "$cleanup_help"

  assert_contains "start-repl.sh" "$start_help"
  assert_contains "start-repl.py" "$SKILL_FILE"
  assert_contains "cleanup-repl.sh" "$cleanup_help"
  assert_contains "bb watch" "$SKILL_FILE"
  assert_contains "bb electron-start" "$SKILL_FILE"
  assert_contains "bb clj-nrepl-eval" "$SKILL_FILE"
  assert_not_contains_text "pnpm" "$SKILL_FILE"
  assert_not_contains_text "cljs-repl db-worker-node" "$SKILL_FILE"
  assert_not_contains_text "node ./static/db-worker-node.js" "$SKILL_FILE"
  assert_not_contains_text "start-desktop-app-repl.sh" "$SKILL_FILE"
  assert_not_contains_text "start-db-worker-node-repl.sh" "$SKILL_FILE"

  rm -rf "$temp_dir"
}

run_test "scripts exist" scripts_exist_test
run_test "start launches fork REPL processes without attaching" start_launches_fork_repl_processes_without_attaching_test
run_test "verify script checks both targets" verify_script_checks_both_targets_test
run_test "verify script fails when target REPL fails" verify_script_fails_when_target_repl_fails_test
run_test "start reuses running processes" start_reuses_running_processes_test
run_test "start rejects removed worker options" start_rejects_removed_worker_options_test
run_test "start fails when standard port is occupied" start_fails_when_standard_port_is_occupied_test
run_test "start fails when app runtime is ambiguous" start_fails_when_app_runtime_is_ambiguous_test
run_test "cleanup stops all fork REPL processes" cleanup_stops_all_fork_repl_processes_test
run_test "cleanup removes legacy desktop state" cleanup_removes_legacy_desktop_state_files_test
run_test "help and docs describe fork scripts" help_and_docs_describe_fork_scripts_test

echo
if [[ "$FAIL_COUNT" -gt 0 ]]; then
  echo "$FAIL_COUNT test(s) failed; $PASS_COUNT passed." >&2
  exit 1
fi

echo "All $PASS_COUNT test(s) passed."
