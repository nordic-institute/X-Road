# scripts/ — task→script map

All build, packaging, and dev-environment scripts live here. One place, no forwarders.

Run paths are relative to `scripts/` unless noted.

---

## Daily inner loop

| Task | Script | Run from |
|---|---|---|
| Compile everything | `./compile-all.sh` | `scripts/` |
| Compile one module (faster) | `./compile-module.sh <module>` | `scripts/` |
| **Compile + build/push all images** (recommended pre-test flow) | `./build-local.sh` | `scripts/` |
| Build native DEB/RPM packages | `./package.sh -r resolute -r rpm-el9` | `scripts/` |

`build-local.sh` is the one-command local flow: it starts the local registry, compiles the
full codebase, and builds and pushes all Security Server images to `localhost:5555`. Run it
before `gradlew intTest` to ensure the containerized test tier sees fresh images.

```
./build-local.sh [service...] [options]
  service...        one or more service names (default: all)
  -s, --skip-tests  skip tests during the Gradle build
  -b, --no-build    reuse existing artifacts; only rebuild images
  -r, --registry R  target registry (default: localhost:5555)
      --no-registry skip the local-registry guard
```

---

## Image builds — `images/`

| Script | Builds |
|---|---|
| `images/build-security-server.sh` | SS runtime images (`ss-*`); reads `lib/service-config.csv` |
| `images/build-central-server.sh` | Central Server dev image |
| `images/build-dev-infra.sh` | Dev-infra images (openbao, testca, postgres) |
| `images/build-builder.sh` | Package-builder toolchain images |

---

## Native packages — `packages/`

`packages/build-deb.sh` and `packages/build-rpm.sh` run **inside** the builder containers.
`package.sh` invokes them via `docker run`; do not run them directly.

---

## Helm charts — `charts/`

| Script | Purpose |
|---|---|
| `charts/publish.sh` | Package + push charts to an OCI registry |
| `charts/test-local.sh` | Local smoke test: package + push to `localhost:5555` |

---

## Dev environments

Both stacks have `dev.sh` for per-module inner-loop work and `start-env.sh` for full bring-up.
The two `dev.sh` files are distinct scripts disambiguated by directory.

### LXD stack — `env-lxd/`

Hosts: macOS (Lima VM) and Linux/WSL2 (direct). See `env-lxd/README.md` for setup and
`env-lxd/docs/linux-wsl-setup.md` for the Linux/WSL2 procedure.

| Script | Purpose |
|---|---|
| `env-lxd/start-env.sh` | Compile + package + provision containers + init |
| `env-lxd/stop-env.sh` | Stop environment and clean up host networking |
| `env-lxd/stop-all-lxd-containers.sh` | Stop all `xrd-*` containers quickly |
| `env-lxd/dev.sh` | Per-module inner loop (build / deploy / both) |
| `env-lxd/scripts/delete-env.sh` | Destroy the containers |
| `env-lxd/scripts/snapshot-containers.sh` | Snapshot containers for fast reset |
| `env-lxd/scripts/restore-containers.sh` | Restore from snapshot |
| `env-lxd/scripts/setup-mac.sh` | One-time macOS provisioning (Lima, LXC, socket_vmnet) |

### Kubernetes / KinD stack — `env-k8s/`

| Script | Purpose |
|---|---|
| `env-k8s/start-env.sh` | Build images → KinD cluster → Helm install → init |
| `env-k8s/delete-env.sh` | Tear down Helm releases + KinD cluster |
| `env-k8s/port-forward.sh` | (Re)start kubectl port-forwards |
| `env-k8s/preflight.sh` | Check required tooling |
| `env-k8s/lint.sh` | ansible-lint + yamllint |
| `env-k8s/dev.sh` | Per-service inner loop (build image + load + rollout) |

```
env-lxd/dev.sh -b -d -m <module>
env-k8s/dev.sh -b -d -m <service>
  -b  build (Gradle / image)
  -d  deploy into the running stack
  -m  module or service name (resolved via lib/service-config.csv)
```

---

## Release tooling — `release/`

| Script | Purpose |
|---|---|
| `release/set-version.sh` | Interactive version-bump tool (human-operated) |
| `release/installer-s3.sh` | Publish installer bundle to S3 |
| `release/installer-artifactory.sh` | Publish installer bundle to Artifactory |

`release/_installer-common.sh` is shared by the two publish scripts; not run directly.

---

## Shared library — `lib/`

`lib/base-script.sh` is sourced by all scripts (logging, registry guard, module lookup).
`lib/service-config.csv` is the single module→image/Gradle-path map.
Neither is run directly.
