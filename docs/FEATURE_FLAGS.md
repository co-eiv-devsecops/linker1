# Feature Flags Technical Documentation

This document describes the Feature Flagging implementation and strategy for the Linker project. It serves as a technical reference guide for the development team on how to manage, evaluate, and phase out feature flags in our codebase.

---

## Feature Flag Strategy

In modern software development, decoupling code deployments from feature releases is a core practice of resilient software engineering. We use Feature Flags to:
* **Decouple Deployments from Releases:** Code can be safely deployed to production at any time with new features turned off. This allows us to merge code to the main branch continuously without exposing unfinished features to end users.
* **Mitigate Release Risk:** By releasing features gradually, we minimize the blast radius of any unexpected bugs or performance issues.
* **Enable Fast Rollbacks:** If a feature behaves unexpectedly in production, it can be disabled instantly via the LaunchDarkly dashboard without needing a code rollback or redeployment.

---

## Current Feature Flags

The table below lists the active feature flags managed in the system:

| Feature Flag | Description | Default State | Owner |
| :--- | :--- | :--- | :--- |
| `new-ui` | Enables the new version of the web interface (V2 Colombia Edition). | OFF | Development Team |
| `head-delete` | Enables HEAD /{id} (metadata) and DELETE /{id} (remove link) endpoints. | OFF | Development Team |

---

## Architecture

To maintain low coupling, high cohesion, and ease of testing, our architecture strictly isolates the LaunchDarkly SDK from the rest of the application using **Dependency Injection (DI)** and **Inversion of Control (IoC)**.

### Composition Root
`Main` acts as the composition root. It is responsible for reading the configuration, initializing the `LDClient` (LaunchDarkly SDK Client), and injecting the initialized instance into the `FeatureFlags` configuration bean.

### ASCII Diagram
The architecture is structured as follows:

```text
Main (Composition Root)
 ├── LinkRepository
 ├── LinkService
 ├── FeatureFlags
 │        │
 │        ▼
 │   LaunchDarkly (SDK)
 │
 ├── StaticRoutes
 └── LinkRoutes
```

### Key Architectural Rules
1. **LaunchDarkly SDK Encapsulation:** `FeatureFlags` is the **only** class in the entire codebase that imports or interacts with the LaunchDarkly SDK (`LDClient`, `LDContext`, etc.).
2. **Abstract Interface Consumption:** Classes like `StaticRoutes` receive the `FeatureFlags` instance via their constructor and query it using simple helper methods (e.g., `featureFlags.isNewUiEnabled()`). They have no knowledge of how the flag is evaluated or what SDK is being used behind the scenes.
3. **Testability:** This design allows us to easily test our routes in isolation. In our tests, we inject a subclass or mock of `FeatureFlags` that returns a predefined boolean value, eliminating the need to connect to LaunchDarkly during testing.

---

## Rollout Strategy

Every new feature integrated via feature flags should follow this rollout checklist:

1. **Develop the Feature:** Implement the new functionality wrapped in a feature flag check.
2. **Deploy with the Feature Flag OFF:** Deploy the code to production with the flag disabled globally.
3. **Enable for Internal Testing:** Target internal developers or QA accounts for testing in the live environment.
4. **Enable for a Small Percentage of Users:** Use LaunchDarkly's targeting rules to roll out the feature to a subset of users (e.g., 10% canary release).
5. **Enable for All Users:** Complete the rollout by setting the flag to 100% enabled for everyone.
6. **Remove the Legacy Implementation:** Once verified, delete the old code paths from the codebase.
7. **Remove the Feature Flag:** Clean up the feature flag from both the code and the LaunchDarkly dashboard.

This process drastically reduces release risks and ensures high availability.

---

## Legacy Code Phase-Out

To avoid technical debt, we employ a progressive phase-out of legacy code. During the transition phase:
* Both `index.html` (V1) and `index-v2.html` (V2) will coexist in the project resources.
* The `new-ui` feature flag dynamically decides which asset to serve on each HTTP request.

Once the new interface has been fully validated in production:
* `index.html` (V1) and `styles.css` (V1) will be deleted.
* The check `featureFlags.isNewUiEnabled()` and the `new-ui` flag configuration will be removed from the project.
* `index-v2.html` and `styles2.css` will be renamed/moved to become the permanent, single implementation.

---

## Local Development

To run the application locally, you must provide your LaunchDarkly SDK key. The application reads this key from the `LD_SDK_KEY` environment variable.

### Visual Studio Code Configuration
You can configure this environment variable in your `.vscode/launch.json` file as follows:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Launch Linker (Main)",
            "request": "launch",
            "mainClass": "Main",
            "projectName": "linker1",
            "env": {
                "LD_SDK_KEY": "sdk-xxxxxxxxxxxxxxxx"
            }
        }
    ]
}
```

*Note: Replace `sdk-xxxxxxxxxxxxxxxx` with your actual LaunchDarkly Client SDK key.*

---

## Benefits

By implementing this architecture and strategy, we have achieved the following:
* **Safe Releases:** Features can be released to production without disrupting existing users.
* **Progressive Rollout:** Allows for targeted and percentage-based releases.
* **Fast Rollback:** Turn off buggy features instantly without redeploying.
* **Better Maintainability:** Clean separation between business logic and framework SDKs.
* **Dependency Injection & Low Coupling:** Clean architecture where classes depend on abstractions, not concrete SDK classes.
* **Easier Testing:** Simple unit and integration tests using offline configurations and overrides.
* **Continuous Delivery Readiness:** Code can be continuously merged to the main branch and deployed safely.

---

## Pipeline-Driven Release Workflow

In addition to manual toggling via the LaunchDarkly dashboard, feature flags can be managed through a dedicated **GitHub Actions workflow** (`feature-launch.yml`). This workflow is completely independent from `ci.yml`, `pipeline.yml`, and `release.yml` — it does **not** trigger any build, test, or deploy job. This enforces the decoupling between deployment and release.

### Workflow: `feature-launch.yml`

| Trigger | Inputs | Environment |
| :--- | :--- | :--- |
| `workflow_dispatch` (manual) | `flag-key`, `state` (on/off), `project-key`, `comment` | `LAUNCHDARKLY_API_KEY` (GitHub secret) |

The workflow calls the [LaunchDarkly REST API](https://1.n-la-c.app/doc-launchdarkly-api) (`PATCH /api/v2/flags/{project}/{flagKey}`) using the `turnFlagOn` / `turnFlagOff` instruction kinds. It does **not** use the LaunchDarkly SDK — the API access token is only used in CI, never baked into the application.

### Required Setup

1. Create a **LaunchDarkly API access token** (not an SDK key) in your LaunchDarkly dashboard: **Settings → Authorization → API access tokens**.
2. Add the token as a GitHub Actions secret named `LAUNCHDARKLY_API_KEY` in the repository settings.
3. Ensure the flag already exists in LaunchDarkly (created via dashboard or API).

### Usage

1. Go to your repository on GitHub → **Actions** → **Feature Launch** → **Run workflow**.
2. Fill in the inputs:
   - `flag-key`: The flag key (e.g., `new-ui`, `head-delete`).
   - `state`: `on` to enable the feature, `off` to disable it.
   - `project-key`: The LaunchDarkly project key (defaults to `default`).
   - `comment`: Optional audit log message.
3. Click **Run workflow**.

The workflow will toggle the flag and confirm the new state via a read-back call.

### Example: Dark Launch + Release

This demonstrates the deploy/release decoupling for the `head-delete` flag:

1. **Deploy**: Code for `HEAD /{id}` and `DELETE /{id}` is merged and deployed via `pipeline.yml` with the `head-delete` flag **OFF**. Users cannot see or use these endpoints.
2. **Release**: Once testing is complete, run the **Feature Launch** workflow with `flag-key: head-delete` and `state: on`. The endpoints become active immediately — no rebuild, no redeploy.
3. **Rollback**: If an issue is found, run the same workflow with `state: off` to instantly disable the feature.

---

## Candidate Features for Flag-Based Release

The following upcoming features are good candidates for shipping behind a feature flag using this pipeline:

| Feature | Flag Key | Rationale |
| :--- | :--- | :--- |
| HEAD /{id} and DELETE /{id} (#69) | `head-delete` | New HTTP methods that change API surface; safe to dark-launch until clients are ready. |
| `strict-url-validation` | `strict-url-validation` | Toggle stricter regex constraints; canary before enforcing for all users. |
| `custom-alias` | `custom-alias` | Manage availability of personalized link aliases during phased rollout. |
| `experimental-api` | `experimental-api` | Expose new REST endpoints for early adopters without impacting general users. |

---

## Conclusion

The implemented architecture enables continuous deployment with minimal risk, facilitates rapid experimentation, and prepares the project to evolve safely. By isolating the LaunchDarkly SDK inside a dedicated configuration wrapper and using constructor-based dependency injection, we have created a resilient, decoupled, and highly maintainable software system.
