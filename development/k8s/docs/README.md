# X-Road k8s Ansible (`niis.xroad_k8s`)

Ansible Collection that spins up the X-Road Security Server on a local [KinD](https://kind.sigs.k8s.io/) cluster (or a pre-provisioned EKS cluster — see [EKS.md](./EKS.md)).

## Prerequisites

- Docker Desktop (macOS) or Docker / Podman (Linux)
- `kind`, `kubectl`, `helm` on PATH
- Python 3.11+
- `ansible-core >= 2.20, < 2.21` (installed via `requirements.txt`)

One-shot install (from `core/development/k8s/`):

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
ansible-galaxy collection install -r requirements.yml
```

Keep the venv activated when running ansible-playbook directly. `scripts/env-k8s/_common.sh` auto-activates `.venv/` if present, so new shells don't need the manual `source`.

## Usage

Bring up the dev environment:

```bash
core/scripts/env-k8s/start-env.sh --env=dev
```

Tear it down:

```bash
core/scripts/env-k8s/delete-env.sh --env=dev
```

Restart port-forwards after a `kubectl` hiccup:

```bash
core/scripts/env-k8s/port-forward.sh --env=dev
```

### Scripts

All live under `core/scripts/env-k8s/`:

| Script | Purpose |
|---|---|
| `start-env.sh` | Bring up the full k8s stack (preflight → images → ansible → port-forward → hurl) |
| `delete-env.sh` | Tear down helm releases + optionally delete the KinD cluster |
| `port-forward.sh` | Start/restart `kubectl port-forward` for 4000, 5500, 5577, 8080, 8443 |
| `preflight.sh` | Detect missing tooling and print install hints per OS |
| `dev.sh` | Per-service rebuild + redeploy (`kind load docker-image` + `kubectl rollout restart`) |
| `init-ss2.sh` | Hurl bootstrap for SS2 (assumes external CS/CA/SS0 are already running) |
| `lint.sh` | Run `ansible-lint` + `yamllint` |

Image build lives at `core/scripts/images/build-security-server.sh` — called automatically by `start-env.sh` and `dev.sh`.

### Common flags for `start-env.sh`

- `--env=dev|test|eks` — target environment (default `dev`)
- `--recreate` — delete the existing kind cluster before bringing it back up
- `--skip-images` — don't rebuild the Security Server container images
- `--skip-forward` — don't start port-forwards
- `--skip-init` — don't run `scripts/env-k8s/init-ss2.sh` (hurl bootstrap against external CS/CA/SS0)
- `--skip-preflight` — skip tooling check
- `-e VAR=VAL` — forwarded to `ansible-playbook --extra-vars`

### Dev loop (single-service rebuild + redeploy)

Mirrors `core/scripts/env-lxd/dev.sh` for the k8s world. Rebuilds one service image, loads it directly into every kind node (bypassing the containerd pull cache), then rolls the matching Deployment.

```bash
core/scripts/env-k8s/dev.sh -bdm proxy            # rebuild + redeploy proxy
core/scripts/env-k8s/dev.sh -dm proxy-ui-api      # redeploy latest image only (no build)
core/scripts/env-k8s/dev.sh -bm signer            # rebuild only, deploy later
core/scripts/env-k8s/dev.sh -h                    # help + supported service names
```

Works against the current kubectl context. For non-`dev` envs (EKS, test-on-Artifactory) the `kind load` step will fail and you should instead push to the target registry.

### Linting

```bash
core/scripts/env-k8s/lint.sh                      # ansible-lint + yamllint
```

## Environments

| Environment | Cluster | Charts | Registry |
|---|---|---|---|
| `dev` | KinD `xroad-dev-cluster` | Local `core/deployment/security-server/k8s/charts/...` | `localhost:5555` (via containerd mirror) |
| `test` | KinD `xroad-test-cluster` | Artifactory `oci://artifactory.niis.org/xroad8-snapshot-helm` | `artifactory.niis.org/xroad8-snapshot-image` |
| `eks` | Pre-provisioned EKS | Artifactory release OCI repo | ECR |

Environment-specific values live in `inventory/<env>/group_vars/all.yml`.

## Host OS notes

- **macOS**: Docker Desktop provides `host.docker.internal` natively. Install helpers with `brew install kind kubectl helm`.
- **Linux**: `host.docker.internal` only resolves if Docker was started with `--add-host=host.docker.internal:host-gateway` (Docker 20.10+). Override `external_service_host` in a local inventory override if needed.

## Related docs

- [EKS.md](./EKS.md) — EKS deployment guide
