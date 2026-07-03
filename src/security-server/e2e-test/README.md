# Security Server e2e test suite

End-to-end tests for the Security Server. The suite runs against two targets,
selected by `test-framework.env-mode`:

| Mode | Value | What it talks to |
|------|-------|------------------|
| Compose (default) | `compose` | Docker Compose stack, brought up by the harness |
| LXD | `lxd` | Pre-provisioned LXD containers, managed externally |

## Running locally — Compose mode

Compose mode is the default. The harness starts the Compose stack, runs the
bootstrap, executes all non-skipped scenarios, then tears the stack down.

```bash
cd core/src
./gradlew :security-server:e2e-test:e2eTest
```

To serve the Allure report after the run:

```bash
./gradlew :security-server:e2e-test:e2eTest -Pe2eTestServeReport=true
```

## Running locally — LXD mode

LXD mode assumes the LXD environment is already running and bootstrapped. The
harness does **not** provision, bootstrap, or tear anything down — that is the
responsibility of the provisioning tooling.

1. Provision and bootstrap the environment:

   ```bash
   cd core/development/native-lxd-stack
   ./start-env.sh
   ```

   This runs Ansible (`xroad_dev.yml`) and the hurl bootstrap (`xroad-hurl`
   role), producing a fully federated LXD stack at the `xrd-*.lxd` hosts.

2. Ensure your workstation resolves `xrd-*.lxd` hostnames. On Linux run:

   ```bash
   ./scripts/setup-linux-net.sh apply
   ```

3. Run the suite against the live LXD environment:

   ```bash
   cd core/src
   ./gradlew :security-server:e2e-test:e2eTestLxd
   ```

The `e2eTestLxd` task sets `-Dtest-framework.env-mode=lxd` and filters to
`not @Skip and not @compose-only`. Address defaults mirror the Ansible
`vars.env` (`xrd-ss0.lxd`, `xrd-ss1.lxd`, etc.) and can be overridden via
environment variables — see `test-framework.lxd.*` properties.

## Compose-only scenarios

Scenarios that require Docker-specific operations (running commands inside a
container, reading files from a container) or depend on a Compose-only feature
overlay (HSM, batch signatures, message-log encryption) must be tagged
`@compose-only`. The LXD run filters them out.

**Maintenance rule:** if you add a scenario that uses an in-container operation
or a Compose-only feature overlay, tag it `@compose-only`. Without the tag it
will run in the LXD CI job and fail.

Currently `@compose-only` covers:

- `0200-ss-messagelog.feature` — message-log CLI and archive operations require
  running commands inside the proxy container.

## CI

The LXD suite runs on every pull request as a separate job (`lxd-e2e`) alongside
the unchanged Compose e2e job (`e2e-tests`). Provisioning reuses the DEB/RPM
package artifacts produced by the build job — nothing is rebuilt from source.
A failed LXD run marks the PR checks red.

To narrow the LXD job back to manual dispatch only, change one line in
`.github/workflows/build.yaml`:

```yaml
# from:
if: ${{ github.event_name == 'workflow_dispatch' || github.event_name == 'pull_request' }}
# to:
if: ${{ github.event_name == 'workflow_dispatch' }}
```
