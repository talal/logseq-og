---
name: writing-commit-messages
description: Writes Git commit messages. Activates when the user asks to write a commit message, draft a commit message, or similar.
---

# Writing Commit Messages

Write commit messages that follow commit style guidelines for the project.

## Format

```
<type>(<scope>): <summary>

<long form description>
```

## Rules

### Type

Use one of the following types:

- `feat`: commits that add a new feature
- `fix`: commits that fix a bug
- `refactor`: refactored code that neither fixes a bug nor adds a feature. There should be no behavior change, only code rewriting/restructuring
- `perf`: special refactor commits geared towards improving performance
- `test`: commits that add new tests or fix existing tests
- `chore`: Changes that do not relate to a fix or feature and don’t modify source or test files. Basically miscellaneous commits (e.g., updating dependencies or modifying .gitignore file)
- `build`: changes that affect the build system/tooling (e.g., just, cargo, npm,
  etc.)
- `ci`: CI/CD related changes
- `docs`: documentation only changes
- `style`: changes that that do not affect the meaning of the code (e.g., using code formatters, missing semi-colons, white-space fixes, etc.)

### Scope

Use a short, lowercase identifier for the area of code changed (e.g., `deps`,
`config`, `ui`, etc.). Determine this from the file paths in the diff. If
changes span the frontend, use `frontend`. Use nested subsystems with `/` when
helpful and exclusive (e.g., `ui/flashcards`).

### Summary

Lowercase start (not capitalized), imperative mood, no trailing period. Keep it
concise—ideally under 60 characters total for the whole subject line.

### Long form description

- Describe **what changed**, **what the previous behavior was**, and **how the new behavior works** at a high level.
- Use plain prose, not bullet points. Wrap lines at ~72 characters.
- Focus on the _why_ and _how_ rather than restating the diff.
- Keep the tone direct and technical without no filler phrases.
- Don't exceed a handful of paragraphs; less is more.

## Workflow

- If `.jj` is present, use `jj` instead of `git` for all commands.
- Run a diff to see what changes are present since the last commit.
- Identify the subsystem from the changed file paths.
- Draft the commit message following the format above.
- Apply the commit
- **Don't push the commit; leave that to the user.**
