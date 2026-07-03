# Contributing to linker1

This project follows a small-batch, trunk-based development workflow. Please read
this before opening a pull request.

## Branching and pull requests

- Branch off `main` for every change (`feature/...`, `refactor/...`, `fix/...`,
  `chore/...`, `docs/...`).
- Keep pull requests small and focused on one backlog item or fix. Prefer several
  small PRs over one large one.
- Rebase onto `main` (don't merge `main` into your branch) to keep history close to
  linear before opening or updating a PR.
- `main` is protected: a pull request needs at least one approval and a green
  `Build` / `Tests` / `Package` / `Summary` / `Smoke Test` pipeline before it can
  merge. Use **Squash and merge** so each PR lands as a single commit on `main`,
  while the full commit history remains visible on the PR's "Commits" tab.

## Test-Driven Development

Changes to application behavior should follow red/green/refactor:

1. **Red** — write a failing test that describes the behavior you want, commit it
   (`test: red - ...`).
2. **Green** — write the minimal code to make it pass, commit it
   (`feat: green - ...` or `fix: green - ...`).
3. **Refactor** — clean up the implementation with tests still passing, commit it
   (`refactor: ...`).

Commit history should show this rhythm rather than a single large commit, even when
using AI assistance to write code.

## Running things locally

```bash
mvn test                                          # run unit + HTTP-level tests
mvn package -DskipTests                           # build the fat jar
java -jar target/linker1-1.0-jar-with-dependencies.jar   # run it
```

The test suite spins up an in-memory SQLite database and, for route-level tests, a
real Javalin instance on an ephemeral port — no external services required.

## CI gates

Every push and pull request runs: `Build` (compile), `Tests` (unit + HTTP-level
tests), `Package` (fat jar + Linux launcher script), `Summary` (artifact sanity
check), and `Smoke Test` (starts the packaged jar and exercises the real HTTP API).
All must pass before a PR can merge into `main`.
