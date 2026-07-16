# Continuous Deployment Strategy

## Blue/Green Deployment (current)

New versions of linker1 are now deployed as a **green VM** alongside the running **blue VM** using the shared OCI Load Balancer.  Nothing on the blue VM is touched until green is fully healthy and promoted.

### Workflow: `.github/workflows/bluegreen.yml`

Triggered on every push to `main` and via `workflow_dispatch`.

```
push to main
    │
    ▼
[build]  ── fail fast: broken build never provisions a VM
    │
    ▼
[provision-green]
    terraform apply (infra/main.tf)
    instance_display_name = vm-linker1-green-<run_id>
    uploads terraform.tfstate as artifact for failure cleanup
    │
    ▼
[blue-green-lb-deploy]  (calls oci-lb-bluegreen-ab.yml@main)
    preflight: validate /healthz is the configured health-check path
    stage_green_backend: add green VM to LB with drain=true
    verify_green_health: poll OCI LB until green reports OK  (≤ 20 min)
    start_ab_test: 90% blue / 10% green
    observe_ab_test: 60 s observation window
    verify_ab_health: confirm both backends still OK
    promote_green: 100% green / 0% blue (drained)
    ──── on any failure: rollback-to-current + remove-new-backend (LB side) ────
    │
    ├── success ──▶ [teardown-blue-on-success]
    │                 oci compute instance terminate <blue-ocid>
    │                 gh variable set OCI_INSTANCE_OCID = <green-ocid>
    │
    └── failure ──▶ [teardown-green-on-failure]
                      terraform destroy (using saved state artifact)
                      Blue VM untouched, OCI_INSTANCE_OCID unchanged
```

### Required secrets and variables — Blue/Green

The OCI CLI secrets (`OCI_CLI_USER`, `OCI_CLI_TENANCY`, `OCI_CLI_FINGERPRINT`, `OCI_CLI_KEY_CONTENT`, `OCI_CLI_REGION`) and application secrets (`LD_SDK_KEY`, `MYSQL_*`, `OTEL_*`, `DEPLOYMENT_PUBLIC_KEY`) are shared with the legacy pipeline and do not need to be re-added.

The following are **new** and must be added before the first run:

| Name | Type | Level | Use |
| --- | --- | --- | --- |
| `OCI_LB_OCID` | variable | repo | OCID of the shared OCI Load Balancer (`OCI_LB_OCID` from `infra/linker.env`) |
| `OCI_LB_LINKER_BACKEND` | variable | repo | Backend set name (`linker-1` — `OCI_LB_LINKER_BACKEND` from `infra/linker.env`) |
| `TF_COMPARTMENT_ID` | variable | repo | OCI compartment OCID the VM is *launched into* — this is your team's own compartment (e.g. `cmp-lz-prod-linker-1`), **not** the network compartment the shared subnet lives in. `oci iam compartment list --compartment-id-in-subtree true --all` lists every compartment your account can see; pick the one named after your team (`cmp-lz-prod-linker-<N>`). Confirm access with `oci compute image list --compartment-id <candidate>` before setting this — if it 404s, it's the wrong compartment (or you lack access to it), not a policy problem to escalate. |
| `TF_SUBNET_ID` | variable | repo | Subnet OCID (`OCI_LINKER_SUBNET_OCID` from `infra/linker.env`). Lives in a *different*, shared network compartment — this is expected, subnets and instances don't need to be in the same compartment. |
| `TF_IMAGE_ID` | variable | repo | OCI image OCID used to create the VM. Must be listed by `oci compute image list --compartment-id <TF_COMPARTMENT_ID value>` — platform images aren't compartment-scoped in the API response (`"compartment-id": null` on `image get`), but `LaunchInstance` still requires the *launching* compartment to have access to the image, so verify against the real `TF_COMPARTMENT_ID`, not just that the OCID resolves at all. |
| `OCI_INSTANCE_OCID` | variable | repo | OCID of the current blue VM (updated automatically after each successful deploy; must be set manually for the very first run) |

One-time setup commands (run from OCI Cloud Shell or locally with the OCI CLI configured):

```bash
gh variable set OCI_LB_OCID           --body "ocid1.loadbalancer.oc1.sa-bogota-1...."
gh variable set OCI_LB_LINKER_BACKEND --body "linker-1"
gh variable set TF_COMPARTMENT_ID     --body "ocid1.compartment.oc1..xxxxxxxx"
gh variable set TF_SUBNET_ID          --body "ocid1.subnet.oc1.sa-bogota-1.xxxxxxxx"
gh variable set TF_IMAGE_ID           --body "ocid1.image.oc1.sa-bogota-1.xxxxxxxx"
gh variable set OCI_INSTANCE_OCID     --body "<current-blue-vm-ocid>"
```

`DEPLOYMENT_PUBLIC_KEY` (already a repo secret) is reused as the SSH key for the new VM.

**Debugging `404-NotAuthorizedOrNotFound` on `terraform apply`'s `LaunchInstance` call**: OCI deliberately returns this same error for "doesn't exist" and "no permission," so it doesn't distinguish a bad OCID from a real access problem. Isolate which one it is with `oci compute image list --compartment-id <TF_COMPARTMENT_ID>` — if that 404s too, `TF_COMPARTMENT_ID` itself is wrong (most likely: it got set to a *network*/platform compartment instead of your team's own compartment, since `TF_SUBNET_ID` legitimately lives in a different one and it's easy to conflate the two). Run `oci iam compartment list --compartment-id-in-subtree true --all` to find your real compartment, confirm access with the `image list` command above, then update `TF_COMPARTMENT_ID`. This exact issue cost significant time during the 2026-07 VM recreation after the course's shared VMs were deleted — `TF_COMPARTMENT_ID` had been set to the network/platform compartment instead of `cmp-lz-prod-linker-1`.

### Health check

`/healthz` is the real health endpoint (runs `SELECT 1` against MySQL).  It is passed to the reusable workflow as `health-check-path: /healthz`.  The OCI LB must be configured with `/healthz` as its health-check URL path; the `preflight` job validates this before doing anything else.  See [`HEALTHCHECK.md`](HEALTHCHECK.md) for endpoint details.

### Failure paths

| Failure point | LB side (reusable workflow) | VM side (this workflow) |
| --- | --- | --- |
| Green health check fails | `remove-new-backend` | `teardown-green-on-failure`: `terraform destroy` |
| A/B health check fails | `rollback-to-current` + `remove-new-backend` | `teardown-green-on-failure`: `terraform destroy` |
| Promotion fails | `rollback-to-current` | `teardown-green-on-failure`: `terraform destroy` |

In all failure cases: blue VM keeps 100% traffic, `OCI_INSTANCE_OCID` is unchanged, and the green VM is destroyed.

### Notes

- **Green VM builds from source**: `infra/cloud-init.yaml` runs `git clone + mvn clean package` on first boot.  It clones the repo's default branch HEAD, not the exact triggering commit.  The `build` job at the start of the workflow acts as a compile gate, but the compiled jar in CI is not transferred to the VM.
- **`pipeline.yml` `deploy-prod` job**: superseded by this workflow. It reads `vars.OCI_INSTANCE_OCID` dynamically (kept up to date by `bluegreen.yml`'s `teardown-blue-on-success` step), so it doesn't hardcode a specific VM and won't outright fail just because a VM was recreated — but it deploys straight to the "current" instance with no green/health-check/rollback safety, bypassing the whole point of this workflow. Should be removed as a follow-up once `bluegreen.yml` is confirmed working end to end.
- **`variables: write` is blocked by org policy, confirmed**: both `bootstrap-blue.yml`'s and `bluegreen.yml`'s `teardown-blue-on-success` job declare `permissions: actions: write` so `GITHUB_TOKEN` can call `gh variable set OCI_INSTANCE_OCID`. This fails with `HTTP 403: Resource not accessible by integration` regardless of that declaration, because the org has "Write permissions for workflows" disabled repo-wide (`gh api repos/co-eiv-devsecops/linker1/actions/permissions/workflow` shows `"default_workflow_permissions":"read"`, and attempting to raise it via the API returns `409 Conflict: Write permissions for workflows are disabled by the organization`). A workflow's own `permissions:` block can only *narrow* the org-enforced ceiling, never raise it — this needs an org admin to change the org-level policy, which nobody on the team currently has access to.
  **Until then, after every successful bootstrap or blue/green promotion, manually run:**

  ```bash
  gh variable set OCI_INSTANCE_OCID --body "<the-new-blue-VM-OCID>"
  ```

  (The workflow logs the OCID it tried to set right before the 403, in the `BLUE_OCID`/`green-instance-id` output — copy it from there.) The proper fix, if an org admin becomes available, is either flipping the org policy or creating a PAT with `repo` scope stored as `secrets.GH_PAT` and swapping it in for `secrets.GITHUB_TOKEN` in that one step (a PAT's permissions aren't subject to this same org-level workflow-token ceiling).

---

## Legacy — Single-VM Deployment (superseded)

The documentation below describes the old `pipeline.yml`-based single-VM deployment through an OCI Bastion.  The VMs it targeted have been deleted.  It is retained for historical context.

### Context (legacy)

Linker1 runs on a single OCI VM (production, `https://1.n-la-c.app`) created with the IaC in `infra/` (`assign_public_ip = false`: the instance has no public IP). Before this change, `.github/workflows/pipeline.yml` didn't deploy anything real: the `deploy-dev`, `validate-dev`, and `deploy-prod` jobs were placeholders that only did `echo`, and the one real job (`rollback`) assumed direct SSH to a public `VM_HOST` that never existed or was configured.

The real mechanism, originally provided by the course instructor as a shared composite action (`co-eiv-devsecops/material-curso/actions/oci-bastion-deploy@main`), reaches the VM through an **OCI Bastion** (an SSH session managed by OCI, with no public IP needed). This document describes how the pipeline was set up around that mechanism.

**Vendored, not cross-repo, as of 2026-07**: `material-curso`'s cross-repo Actions access broke sometime after 2026-07-11 (every workflow depending on it started failing with `Unable to resolve action ... not found`, most likely an org policy change during the course's final week). `oci-bastion-deploy` and `oci-lb-bluegreen` (plus the `oci-lb-bluegreen-ab.yml` reusable workflow) are now vendored directly into this repo at `actions/` and `.github/workflows/oci-lb-bluegreen-ab.yml` respectively, with local `uses: ./actions/...` references instead of the original cross-repo ones. The content is unmodified from the source; only the reference paths changed.

The action downloads a GitHub Actions artifact, opens a `bastion session create-managed-ssh` session against `instance-id`, authenticating with `env.DEPLOYMENT_PUBLIC_KEY`/`env.OCI_BASTION_OCID`, copies the artifact's contents to `target-path` (via `sudo tar -x`), runs `script` over SSH with the working directory already set to `target-path` and the `DEPLOY_PATH` variable pointing there, and deletes the bastion session at the end. The `artifact-name` and `target-path` inputs are **required** (`required: true`) even when a new artifact isn't needed, so the `rollback` job reuses the `linker-app` artifact that `Build` already uploaded in the same run, purely to satisfy that requirement. The action has no `ssh-public-key` or `bastion-id` inputs (unlike the template the instructor shared); it takes those values from `env.DEPLOYMENT_PUBLIC_KEY`/`env.OCI_BASTION_OCID`, which are indeed declared in the job's `env:` block.

There is no separate VM for a development environment yet, so this pipeline only covers `prod`. The `deploy-dev`/`validate-dev` jobs were removed from the file instead of being left as a simulation; once a second VM exists, they can be added following the same pattern as `deploy-prod`/`validate-prod`.

## Jobs in `.github/workflows/pipeline.yml`

Triggers on every `push` to `main` (and manually via `workflow_dispatch`):

1. **`Build`**: compiles the jar (`mvn package -DskipTests`) and uploads it as the `linker-app` artifact. This is a job independent from `ci.yml`'s `package` (artifacts from `actions/upload-artifact` don't cross between different workflows without extra plumbing), so this cheap step is duplicated instead of complicating cross-workflow downloads.
2. **`deploy-prod`**: runs in the context of the `prod` GitHub Environment (secrets/variables scoping). Uses `oci-bastion-deploy` to upload the jar to the VM through the bastion and run a remote script that:
   - copies the jar to `/opt/linker1/linker1.jar`
   - rewrites the `systemd` unit (`linker1.service`), now including `Environment="LD_SDK_KEY=..."` (previously missing: `Main.java` calls `System.exit(1)` if that variable isn't present)
   - restarts the service and verifies `systemctl is-active` + `curl localhost:8080` → `200`
   - then, back on the runner (not the VM), runs `scripts/check-grafana-metrics.sh` to confirm the instance is actually emitting telemetry into Grafana Cloud post-deploy — see [Optional: post-deploy Grafana telemetry check](#optional-post-deploy-grafana-telemetry-check) below and [`MONITORING.md`](MONITORING.md). This is why the job now also has a `Checkout repository` step: the earlier jobs didn't need the repo checked out on the runner itself, only this script does.
3. **`validate-prod`**: read-only checks against `https://1.n-la-c.app` (`/`, `/app.js`, `/styles.css`, 404 on a nonexistent route). It doesn't repeat the mutating tests (create link, alias, conflict) because those are already covered by `ci.yml`'s `api-tests` job (Newman) against the same production instance; running them again on every deploy would just pollute the real database.
4. **`rollback`**: triggers if `deploy-prod` or `validate-prod` fail. Resolves the previous SemVer tag (same as before) and now uses the same `oci-bastion-deploy` action (instead of raw SSH) to run `bash scripts/rollback.sh <tag>` on the checkout that already exists on the VM.

## About manual approval on the `prod` Environment

GitHub lets you configure "required reviewers" on an Environment to pause a deploy until someone manually approves it — but that protection **is not available for private repos on GitHub's free plan** (only on public repos or Team/Enterprise plans). This was verified in Settings > Environments > `prod`: the page doesn't offer the reviewers section, only "Deployment branches and tags" and secrets/variables.

In practice this doesn't leave the deployment uncontrolled: the real gate is already provided by `main`'s branch protection (PR + 1 required approval + green CI checks, see [`BRANCH_PROTECTION.md`](BRANCH_PROTECTION.md)) — nothing reaches `main` (and therefore triggers `deploy-prod`) without prior human review. The job's `environment: prod` is kept as-is because it's still useful for secrets/variables scoping, even though it doesn't add an additional pause.

## Required secrets and variables

Already configured in the repository (verified in Settings > Secrets and variables > Actions):

| Name | Type | Level | Use |
| --- | --- | --- | --- |
| `OCI_CLI_USER` | secret | repo | OCI CLI authentication |
| `OCI_CLI_TENANCY` | secret | organization | OCI CLI authentication |
| `OCI_CLI_FINGERPRINT` | secret | repo | OCI CLI authentication |
| `OCI_CLI_KEY_CONTENT` | secret | repo | Private key of the OCI API key |
| `OCI_CLI_REGION` | variable | organization | OCI region (`sa-bogota-1`) |
| `OCI_BASTION_OCID` | variable | organization | OCI Bastion used for the managed SSH session |
| `DEPLOYMENT_PRIVATE_KEY` | secret | repo | SSH private key the action uses to connect through the bastion |
| `DEPLOYMENT_PUBLIC_KEY` | secret | repo | The matching public key, authorized on the bastion session |
| `LD_SDK_KEY` | secret | repo | Already used by `ci.yml`'s `smoke` job; reused for prod's `systemd` unit |
| `OCI_INSTANCE_OCID` | variable | repo | OCID of `vm-linker1-app`, obtained via `oci compute instance list --compartment-id $CMP_ID` from Cloud Shell (the VM wasn't created with Terraform, so `terraform output` doesn't work for this) |

Note: `DEPLOYMENT_PUBLIC_KEY` is a **secret**, not a variable, even though the instructor's original template referenced it as `vars.DEPLOYMENT_PUBLIC_KEY` — `pipeline.yml` was already adjusted to read it as `secrets.DEPLOYMENT_PUBLIC_KEY`.

There is also an `OCI_VM_SSHKEY_CONTENT` secret in the repo that isn't used by this pipeline or the instructor's template — it was left untouched, unused until it's identified what it was for.

### Optional: OTLP export and MySQL healthcheck

`deploy-prod` also sets these. **Update (2026-07-12): all of them are now configured** as repo-level secrets/variables (verified directly in Settings > Secrets and variables > Actions) — this section previously said none existed, which is now stale:

| Name | Type | Level | Use |
| --- | --- | --- | --- |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | variable | repo | Grafana Cloud OTLP gateway URL — see [`INSTRUMENTATION.md`](INSTRUMENTATION.md#otlp-export-configuration) |
| `OTEL_EXPORTER_OTLP_HEADERS` | secret | repo | `Authorization=Basic <token>` for the OTLP gateway |
| `MYSQL_HOST` | variable | repo | MySQL host for `/healthz`'s `SELECT 1` check |
| `MYSQL_DATABASE` | variable | repo | MySQL database name |
| `MYSQL_USER` | variable | repo | MySQL user |
| `MYSQL_PWD` | secret | repo | MySQL password |
| `LOG_LEVEL` | variable | repo or `prod` env | Optional, defaults to `INFO` in the pipeline if unset |

**Known risk with `OTEL_EXPORTER_OTLP_HEADERS`**: the instructor's course material hands out this variable as a literal template, `Authorization=Basic%<ponga_su_token>` (placeholder text, malformed `%` instead of a space, unclosed angle bracket) — if that string was ever pasted as-is into the secret instead of being replaced with a real `Authorization=Basic <base64-encoded-instance-id:api-token>` value, OTLP export fails authentication silently (same "looks healthy, exports nothing" failure mode documented in [`INSTRUMENTATION.md`](INSTRUMENTATION.md#otlp-export-configuration)) — `/healthz` and `/` keep returning `200` throughout, so this doesn't show up as a deploy failure, only as an empty Grafana dashboard. See [`MONITORING.md`](MONITORING.md#troubleshooting-dashboard-shows-no-data-on-every-panel) for how this was diagnosed and how to regenerate the secret with a real token.

### Optional: post-deploy Grafana telemetry check

`deploy-prod` also runs `scripts/check-grafana-metrics.sh` after the local `/healthz` check, to confirm the deployed instance is actually emitting metrics into Grafana Cloud (not just that `/healthz` answers locally on the VM). Same situation as above — **not yet configured in the repo**, so the step currently prints a warning and skips rather than failing the deploy:

| Name | Type | Level | Use |
| --- | --- | --- | --- |
| `GRAFANA_PROM_QUERY_URL` | variable | repo or `prod` env | Grafana Cloud Prometheus/Mimir query endpoint |
| `GRAFANA_PROM_USER` | variable | repo or `prod` env | Grafana Cloud Prometheus instance ID |
| `GRAFANA_CLOUD_API_KEY` | secret | repo or `prod` env | Grafana Cloud API key/token, `metrics:read` scope |

See [`MONITORING.md`](MONITORING.md#post-deploy-automated-check-bonus) for where to find these values and how the check behaves once configured.

## Open risks / things to verify

- The remote `script:` assumes the `ubuntu` user has passwordless `sudo` on the VM (same assumption `deploy.sh` already makes). This is consistent with what the action itself needs for its artifact-copy step (`sudo tar -C $target_q -xf -`), so if the bastion didn't grant that access, the action's own "Copy artifact to target path" step would already fail before reaching our script.
- The `rollback` job assumes the repo's persistent checkout lives at `/home/ubuntu/linker1` on the VM (matches `infra/cloud-init.yaml`, which clones it there on first boot) — if some future manual deployment uses a different path, that line in `pipeline.yml` needs adjusting.
- The bastion session has a 1800s TTL (the action's default) — plenty for a normal deploy, but if `mvn package`/deployment ever takes longer, `session-ttl` is a configurable input to raise.

## Related documents

- [Monitoring (Grafana)](MONITORING.md) — dashboard, panel guide, and the post-deploy telemetry check
- [Rollback Strategy](ROLLBACK_STRATEGY.md)
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml)
- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- [`deploy.sh`](../deploy.sh)
- [`infra/`](../infra/) — the VM's Terraform
