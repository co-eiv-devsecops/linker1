# Continuous Deployment Strategy

## Context

Linker1 runs on a single OCI VM (production, `https://1.n-la-c.app`) created with the IaC in `infra/` (`assign_public_ip = false`: the instance has no public IP). Before this change, `.github/workflows/pipeline.yml` didn't deploy anything real: the `deploy-dev`, `validate-dev`, and `deploy-prod` jobs were placeholders that only did `echo`, and the one real job (`rollback`) assumed direct SSH to a public `VM_HOST` that never existed or was configured.

The real mechanism, provided by the course instructor, is a shared composite action (`co-eiv-devsecops/material-curso/actions/oci-bastion-deploy@main`) that reaches the VM through an **OCI Bastion** (an SSH session managed by OCI, with no public IP needed). This document describes how the pipeline was set up around that mechanism.

The action downloads a GitHub Actions artifact, opens a `bastion session create-managed-ssh` session against `instance-id`, authenticating with `env.DEPLOYMENT_PUBLIC_KEY`/`env.OCI_BASTION_OCID`, copies the artifact's contents to `target-path` (via `sudo tar -x`), runs `script` over SSH with the working directory already set to `target-path` and the `DEPLOY_PATH` variable pointing there, and deletes the bastion session at the end. The `artifact-name` and `target-path` inputs are **required** (`required: true`) even when a new artifact isn't needed, so the `rollback` job reuses the `linker-app` artifact that `Build` already uploaded in the same run, purely to satisfy that requirement. The action has no `ssh-public-key` or `bastion-id` inputs (unlike the template the instructor shared); it takes those values from `env.DEPLOYMENT_PUBLIC_KEY`/`env.OCI_BASTION_OCID`, which are indeed declared in the job's `env:` block.

There is no separate VM for a development environment yet, so this pipeline only covers `prod`. The `deploy-dev`/`validate-dev` jobs were removed from the file instead of being left as a simulation; once a second VM exists, they can be added following the same pattern as `deploy-prod`/`validate-prod`.

## Jobs in `.github/workflows/pipeline.yml`

Triggers on every `push` to `main` (and manually via `workflow_dispatch`):

1. **`Build`**: compiles the jar (`mvn package -DskipTests`) and uploads it as the `linker-app` artifact. This is a job independent from `ci.yml`'s `package` (artifacts from `actions/upload-artifact` don't cross between different workflows without extra plumbing), so this cheap step is duplicated instead of complicating cross-workflow downloads.
2. **`deploy-prod`**: runs in the context of the `prod` GitHub Environment (secrets/variables scoping). Uses `oci-bastion-deploy` to upload the jar to the VM through the bastion and run a remote script that:
   - copies the jar to `/opt/linker1/linker1.jar`
   - rewrites the `systemd` unit (`linker1.service`), now including `Environment="LD_SDK_KEY=..."` (previously missing: `Main.java` calls `System.exit(1)` if that variable isn't present)
   - restarts the service and verifies `systemctl is-active` + `curl localhost:8080` → `200`
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

`deploy-prod` also sets these, but **not yet configured in the repo** (verified via `gh secret list` / `gh variable list` / `gh secret list --env prod` / `gh variable list --env prod` — none exist as of this writing). Until they're added, `add_env_if_set` in the job's script simply omits each corresponding `Environment=` line, so the app starts fine with OTLP export disabled and `/healthz` reporting `503` (see [`HEALTHCHECK.md`](HEALTHCHECK.md)):

| Name | Type | Level | Use |
| --- | --- | --- | --- |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | variable | repo or `prod` env | Grafana Cloud OTLP gateway URL — see [`INSTRUMENTATION.md`](INSTRUMENTATION.md#otlp-export-configuration) |
| `OTEL_EXPORTER_OTLP_HEADERS` | secret | repo or `prod` env | `Authorization=Basic <token>` for the OTLP gateway; token comes from the OCI Vault at `https://cloud.oracle.com/security/secrets?region=sa-bogota-1` |
| `MYSQL_HOST` | variable | repo or `prod` env | MySQL host for `/healthz`'s `SELECT 1` check |
| `MYSQL_DATABASE` | variable | repo or `prod` env | MySQL database name (`linker_db_<group-number>`) |
| `MYSQL_USER` | variable | repo or `prod` env | MySQL user (`linker_user_<group-number>`) |
| `MYSQL_PWD` | secret | repo or `prod` env | MySQL password; also comes from the OCI Vault above |
| `LOG_LEVEL` | variable | repo or `prod` env | Defaults to `INFO` in the pipeline if unset |

These need to be added with `gh secret set <NAME>` / `gh variable set <NAME>` (or via Settings > Secrets and variables > Actions) — since they're pulled from OCI Vault and the course's Grafana Cloud stack, only someone with access to those consoles can supply the real values.

## Open risks / things to verify

- The remote `script:` assumes the `ubuntu` user has passwordless `sudo` on the VM (same assumption `deploy.sh` already makes). This is consistent with what the action itself needs for its artifact-copy step (`sudo tar -C $target_q -xf -`), so if the bastion didn't grant that access, the action's own "Copy artifact to target path" step would already fail before reaching our script.
- The `rollback` job assumes the repo's persistent checkout lives at `/home/ubuntu/linker1` on the VM (matches `infra/cloud-init.yaml`, which clones it there on first boot) — if some future manual deployment uses a different path, that line in `pipeline.yml` needs adjusting.
- The bastion session has a 1800s TTL (the action's default) — plenty for a normal deploy, but if `mvn package`/deployment ever takes longer, `session-ttl` is a configurable input to raise.

## Related documents

- [Rollback Strategy](ROLLBACK_STRATEGY.md)
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml)
- [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- [`deploy.sh`](../deploy.sh)
- [`infra/`](../infra/) — the VM's Terraform
