# Serverless Deployment (AWS Lambda) — bonus

Linker1 also runs as an AWS Lambda function behind API Gateway — a **separate, additional PROD target** alongside the VM/blue-green deployment (`docs/DEPLOYMENT.md`), not a replacement for it. Nothing here touches the VM, the load balancer, or `OCI_INSTANCE_OCID`.

## Why this exists

Issue #74 (bonus): reimplement Linker as a serverless function on AWS Lambda or Azure Functions, generating **the same artifact** across platforms rather than forking the business logic into a separate codebase.

## What's reused vs. what's new

| Reused unchanged | New (serverless-only) |
| --- | --- |
| `linker.LinkService` | `linker.serverless.LinkLambdaHandler` — routes API Gateway proxy events to `LinkService`, replacing Javalin's `LinkRoutes` |
| `linker.LinkRepository` | `linker.serverless.LambdaComposition` — composition root wiring a MySQL connection from Lambda environment variables, replacing `Main`'s SQLite wiring |
| `linker.Link`, `linker.AliasConflictException` | `serverless/template.yaml` — AWS SAM IaC |
| The exact same fat jar (`target/linker1-1.0-jar-with-dependencies.jar`) `mvn package` already builds for the VM target | `.github/workflows/serverless-deploy.yml` — deploy pipeline |

`LinkService`/`LinkRepository` have zero AWS/Lambda-specific code in them — `LinkLambdaHandler` is a thin adapter, the same shape as `LinkRoutes` is for Javalin. This is the literal meaning of "same artifact for all platforms": one `mvn package` run produces one jar; the VM target points `java -jar` at `Main`, the Lambda target points its `Handler:` config at `linker.serverless.LinkLambdaHandler::handleRequest` inside that same jar. There is no second build, no forked business logic.

## Why MySQL, not SQLite, here specifically

The VM target's datastore (SQLite, a local file at `LINKER_DB_PATH`) does not work in Lambda: the execution environment's filesystem is ephemeral and not shared across concurrent invocations or across cold starts on a different underlying instance — a SQLite file written by one invocation is not guaranteed to be there (or in a consistent state) for the next. `LinkRepository` already takes any `java.sql.Connection`, so this requires no change to it, only a different connection source. This is scoped to the serverless target only — the VM deployment keeps using SQLite as its primary store exactly as before (MySQL there remains additive, `/healthz`-only, per `docs/HEALTHCHECK.md`).

## What's intentionally out of scope for v1

To keep this bonus deliverable focused on the issue's actual acceptance criteria (reuse `LinkService`/`LinkRepository`, deploy as a new PROD target, document it), the following are **not** wired into the Lambda handler, unlike the VM target:

- **OpenTelemetry** (`linker.telemetry.*`) — would need SDK initialization per cold start; AWS X-Ray or the Lambda OTel layer is the idiomatic path here, not a follow-up for this bonus.
- **LaunchDarkly feature flags** — the VM target's static routes (`index.html` vs `index-v2.html`) don't apply; Lambda serves only the `/link`/`/{id}` API surface, not the frontend.
- **`/healthz`** — the Lambda's own health is API Gateway/Lambda's own health, a different operational model than the VM's systemd-managed process.

None of these are precluded by the design — `LinkLambdaHandler`'s constructor already takes an injected `LinkService`, so adding telemetry later means adding constructor parameters, the same pattern `Main.java` already uses.

## Endpoints

Same paths/methods/status codes as the VM target's `LinkRoutes`, served through API Gateway instead of nginx → Javalin:

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/link` | Create a link (JSON body `{"url": "...", "alias": "..."}`, `alias` optional). `201` new, `200` dedupe, `400` invalid, `409` alias conflict. |
| `GET` | `/{id}` | `301` redirect to the stored URL, `404` if unknown. |
| `HEAD` | `/{id}` | `200` with the real URL as the body (no redirect), `404` if unknown. |
| `DELETE` | `/{id}` | `204` on success, `404` if unknown. |

## Deployment

### One-time setup

1. **AWS account + credentials**: an IAM user (or role) with permissions to deploy Lambda, API Gateway, IAM roles, and CloudFormation (SAM deploys via CloudFormation under the hood). Store as GitHub secrets:
   ```bash
   gh secret set AWS_ACCESS_KEY_ID     --body "AKIA..."
   gh secret set AWS_SECRET_ACCESS_KEY --body "..."
   gh variable set AWS_REGION          --body "us-east-1"
   ```
2. **Network access to MySQL**: the Lambda function attaches to the same private subnet(s) the OCI VMs use (`TF_SUBNET_ID`'s value works here too, since MySQL lives in that same private network) via `VpcConfig`, plus a security group allowing outbound `3306`. This means the Lambda function needs a **NAT Gateway** (or VPC endpoint, depending on what else it needs to reach) for internet access if it ever needs it beyond MySQL -- not required for this handler today, since it only talks to MySQL, but worth knowing before adding anything that needs outbound internet (e.g. telemetry export, later).
   ```bash
   gh variable set SERVERLESS_VPC_SUBNET_IDS --body "subnet-aaaa,subnet-bbbb"
   gh variable set SERVERLESS_VPC_SG_IDS     --body "sg-cccc"
   ```
3. **MySQL connection details** (same backing service `/healthz` already uses on the VM target):
   ```bash
   gh variable set SERVERLESS_MYSQL_HOST --body "10.0.65.126"
   gh variable set SERVERLESS_MYSQL_DATABASE --body "linker_db_1"
   gh variable set SERVERLESS_MYSQL_USER --body "linker_user_1"
   gh secret   set SERVERLESS_MYSQL_PWD  --body "..."
   ```
4. **Table**: the handler creates `shorturl` (`CREATE TABLE IF NOT EXISTS`) on first cold start if it doesn't already exist -- no separate migration step needed, same as the VM target's `Main.java`.

### Deploying

Manual trigger only (`workflow_dispatch`) — this never runs automatically on push, so a team without AWS credentials configured is never blocked by it:

```bash
gh workflow run serverless-deploy.yml
```

This runs `.github/workflows/serverless-deploy.yml`:
1. `mvn package -DskipTests` — the exact same build the VM target uses, producing `target/linker1-1.0-jar-with-dependencies.jar`.
2. `sam build` + `sam deploy` against `serverless/template.yaml` — provisions the Lambda function, IAM execution role, and API Gateway REST API (`AWS::Serverless::Api`, implicit via the `Api` event source).
3. Prints the deployed API Gateway invoke URL from the CloudFormation stack outputs.

### Local testing before a real deploy

The AWS SAM CLI can invoke the packaged jar locally without deploying anything, using Docker to emulate the Lambda runtime:

```bash
mvn package -DskipTests
cd serverless
sam build --template-file template.yaml
sam local invoke LinkFunction --event events/get-existing-id.json
# or, for a full local API Gateway emulation:
sam local start-api
curl -X POST http://localhost:3000/link -d '{"url":"https://example.com"}'
```

### Verifying after deploy

```bash
API_URL=$(aws cloudformation describe-stacks --stack-name linker1-serverless \
  --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)

curl -X POST "${API_URL}link" -H "Content-Type: application/json" -d '{"url":"https://example.com"}'
# -> 201, an 8-char id in the body

curl -i "${API_URL}<the-id-from-above>"
# -> 301, Location: https://example.com
```

## Testing strategy

`test/linker/serverless/LinkLambdaHandlerTest.java` unit-tests the handler directly (no AWS account, no Docker, no live MySQL): constructs `APIGatewayProxyRequestEvent` objects by hand and calls `handleRequest()` against a `LinkService` backed by an in-memory SQLite connection (`LinkRepository` doesn't care which JDBC driver it's given). This exercises the exact same routing/business logic that runs in production against MySQL — only the connection source differs, and that swap lives entirely in `LambdaComposition`.

`LinkLambdaHandler`'s real, MySQL-wiring no-arg constructor is excluded from the JaCoCo 100%-coverage gate via a `@Generated`-annotated constructor (see `linker/serverless/Generated.java`) — the same rationale as `Main.class`'s existing whole-class exclusion (a composition root that opens a real network connection has no meaningful unit test without live infrastructure), applied at constructor granularity so the rest of the class still counts normally.

## Known limitations / things to verify once deployed

- **Cold starts**: the fat jar bundles Javalin/Jetty/LaunchDarkly/OpenTelemetry SDK classes that this handler never touches -- dead weight increasing Lambda package size and JVM classloading time on cold start. Acceptable for a bonus/demo deployment; a production-grade serverless target would likely want a slimmer, Lambda-specific jar (a second `maven-assembly-plugin` execution with a minimal descriptor) as a future optimization, which would still satisfy "same source, no duplicated logic" even if the *build artifact* diverges from the VM target's.
- **VPC cold start latency**: attaching a Lambda function to a VPC (required here for MySQL access) has historically added cold-start latency; AWS's Hyperplane ENI improvements have largely mitigated this since 2019, but it's worth measuring against this specific MySQL instance/region rather than assuming.
- **Concurrency vs. MySQL connections**: `LambdaComposition.defaultService()` opens one MySQL connection per Lambda execution environment (reused across warm invocations, not per-request) — under high concurrency, many simultaneous cold starts could open many MySQL connections at once. Not a problem at demo/bonus scale; would need connection pooling (e.g. RDS Proxy) for production traffic.

## Related documents

- [Deployment (VM / blue-green)](DEPLOYMENT.md) — the primary PROD target this is additive to.
- [Health Check](HEALTHCHECK.md) — why MySQL was already wired up as a backing service before this bonus existed.
- [`serverless/template.yaml`](../serverless/template.yaml) — the SAM IaC.
- [`.github/workflows/serverless-deploy.yml`](../.github/workflows/serverless-deploy.yml) — the deploy pipeline.
- [`src/linker/serverless/`](../src/linker/serverless/) — the handler, composition root, and coverage-exclusion marker.
