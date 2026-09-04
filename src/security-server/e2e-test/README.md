# Security Server e2e test suite

End-to-end tests for the Security Server. A single `e2eTest` task runs the suite
against any target; the target is chosen with the `-Pe2e.env-mode` Gradle
property, which the task forwards as the `test-framework.env-mode` system
property:

| Mode | `-Pe2e.env-mode` | What it talks to |
|------|------------------|------------------|
| Compose (default) | `compose` | Docker Compose stack, brought up by the harness |
| LXD | `lxd` | Pre-provisioned LXD containers, managed externally |

To add a deployment target: add a `case` in `E2eStackSessionListener` (mapping
the mode to its `*EnvSetup`) and run with `-Pe2e.env-mode=<new-mode>`. No Gradle
change is needed unless the new mode also needs the harness to boot a local
stack — only `compose` wires the `generateIntTestEnv`/`copyComposeFiles` deps.

## Running locally — Compose mode

Compose mode is the default. The harness starts the Compose stack, runs the
bootstrap, executes the suite, then tears the stack down.

```bash
cd core/src
./gradlew :security-server:e2e-test:e2eTest
```

To run a single test class or method directly:

```bash
./gradlew :security-server:e2e-test:e2eTest --tests "SsProxyMessageFlowTest"
```

### ss0 as a sidecar container

Compose mode also has a per-instance stack switch, `-Pe2e.ss0-stack`, that swaps ss0 (only ss0; ss1,
the Central Server stack and hurl are unaffected) from today's per-service stack to one full sidecar
container (embedded PostgreSQL, embedded OpenBao, every service under supervisord):

| `-Pe2e.ss0-stack` | ss0 shape |
|--------------------|-----------|
| `multi-container` (default) | Today's ~17-container per-service stack |
| `sidecar` | One `xroad-security-server-sidecar` container |

The sidecar image is built from tree-built Ubuntu DEBs, not published packages. From
`core/sidecar/`, after `core/scripts/packages/build-deb.sh` (or an equivalent tree build) has
produced DEBs under `core/deployment/native-packages/build/ubuntu26.04`:

```bash
cd core/sidecar
./docker-build.sh --target=slim --packages-path=../deployment/native-packages/build/ubuntu26.04
./docker-build.sh --target=full --packages-path=../deployment/native-packages/build/ubuntu26.04
```

This produces the local images `xroad-security-server-sidecar:8.0.0-slim` and
`xroad-security-server-sidecar:8.0.0`. The harness resolves the sidecar image the same way it
resolves every other service image — through the generated `.env` (`SIDECAR_IMG`, tagged
`<xroadImageRegistry>/xroad-security-server-sidecar:<xroadImageTag>`) — so push the locally built
image to the local registry under that reference before running the suite:

```bash
docker tag xroad-security-server-sidecar:8.0.0 localhost:5555/xroad-security-server-sidecar:8.0.0-beta2-SNAPSHOT
docker push localhost:5555/xroad-security-server-sidecar:8.0.0-beta2-SNAPSHOT
```

(`localhost:5555` and `8.0.0-beta2-SNAPSHOT` are `core/src/gradle.properties`'
`xroadImageRegistry`/default `xroadImageTag`; `core/scripts/build-images.sh` starts the `xrd-registry`
container if it is not already running.) Then run the suite with the variant selected:

```bash
cd core/src
./gradlew :security-server:e2e-test:e2eTest -Pe2e.ss0-stack=sidecar
```

The batch-signing and op-monitor configuration today's ss0 overlays seed via one-shot psql containers
is seeded here by scripts mounted into the sidecar's first-boot hook directory
(`/etc/xroad/entrypoint.d`, see `core/sidecar/SIDECAR.md`), calling the native `db_property.sh` tool
instead. Messagelog/archive operations are not implemented for the sidecar shape yet, so the ss0
archive scenario fails rather than passing, while bootstrap, registration, message exchange in both
directions, batch signing and op-monitor all run as normal.

## Running locally — LXD mode

LXD mode assumes the LXD environment is already running and bootstrapped. The
harness does **not** provision, bootstrap, or tear anything down — that is the
responsibility of the provisioning tooling.

1. Provision and bootstrap the environment:

   ```bash
   cd core/scripts/env-lxd
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
   ./gradlew :security-server:e2e-test:e2eTest -Pe2e.env-mode=lxd
   ```

`-Pe2e.env-mode=lxd` sets the `test-framework.env-mode=lxd` system property and
supports the same `--tests` narrowing as the default run. Address defaults
mirror the Ansible `vars.env` (`xrd-ss0.lxd`, `xrd-ss1.lxd`, etc.) and can be
overridden via environment variables — see `test-framework.lxd.*` properties.

## Compose-only operations

Tests never touch Docker or `lxc` directly: environment-specific operations go
through the `E2eEnvironment` seam and the `MessagelogDbOps`/`MessagelogArchiveOps`
interfaces, each implemented by both `E2eEnvSetup` (Compose) and `LxdEnvSetup`
(LXD). Test methods declare these interfaces as parameters; the harness injects
the active environment.

**Maintenance rule:** if you add a test that needs an in-container operation or
a Compose-only feature overlay (HSM, batch signatures), make it env-aware
through those seams. If it genuinely cannot run on LXD, guard it with

```java
@DisabledIfSystemProperty(named = "test-framework.env-mode", matches = "lxd")
```

so both the Gradle task and the fat-jar CI run skip it. Without either, it will
run in the LXD CI job and fail.

## CI

All three lanes (compose, lxd, k8s) run as one matrix job group named `e2e`,
defined once in `.github/workflows/_test-e2e.yml` and invoked once from
`build.yaml` — the Actions UI shows a single "e2e" group with one leg per
substrate, not separate jobs. The lxd leg is limited to pull requests and
manual dispatch by a guard inside `_test-e2e.yml` itself (compose and k8s run
on every trigger `build.yaml` runs for).

The matrix has no `needs: build-and-package` edge. Each leg starts with the
pipeline and runs its package-independent prep immediately — lxd tooling
install/init/networking, k8s kind/helm install — so that work overlaps the
build instead of waiting for it. Only once that prep is done does the leg poll
for a readiness marker artifact uploaded by `build-and-package`: lxd waits
for `artifacts-ready` (native packages + the e2e-test fat jar); compose and
k8s wait for `images-ready` (all image pushes, since both deploy run-tagged
images that land later in that job — the compose stack's image refs are baked
into the jar's `.env` at build time). The marker also carries the image
registry and tag,
which the lanes would otherwise get from `needs.build-and-package.outputs.*` —
unavailable without the `needs` edge. Provisioning reuses the DEB/RPM package
artifacts (lxd) or pushed images (k8s) produced by the build job — nothing is
rebuilt from source. A failed leg marks the PR checks red; a failed or
cancelled `build-and-package` fails every waiting leg within minutes instead
of idling out. The suite runs through the shadow jar, e.g.
`java -Dtest-framework.env-mode=lxd -jar e2e-test-1.0.jar` for the lxd leg.

To narrow the lxd leg back to manual dispatch only, change its `if:` guard in
`.github/workflows/_test-e2e.yml`:

```yaml
# from:
if: >
  inputs.variant != 'lxd'
  || github.event_name == 'workflow_dispatch'
  || github.event_name == 'pull_request'
# to:
if: >
  inputs.variant != 'lxd'
  || github.event_name == 'workflow_dispatch'
```
