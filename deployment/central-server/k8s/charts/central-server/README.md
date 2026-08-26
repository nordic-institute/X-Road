# central-server Helm chart

> **NOT production-ready.** This chart deploys the single all-in-one
> `central-server-dev` image — the same image docker-compose uses for
> local/e2e development — as one Kubernetes Deployment. It does not deploy a
> decomposed Central Server (separate admin-service, management-service,
> registration-service workloads). Every rendered resource carries the label
> `xroad.niis.org/production-ready: "false"`, and `Chart.yaml` carries the
> matching annotation.

## 1. Overview

The chart mirrors the Security Server chart's structure (`deployment/security-server/k8s/charts/security-server`):
a values-driven `services` map, the same `xroad.labels` / `xroad.service` /
`xroad.deployment` / `xroad.serviceaccount` helper templates, and
`global.image.registry` / `global.image.tag` for image coordinates. Today the
`services` map has exactly one entry, `central-server`, wrapping the
monolithic dev image. The shape is intentionally the same as the Security
Server chart's so that decomposing the Central Server later — splitting
admin-service/management-service/registration-service into their own
Deployments — is a matter of adding more `services.*` entries, not a
templates rewrite.

## 2. What the dev image is

`central-server-dev` (built by `core/scripts/images/build-central-server.sh`)
packages the real `xroad-centralserver` Debian install plus an embedded
PostgreSQL cluster and a co-located Issuer Service, all managed by
`supervisord` under a single root-running container — see
`development/docker/central-server/Dockerfile` and
`files/cs-entrypoint.sh`. It is a dev/e2e convenience image, not a shape any
production deployment should imitate.

Because it runs postgres/supervisord/nginx as root and writes across its own
filesystem (`/etc/postgresql`, `/var/lib/postgresql`, `/etc/xroad`, …), this
chart's `securityContext.pod` / `securityContext.container` values default to
`{}` rather than the Security Server chart's rootless/read-only-rootfs
profile — faking that hardening posture here would be actively misleading.

## 3. Ports

Verified against the `cs` service in
`core/src/security-server/e2e-test/src/intTest/resources/compose.aux.yaml`:

| Port | Name | Purpose |
|---|---|---|
| 80 | `gconf-http` | Global configuration distribution (`/internalconf`) |
| 443 | `gconf-https` | Global configuration distribution (TLS) |
| 4000 | `https` | Admin UI / frontend |
| 4001 | `reg-service` | Registration service (auth-cert registration) |
| 4002 | `mgmt-service` | Management service (client/subsystem registration) |
| 5432 | `postgres` | Embedded database |
| 6183 | `issuer-did` | Co-located Issuer Service — DID resolution |
| 6185 | `issuer-issuance` | Co-located Issuer Service — issuance API |
| 6187 | `issuer-status` | Co-located Issuer Service — status list API |
| 9994 | `signer-debug` | Signer remote debug |

## 4. Image coordinates and `imagePullSecrets`

Image ref is `{{ .Values.global.image.registry }}/{{ .Values.services.central-server.imageName }}:{{ .Values.global.image.tag }}`,
same composition rule as the Security Server chart. Default registry points
at the GHCR path the CI build uses (`ghcr.io/nordic-institute/x-road`).

The `central-server-dev` GHCR package is **private** — anonymous pulls are
denied. Set `imagePullSecrets` to the name of a `kubernetes.io/dockerconfigjson`
Secret holding a token with `packages:read` scope:

```bash
helm template . --set imagePullSecrets[0]=ghcr-pull-secret
```

The chart does not create that Secret; provisioning it (e.g. via the kind
cluster bring-up) is out of scope for this chart.

## 5. Out of scope

- A production-grade, decomposed Central Server chart (separate
  admin/management/registration workloads).
- E2E fixtures and hurl bootstrap — the `e2e-fixtures` chart.
- Topology wiring (ansible inventory, port-forwards) — owned by
  `development/k8s` and `core/scripts/env-k8s`.

## 6. Rendering

Local `helm lint` is broken under the installed Helm version even against the
unmodified Security Server chart (`invalid Yaml document separator`) — a
known local tooling issue, not specific to this chart. Use `helm template`
for render verification:

```bash
helm template test-cs . \
  --set imagePullSecrets[0]=ghcr-pull-secret \
  --set global.image.tag=8.0.0-dev
```
