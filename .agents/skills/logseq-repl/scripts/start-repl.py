#!/usr/bin/env python3
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO_ROOT = SCRIPT_DIR.parents[3]
STANDARD_PORTS = (8701, 3001, 9630, 9631)
try:
    NREPL_PORT = int(os.environ.get("LOGSEQ_REPL_PORT", "8701"))
except ValueError as exc:
    raise SystemExit("Error: LOGSEQ_REPL_PORT must be an integer") from exc

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(line_buffering=True)
    sys.stderr.reconfigure(line_buffering=True)


def parse_args():
    parser = argparse.ArgumentParser(
        prog="start-repl.sh",
        description="Start the Logseq OG Desktop REPL workflow.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        allow_abbrev=False,
        epilog=(
            "This starts the repository's bb watch task and Electron desktop app,\n"
            "verifies the :app and :electron runtimes, and exits."
        ),
    )
    parser.add_argument(
        "--repo-root",
        default=os.environ.get("REPO_ROOT", str(DEFAULT_REPO_ROOT)),
        help="Logseq repository root (default: auto-detect from script location)",
    )
    return parser.parse_args()


def require_command(name):
    if shutil.which(name) is None:
        raise SystemExit(f"Error: {name} not found in PATH")


def read_pid(path):
    try:
        text = path.read_text().strip()
    except FileNotFoundError:
        return None
    return int(text) if re.fullmatch(r"\d+", text) else None


def is_running(pid):
    if not pid:
        return False
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def write_pid(path, pid):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"{pid}\n")


def wait_for_patterns(path, timeout, patterns, all_required=True):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.exists():
            text = path.read_text(errors="replace")
            if all_required and all(pattern in text for pattern in patterns):
                return True
            if not all_required and any(pattern in text for pattern in patterns):
                return True
        time.sleep(1)
    return False


def process_group_kwargs():
    if os.name == "nt":
        return {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
    return {"start_new_session": True}


def start_process(repo_root, log_path, command):
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("wb") as log_file:
        return subprocess.Popen(
            command,
            cwd=repo_root,
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            **process_group_kwargs(),
        )


def port_listener_output(port):
    if shutil.which("ss") is not None:
        try:
            return subprocess.run(
                ["ss", "-H", "-ltn", f"( sport = :{port} )"],
                text=True,
                capture_output=True,
                check=False,
            ).stdout
        except OSError:
            pass

    if shutil.which("lsof") is not None:
        try:
            return subprocess.run(
                ["lsof", "-nP", f"-iTCP:{port}", "-sTCP:LISTEN"],
                text=True,
                capture_output=True,
                check=False,
            ).stdout
        except OSError:
            pass

    return None


def has_managed_processes(paths):
    return any(is_running(read_pid(path)) for path in paths)


def require_clean_ports():
    had_conflict = False
    audit_unavailable = False
    for port in STANDARD_PORTS:
        output = port_listener_output(port)
        if output is None:
            audit_unavailable = True
            continue
        if output.strip():
            had_conflict = True
            print(f"Port {port} is already listening:", file=sys.stderr)
            print(output.rstrip(), file=sys.stderr)

    if audit_unavailable:
        print("Warning: neither ss nor lsof is available; skipping port audit.", file=sys.stderr)
    if had_conflict:
        print("Error: standard Logseq development ports are occupied.", file=sys.stderr)
        print("Stop the conflicting process, then retry.", file=sys.stderr)
        return False
    return True


def ensure_shadow_watch(repo_root, shadow_pid_file, shadow_log):
    pid = read_pid(shadow_pid_file)
    if is_running(pid):
        print(f"Reusing desktop watcher (pid={pid})")
        return pid

    print("Starting desktop watcher via bb watch ...")
    process = start_process(repo_root, shadow_log, ["bb", "watch"])
    write_pid(shadow_pid_file, process.pid)
    time.sleep(1)

    if process.poll() is not None:
        raise SystemExit(f"Error: bb watch exited early. Check {shadow_log}")

    if not wait_for_patterns(shadow_log, 300, ["Desktop watcher is ready."]):
        raise SystemExit(f"Error: bb watch did not finish the initial build in time. Check {shadow_log}")

    print("Desktop watcher is ready")
    return process.pid


def ensure_desktop_app(repo_root, desktop_pid_file, desktop_log):
    pid = read_pid(desktop_pid_file)
    if is_running(pid):
        print(f"Reusing Desktop dev app (pid={pid})")
        return pid

    print("Starting Desktop dev app via bb electron-start ...")
    process = start_process(repo_root, desktop_log, ["bb", "electron-start"])
    write_pid(desktop_pid_file, process.pid)
    time.sleep(1)

    if process.poll() is not None:
        raise SystemExit(f"Error: bb electron-start exited early. Check {desktop_log}")

    if not wait_for_patterns(desktop_log, 120, ["Starting Electron..."], all_required=False):
        raise SystemExit(f"Error: Desktop dev app did not start Electron in time. Check {desktop_log}")

    print(f"Desktop dev app is running (pid={process.pid})")
    return process.pid


def runtime_count(repo_root, build_name):
    form = (
        "(do (require '[shadow.cljs.devtools.api :as api]) "
        f"(println (count (api/repl-runtimes :{build_name}))))"
    )
    proc = subprocess.run(
        ["bb", "clj-nrepl-eval", "--port", str(NREPL_PORT), "--reset-session"],
        cwd=repo_root,
        input=form + "\n",
        text=True,
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        raise SystemExit(
            f"Error: failed to inspect :{build_name} runtimes.\n"
            f"--- nREPL output ---\n{proc.stdout}{proc.stderr}\n--------------------"
        )
    matches = re.findall(r"^(\d+)$", proc.stdout + proc.stderr, flags=re.MULTILINE)
    if not matches:
        raise SystemExit(
            f"Error: could not parse :{build_name} runtime count.\n"
            f"--- nREPL output ---\n{proc.stdout}{proc.stderr}\n--------------------"
        )
    return int(matches[-1])


def wait_for_runtime_count(repo_root, build_name, expected, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        count = runtime_count(repo_root, build_name)
        if expected == "exactly-one":
            if count == 1:
                print(f"Detected exactly one live :{build_name} runtime")
                return
            if count != 0:
                raise SystemExit(f"Error: Expected exactly one live :{build_name} runtime, found {count}.")
        elif expected == "nonzero" and count != 0:
            print(f"Detected live :{build_name} runtime count: {count}")
            return
        time.sleep(1)

    if expected == "exactly-one":
        raise SystemExit(f"Error: Expected exactly one live :{build_name} runtime, found 0 after waiting.")
    raise SystemExit(f"Error: expected a live :{build_name} runtime, but runtime count stayed 0.")


def verify_repls(repo_root):
    subprocess.run([str(SCRIPT_DIR / "verify-repls.sh"), "--repo-root", str(repo_root)], check=True)


def print_summary(shadow_log, desktop_log, shadow_pid_file, desktop_pid_file):
    print()
    print("Logs:")
    print(f"  desktop watcher: {shadow_log}")
    print(f"  desktop-app:     {desktop_log}")
    print("PID files:")
    print(f"  {shadow_pid_file}")
    print(f"  {desktop_pid_file}")
    print()
    print("Build selection helpers:")
    print(f'  bb clj-nrepl-eval -p {NREPL_PORT} --reset-session "(shadow.user/cljs-repl)"')
    print(f'  bb clj-nrepl-eval -p {NREPL_PORT} --reset-session "(shadow.user/electron-repl)"')
    print()
    print("After selecting a build, evaluate forms with bb clj-nrepl-eval on the same port.")
    print("Startup complete. Attach to the needed REPL manually.")


def main():
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    if not repo_root.is_dir():
        raise SystemExit(f"Error: repo root not found: {repo_root}")

    require_command("bb")

    log_dir = repo_root / "tmp" / "logseq-repl"
    log_dir.mkdir(parents=True, exist_ok=True)

    shadow_pid_file = log_dir / "shared-shadow-watch.pid"
    desktop_pid_file = log_dir / "desktop-electron.pid"
    shadow_log = log_dir / "shared-shadow-watch.log"
    desktop_log = log_dir / "desktop-electron.log"

    managed_pid_files = [shadow_pid_file, desktop_pid_file]

    if not has_managed_processes(managed_pid_files) and not require_clean_ports():
        return 1

    ensure_shadow_watch(repo_root, shadow_pid_file, shadow_log)
    ensure_desktop_app(repo_root, desktop_pid_file, desktop_log)
    wait_for_runtime_count(repo_root, "app", "exactly-one", 120)
    wait_for_runtime_count(repo_root, "electron", "nonzero", 120)
    verify_repls(repo_root)
    print_summary(shadow_log, desktop_log, shadow_pid_file, desktop_pid_file)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
