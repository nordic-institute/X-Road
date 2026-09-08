# scripts/ — task→script map

All build, packaging, and dev-environment scripts live here. One place, no forwarders.

Run paths are relative to `scripts/` unless noted.

---

## Daily inner loop

| Task | Script | Run from |
|---|---|---|
| Compile everything | `./compile-all.sh` | `scripts/` |
| Compile one module (faster) | `./compile-module.sh <module>` | `scripts/` |
| **Build all local tiers** (infra + Security Server + Central Server) | `./build-images.sh` | `scripts/` |
| Build native DEB/RPM packages | `./build-native-packages.sh -r resolute -r rpm-el9` | `scripts/` |

`build-images.sh` is the one-command local flow: a bare run starts the local registry, builds
the dev-infra images, compiles the full codebase, and builds/pushes the Security Server and
Central Server images to `localhost:5555`. Run it before `gradlew intTest` to ensure the
containerized test tier sees fresh images. Trim tiers you don't need with `--skip-*`; for
granular single-image selection call `images/build-security-server.sh <service...>` directly.

```
./build-images.sh [options]      # bare = infra + security-server + central-server
  --skip-infra      skip the dev-infra tier (openbao, testca, postgres-dev, nginx-cp)
  --skip-ss         skip the Security Server tier (Gradle compile + SS images)
  --skip-cs         skip the Central Server tier (DEB build + CS image)
  -s, --skip-tests  skip tests during the Gradle build
  -b, --no-build    reuse existing artifacts and packages; only rebuild images
  -r, --registry R  target registry (default: localhost:5555)
      --no-registry skip the local-registry guard
```

---

## Image builds — `images/`

| Script | Builds |
|---|---|
| `images/build-security-server.sh` | SS runtime images (`ss-*`); reads `lib/service-config.csv`. For granular single-image selection call this directly, e.g. `images/build-security-server.sh proxy signer` |
| `images/build-central-server.sh` | Central Server dev image |
| `images/build-dev-infra.sh` | Dev-infra images (openbao, testca, postgres-dev, nginx-cp) |
| `images/build-builder.sh` | Package-builder toolchain images |

---

## Native packages — `packages/`

`packages/build-deb.sh` and `packages/build-rpm.sh` run **inside** the builder containers.
`build-native-packages.sh` invokes them via `docker run`; do not run them directly.

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
