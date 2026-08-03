#!/usr/bin/env bash

logseq_repl_is_running_pid() {
  local pid="$1"
  [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

logseq_repl_read_pid() {
  local file="$1"
  if [[ -f "$file" ]]; then
    tr -d '[:space:]' < "$file"
  fi
}

logseq_repl_port_check_line() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -H -ltn "( sport = :$port )" 2>/dev/null || true
  elif command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
  fi
}

logseq_repl_audit_standard_ports() {
  local had_conflict=0
  local ports=(8701 3001 9630 9631)
  local port output

  for port in "${ports[@]}"; do
    output="$(logseq_repl_port_check_line "$port")"
    if [[ -n "$output" ]]; then
      had_conflict=1
      echo "Port $port is already listening:" >&2
      echo "$output" >&2
      echo >&2
    fi
  done

  return "$had_conflict"
}

logseq_repl_require_clean_standard_ports() {
  if logseq_repl_audit_standard_ports; then
    return 0
  fi

  echo "Error: standard Logseq development ports are still occupied after cleanup." >&2
  echo "Resolve the external conflict first, then retry." >&2
  return 1
}

logseq_repl_runtime_count() {
  local repo_root="$1"
  local build_name="$2"
  local output
  local form="(do (require '[shadow.cljs.devtools.api :as api]) (println (count (api/repl-runtimes :$build_name))))"

  pushd "$repo_root" >/dev/null || return 1
  if ! output="$(printf '%s\n' "$form" | bb clj-nrepl-eval --port 8701 --reset-session 2>&1)"; then
    popd >/dev/null || return 1
    echo "Error: failed to inspect :$build_name runtimes." >&2
    echo "--- nREPL output ---" >&2
    echo "$output" >&2
    echo "--------------------" >&2
    return 1
  fi
  popd >/dev/null || return 1

  local runtime_count
  runtime_count="$(printf '%s\n' "$output" | awk '/^[0-9]+$/{n=$0} END{if (n != "") print n; else exit 1}')" || {
    echo "Error: could not parse :$build_name runtime count." >&2
    echo "--- nREPL output ---" >&2
    echo "$output" >&2
    echo "--------------------" >&2
    return 1
  }

  printf '%s\n' "$runtime_count"
}
