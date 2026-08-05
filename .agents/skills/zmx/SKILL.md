---
name: zmx
description: Manage persistent zmx terminal sessions and run commands with durable shell state. Use this skill when work needs a persistent terminal session whose shell state, background processes, and scrollback survive between commands or disconnections.
---

# ZMX Session Workflow

`zmx` provides persistent persistent terminal sessions (PTY).

- Multiple clients can connect to the same session
- Re-attaching to a session restores previous terminal state and output
- You can send commands to a session without attaching to it
- You can print scrollback history of a terminal session in plain text

## Prerequisite

Check that the CLI is available before using the workflow:

```bash
command -v zmx && zmx version
```

If it is missing, then stop and inform the user. **Do not substitute `tmux` or
a shell background process**, those do not provide the zmx session contract.

## Session lifecycle

A session is identified by a name. Names should be stable and descriptive, for
example `logseq-dev` or `api-tests`.

List active sessions:

```bash
zmx list
zmx list --short
```

Create or open a session without taking over the current terminal. `run` is an
upsert for the session and preserves the session's working directory and
exported environment:

```bash
zmx run <session> true
```

Use `zmx attach` when a real interactive terminal is required. It creates the
session if it does not exist:

```bash
zmx attach <session>
```

`zmx attach` is long-lived and interactive. Start it with the process manager
(`hub start`, application `zmx`, args `["attach", "<session>"]`) rather than a
blocking shell command. Give the process a stable name such as
`zmx-attach-<session>`. Detach with `Ctrl+\`, close the terminal client, or run
`zmx detach` from another terminal; do not kill the session merely to leave it.

For ordinary agent work, prefer `zmx run` over attaching. Attach only for
interactive programs or prompts that require a human, such as `sudo`, a
password prompt, `vim`, `htop`, a TUI, or an interactive rebase.

## Run commands with persistent state

Run a non-interactive command directly in the session:

```bash
zmx run <session> <argv>...
```

Arguments are passed directly; zmx does not add a shell wrapper. Pass each
argument separately:

```bash
zmx run logseq-dev git status --short
zmx run logseq-dev bb test
```

Shell operators and expansions are literal arguments, so use an explicit shell
when chaining is intentional:

```bash
zmx run logseq-dev sh -c 'export MODE=test && ./script.sh "$MODE"'
```

Do not use `run` for interactive programs: they wait for input and can hang the
caller. For a long-running non-interactive command, detach the zmx client and
wait on the session task:

```bash
zmx run <session> -d <argv>...
zmx wait <session>
```

A detached run has no live output stream in the caller. Inspect its scrollback
with `zmx history` after `zmx wait`.

## Send raw terminal input

Use `send` when the process is interactive or when the exact PTY bytes matter:

```bash
# send is fire-and-forget and does not append Enter
printf 'yes\r' | zmx send <session>

# Ctrl-C
zmx send <session> "$(printf '\003')"
```

`zmx send` sends bytes to the PTY. It does not track completion or return an
exit status for the process. Append `\r` explicitly when the target should
receive Enter. Use `zmx run` when completion and command status matter.

## Follow and inspect output

Follow a session's scrollback and subsequent output with `tail`:

```bash
zmx tail <session>
```

`zmx tail` streams existing scrollback and subsequent output while attached. It
may exit when there is no active task or when the session closes. Start it
through `hub start` with application `zmx` and args `["tail", "<session>"]`,
using a stable process name such as `zmx-tail-<session>`. Stop that follower
with `hub stop` when finished; stopping the follower does not kill the zmx
session.

For a bounded snapshot, use history:

```bash
zmx history <session>
zmx history <session> --vt
zmx history <session> --html
```

Use plain text by default. `--vt` and `--html` preserve terminal formatting for
consumers that understand those formats. If the session does not exist, report
the zmx error instead of creating a replacement session just to inspect it.

After a detached or raw-input operation, the usual sequence is:

```bash
zmx wait <session>
zmx history <session>
```

`wait` is meaningful for detached `run` tasks. It does not make a raw `send`
operation synchronous; use history or a known prompt/output condition to verify
an interactive action.

## Kill and cleanup

Kill named sessions only when their processes are no longer needed:

```bash
zmx kill <session>
zmx kill <session> --force
```

`--force` also terminates attached clients. Prefer stopping `hub` followers or
attach clients first, then killing the session only when cleanup is intentional.

## Recommended decision table

| Need                             | Command                   | Notes                                   |
| -------------------------------- | ------------------------- | --------------------------------------- |
| Discover sessions                | `zmx list --short`        | Names only; use `zmx list` for metadata |
| Open/create for agent commands   | `zmx run NAME true`       | Does not occupy an interactive terminal |
| Run a command with exit status   | `zmx run NAME ARGV...`    | Direct argv; no implicit shell          |
| Run a long command in background | `zmx run NAME -d ARGV...` | Follow with `zmx wait NAME`             |
| Interactive terminal             | `zmx attach NAME`         | Launch through `hub start`              |
| Send PTY input                   | `zmx send NAME TEXT`      | Raw, asynchronous, no automatic CR      |
| Follow live output               | `zmx tail NAME`           | Launch through `hub start`              |
| Read bounded output              | `zmx history NAME`        | Does not alter the session              |
| End the session                  | `zmx kill NAME`           | Use `--force` only when required        |

Keep session names and command arguments explicit. Never put credentials or
other secrets into session names, shell history, or raw-input examples.
