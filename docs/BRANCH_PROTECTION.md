# Branch Protection

## Context

`main` was already protected in the previous assignment (Small Batch Development): it requires a PR, 1 approval, and the `Build`/`Tests`/`Package`/`Summary`/`Smoke Test` checks green before merging.

Since then, the team adopted `DEV` as the real integration branch (the current flow is: feature branch → PR into `DEV` → approval → merge; `main` receives changes already validated on `DEV`). `DEV`, however, had no protection rule configured — direct pushes with no PR or review were possible. This document closes that gap.

## State verified before configuring

```bash
gh api repos/co-eiv-devsecops/linker1/branches/DEV/protection
```

Before this change, the response did not include `required_status_checks` or `required_pull_request_reviews` — only the default flags (`allow_force_pushes: false`, etc.), i.e. no real PR or check requirement to merge into `DEV`.

## Rules applied to `DEV`

- **Requires a Pull Request**: direct pushes to `DEV` are not allowed.
- **1 minimum approval**, and previous approvals are dismissed if new commits are pushed (`dismiss_stale_reviews`).
- **Required status checks** (must pass green before merging), taken from `.github/workflows/ci.yml`, the only workflow that actually runs on a `pull_request` targeting `DEV`:
  - `Build`
  - `Tests`
  - `Package`
  - `Summary`
  - `Smoke Test`
- **`strict: true`**: the branch must be up to date with `DEV` before merging.
- **Resolved conversations**: cannot merge with unresolved review comments.
- **No force-push or branch deletion.**
- **`enforce_admins: true`**: the rules also apply to repo administrators, no bypass.

Neither `API Tests (Live)` nor the jobs in `.github/workflows/pipeline.yml` (`Deploy to Development`, `Validate Development`, `Deploy to Production`, `Rollback`) were included as required. That workflow only triggers on `push` (and `workflow_dispatch`), never on `pull_request` — its jobs simply don't exist as checks on a PR. This was discovered the hard way: the first version of this configuration did include them as required, which left **every PR into `DEV` permanently blocked** (`mergeStateStatus: BLOCKED`, with no possible check to satisfy it). It was fixed immediately upon detecting it on the first real PR (#38) that went through this rule.

`required_linear_history` was left as `false` for `DEV` (unlike `main`): since it's the integration branch for several parallel features, allowing merge commits makes sense there; `main` still uses squash-merge as the single source of truth.

## How it was applied

```bash
gh api repos/co-eiv-devsecops/linker1/branches/DEV/protection \
  -X PUT --input docs/protection-config.json
```

Using the [`protection-config.json`](protection-config.json) file in this same directory.

## Verification

```bash
gh api repos/co-eiv-devsecops/linker1/branches/DEV/protection
```

Should return `required_status_checks.contexts` with the 5 checks listed above and `required_pull_request_reviews.required_approving_review_count: 1`.

As an additional test, you can try a direct push to `DEV` (without a PR) from a local checkout:

```bash
git checkout DEV
git commit --allow-empty -m "test: direct push blocked"
git push origin DEV
```

It should be rejected with an error stating that a pull request is required.

## Impact on the team's workflow

- Every change into `DEV` goes through a `feature/*`, `fix/*`, or `chore/*` branch and a PR.
- Every PR needs at least one approval from another team member.
- CI/CD checks must pass before merging — it's no longer possible to merge with a broken pipeline.
- `main` keeps its own rules (already configured), inheriting only changes that already passed through `DEV`.

## Related documents

- [Rollback Strategy](ROLLBACK_STRATEGY.md)
- [`.github/CONTRIBUTING.md`](../.github/CONTRIBUTING.md)
- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml)
