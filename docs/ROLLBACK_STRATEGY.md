# Rollback Strategy

## Context

Linker1 is deployed on an OCI VM via `deploy.sh`, which builds the project directly on the VM (x86_64 architecture) and leaves it running as the `linker1.service` systemd service behind Nginx. Versions are tagged with Git tags following [Semantic Versioning](https://semver.org/) (`vMAJOR.MINOR.PATCH`); every `v*.*.*` tag triggers the `release.yml` workflow, which publishes a GitHub Release with the packaged jar and the launcher script (`scripts/linker1`) as assets.

This rollback strategy uses exactly those two mechanisms that already exist (SemVer tags + `deploy.sh`) instead of introducing new infrastructure: rolling back means stopping the service, switching to the code of a previous tag, and rebuilding/restarting with `deploy.sh`.

## When to roll back

- `pipeline.yml`'s `deploy-prod` fails or the service doesn't start after a deployment.
- The post-deploy smoke test (the same checks that run in CI: static assets, create link, alias, redirect, 404) fails against the live instance.
- A critical bug or data regression is detected shortly after a release.

## Strategy

1. **Identify the last stable version**: the tag immediately before the one that failed (e.g. if `v1.2.0` fails, roll back to `v1.1.0`).
2. **Stop the service** on the VM (`systemctl stop linker1.service`) so it doesn't keep serving a broken version.
3. **Switch to that tag's code** in the checkout that already exists on the VM (`git fetch --tags && git checkout <tag>`), without re-cloning or depending on external artifacts.
4. **Rebuild with `deploy.sh`**, the exact same path as a normal deployment — so the rollback isn't a separate, untested procedure, but the same script already used daily, just pointed at a different commit.
5. **Verify** that the service is `active (running)` and that the main routes respond as expected.

This is the same approach Esteban documented for releases: tags already exist and are already the source of truth for "what version is this"; the rollback simply moves to a previous tag instead of a new one.

## Manual procedure (via OCI Cloud Shell)

The VM has no public IP (`assign_public_ip = false` in `infra/main.tf`), so you can't `ssh ubuntu@<host>` from just any machine. Manual access happens from **OCI Cloud Shell** (which already runs inside OCI's network) using an SSH key stored in OCI Vault:

```bash
# 0. Set up the relevant IDs once (~/.bash_env, loaded from ~/.bashrc)
export CMP_ID="ocid1.compartment.oc1..xxxxxxxx"
export SUBNET_ID="ocid1.subnet.oc1.sa-bogota-1.xxxxxxxx"
export VM_KEY_SECRET_ID="ocid1.vaultsecret.oc1..xxxxxxxx"

# 1. Fetch the SSH private key from the Vault and give it a short entry in ~/.ssh/config
oci secrets secret-bundle get --secret-id "$VM_KEY_SECRET_ID" --stage Current \
  --query 'data."secret-bundle-content".content' --raw-output | base64 -d > ~/.ssh/linker.key
chmod 600 ~/.ssh/linker.key
# Add to ~/.ssh/config:
#   Host linker
#       HostName A.B.C.D
#       User ubuntu
#       IdentityFile ~/.ssh/linker.key
#       IdentitiesOnly yes

# 2. Connect and run the rollback
ssh linker
cd linker1
bash scripts/rollback.sh v1.0.0
```

The script:

1. Verifies the requested tag exists (`git rev-parse`).
2. Stops `linker1.service`.
3. Runs `git fetch --tags` and `git checkout <tag>`.
4. Runs `deploy.sh` to rebuild and restart the service with that code.
5. Verifies the service ended up `active (running)` and that `GET /` responds `200`.
6. If anything fails along the way, it stops (`set -euo pipefail`) and prints which step it was on, instead of silently leaving the system half-migrated.

## Automated procedure (GitHub Actions via OCI Bastion)

The `rollback` job in [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml) is connected to `deploy-prod` and `validate-prod`: it triggers automatically if either one fails (`if: failure()`). Since the VM has no public IP, it doesn't connect via direct SSH — it uses the course's composite action `co-eiv-devsecops/material-curso/actions/oci-bastion-deploy@main`, which opens a managed session against an OCI Bastion and remotely runs `cd linker1 && bash scripts/rollback.sh <tag>`, pointing at the tag prior to the one that was being deployed.

It requires these variables/secrets in the repository (see details and current status in [`DEPLOYMENT.md`](DEPLOYMENT.md)):

| Name | Type | Description |
| --- | --- | --- |
| `OCI_CLI_USER`/`OCI_CLI_TENANCY`/`OCI_CLI_FINGERPRINT`/`OCI_CLI_KEY_CONTENT` | secret | OCI CLI authentication |
| `OCI_CLI_REGION` | variable | OCI region |
| `OCI_BASTION_OCID` | variable | Bastion used for the managed SSH session |
| `DEPLOYMENT_PRIVATE_KEY` / `DEPLOYMENT_PUBLIC_KEY` | secret / variable | SSH key pair the action uses through the bastion |
| `OCI_INSTANCE_OCID` | variable | Production VM's OCID (the only value the team needs to add) |

## Simulation

To validate the procedure without depending on a real broken deployment, `scripts/rollback.sh` can be run against any existing tag at any time — it doesn't depend on there having been a prior failure. Steps to simulate:

```bash
# 1. Confirm the currently deployed tag
git -C /home/ubuntu/linker1 describe --tags

# 2. Run the rollback to the same tag (v1.0.0) as a test,
#    since only one tag is currently published
bash scripts/rollback.sh v1.0.0

# 3. Confirm the service is still active and responding
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
```

Once more than one tag is published (e.g. after a `v1.1.0`), the real simulation is: deploy `v1.1.0`, then run `bash scripts/rollback.sh v1.0.0` and confirm the service goes back to responding with `v1.0.0`'s behavior.

## Success criteria

- The service is `active (running)` per `systemctl status linker1.service`.
- `GET /` responds `200` within 2 minutes of running the script.
- The code on the VM (`git describe --tags`) matches the rollback's target tag.
- The SQLite database isn't lost (`deploy.sh` doesn't touch it; it lives in `/var/lib/linker1`, outside the repo directory).

## Related documents

- [`deploy.sh`](../deploy.sh) — the deployment script the rollback depends on.
- [`scripts/rollback.sh`](../scripts/rollback.sh) — the rollback implementation.
- [`.github/workflows/pipeline.yml`](../.github/workflows/pipeline.yml) — the continuous deployment pipeline with the automatic rollback job.
- [`.github/workflows/release.yml`](../.github/workflows/release.yml) — generates the tags/releases the rollback targets.
- [Continuous Deployment Strategy](DEPLOYMENT.md) — describes the full `deploy-prod`/`validate-prod` pipeline.
