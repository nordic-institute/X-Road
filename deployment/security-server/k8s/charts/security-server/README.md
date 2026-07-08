# security-server Helm chart

## 1. Overview

Helm chart for deploying the X-Road Security Server on Kubernetes. The
chart ships the core Security Server workloads (proxy, proxy-ui-api,
signer, configuration-client, monitor, auxiliary-service, per-service
init Jobs and their supporting Secrets / ConfigMaps / PVCs) by default,
and additionally carries the dataspace (DSP) stack whose application
Pods default to `replicas: 0` — no DSP Pods start unless explicitly
enabled via per-service `replicas` overrides.

## 2. Dataspace (DSP) services

DSP application workloads (`ds-control-plane`, `ds-identity-hub`,
`ds-issuer-service`, `mock-jwks-server`) are folded into the standard
`services.*` map alongside core Security Server services. They follow
the same schema (imageName, replicas, env, envFromSecrets, ports,
probes, resources, volumeMounts).

**Default behavior:** all four DSP services default to `replicas: 0` —
no DSP Pods are created. DSP infrastructure resources rendered by the
chart (mock-jwks-server keys ConfigMap, serverconf seed Job) always
render regardless of DSP replica counts. **DSP database clusters are
not rendered by this chart** — they must be provisioned externally (in
the dev stack by the `cloudnative_pg` ansible role, in production by
whatever operator owns DSP state). Each DS service expects a bare
`db-<name>` Service plus a basic-auth Secret named `db-<name>` with
key `password`.

**Enabling a DSP service:** set `replicas` to 1 (or higher) and supply
the full image reference via `imageName`. If `imageName` is empty when
`replicas > 0`, Helm will abort with a named `required` error.

```bash
helm template . \
  --set services.ds-control-plane.replicas=1 \
  --set services.ds-control-plane.imageName=myregistry/ds-control-plane:1.0 \
  --set services.ds-identity-hub.imageName=myregistry/ds-identity-hub:1.0 \
  --set services.ds-issuer-service.imageName=myregistry/ds-issuer-service:1.0 \
  --set services.mock-jwks-server.imageName=python:3.11-alpine \
  --set postgres.image=postgres:17
```

To enable all four DSP services:

```bash
helm template . \
  --set services.ds-control-plane.replicas=1 \
  --set services.ds-control-plane.imageName=myregistry/ds-control-plane:1.0 \
  --set services.ds-identity-hub.replicas=1 \
  --set services.ds-identity-hub.imageName=myregistry/ds-identity-hub:1.0 \
  --set services.ds-issuer-service.replicas=1 \
  --set services.ds-issuer-service.imageName=myregistry/ds-issuer-service:1.0 \
  --set services.mock-jwks-server.replicas=1 \
  --set services.mock-jwks-server.imageName=python:3.11-alpine \
  --set postgres.image=postgres:17
```

## 3. Required values for DSP services

The chart renders **no default application images** for DSP services.
Every required input must be supplied explicitly. `helm template` /
`helm install` fail fast with a named `required` error otherwise.

Required values for each DSP service with `replicas > 0`:

```
- --set services.<name>.imageName=<full-image-ref>  # REQUIRED when replicas>0; no default
- --set postgres.image=<image>                       # REQUIRED for DSP wait-gate initContainers
```

## 4. DSP database prerequisites

For each enabled DS service, the chart expects these externally-managed
resources in the release namespace:

| Service | Bare Service DNS | Secret name | Secret keys |
|---|---|---|---|
| `ds-control-plane`   | `db-ds-control-plane`   | `db-ds-control-plane`   | `username`, `password` |
| `ds-identity-hub`    | `db-ds-identity-hub`    | `db-ds-identity-hub`    | `username`, `password` |
| `ds-issuer-service`  | `db-ds-issuer-service`  | `db-ds-issuer-service`  | `username`, `password` |

The `cloudnative_pg` ansible role produces exactly this shape (Secret
type `kubernetes.io/basic-auth`, ExternalName Service alias to the CNPG
`-rw` primary). Any other provider — an operator-managed cluster, an
`ExternalSecret`, etc. — must match the same names and keys.

## 5. Compose → Helm parity table

This table is the **canonical anti-drift artefact**. Every compose
service in `core/src/security-server/api-test/src/intTest/resources/compose.api.ds.yaml`
MUST map to a Helm template; every env var, port, probe, and
`depends_on` edge MUST be reflected in the corresponding Helm template.

| Compose service | Helm template / values path | Env vars | Ports | Probe | Depends-on → initContainer(s) | Source-of-truth compose lines |
|---|---|---|---|---|---|---|
| `db-ds-control-plane` | external (cloudnative_pg ansible role) — chart expects bare Service `db-ds-control-plane` and Secret `db-ds-control-plane/password` | — (provisioned outside chart) | 5432 | CNPG-managed | — (no depends_on) | compose lines 19–42 |
| `db-ds-identity-hub` | external (cloudnative_pg ansible role) — chart expects bare Service `db-ds-identity-hub` and Secret `db-ds-identity-hub/password` | — (provisioned outside chart) | 5432 | CNPG-managed | — (no depends_on) | compose lines 93–114 |
| `db-ds-issuer-service` | external (cloudnative_pg ansible role) — chart expects bare Service `db-ds-issuer-service` and Secret `db-ds-issuer-service/password` | — (provisioned outside chart) | 5432 | CNPG-managed | — (no depends_on) | compose lines 150–171 |
| `ds-control-plane` | `services.ds-control-plane` in `values.yaml` + `templates/services/all.yaml` | `HOSTNAME`, `XROAD_SECRET_STORE_HOST`, `XROAD_SECRET_STORE_TOKEN` (secretKeyRef), `XROAD_SECRET_STORE_SCHEME`, `XROAD_COMMON_RPC_CHANNEL_CONFIGURATION_CLIENT_HOST`, `XROAD_DB_DS_CONTROL_PLANE_HIBERNATE_CONNECTION_URL`, `XROAD_DB_DS_CONTROL_PLANE_HIBERNATE_CONNECTION_USERNAME`, `XROAD_DB_DS_CONTROL_PLANE_HIBERNATE_CONNECTION_PASSWORD` (secretKeyRef), `EDC_IAM_OAUTH2_JWKS_URL`, `EDC_IAM_DID_WEB_USE_HTTPS`, `DB_CONFIG_SOURCE_ENABLED`, `DB_CONFIG_SOURCE_URL`, `DB_CONFIG_SOURCE_USERNAME`, `DB_CONFIG_SOURCE_PASSWORD` (secretKeyRef) | 8181, 8182, 8183, 8184, 9999 | `httpGet :4099/q/health` (HTTP) — 5s/40/initialDelay=5s | `db-ds-control-plane` → `wait-db`; `mock-jwks-server` → `wait-mock-jwks`; `configuration-client` → `wait-config-client`; `ds-identity-hub` → `wait-identity-hub`; `db-serverconf-ds-control-plane-config-seed` → `wait-serverconf-seed` | compose lines 44–91 |
| `ds-identity-hub` | `services.ds-identity-hub` in `values.yaml` + `templates/services/all.yaml` | `HOSTNAME`, `EDC_IH_DID_PUBLIC_HOSTNAME`, `XROAD_SECRET_STORE_HOST`, `XROAD_SECRET_STORE_TOKEN` (secretKeyRef), `XROAD_SECRET_STORE_SCHEME`, `XROAD_DB_DS_IDENTITY_HUB_HIBERNATE_CONNECTION_URL`, `XROAD_DB_DS_IDENTITY_HUB_HIBERNATE_CONNECTION_USERNAME`, `XROAD_DB_DS_IDENTITY_HUB_HIBERNATE_CONNECTION_PASSWORD` (secretKeyRef), `EDC_IAM_OAUTH2_JWKS_URL`, `EDC_IAM_DID_WEB_USE_HTTPS`, `EDC_ENCRYPTION_STRICT`, `DEBUG` | 9999, 8182, 10001, 10100 | `httpGet :8181/api/check/health` (HTTP) | `db-ds-identity-hub` → `wait-db`; `mock-jwks-server` → `wait-mock-jwks` | compose lines 116–148 |
| `ds-issuer-service` | `services.ds-issuer-service` in `values.yaml` + `templates/services/all.yaml` | `HOSTNAME`, `XROAD_SECRET_STORE_HOST`, `XROAD_SECRET_STORE_TOKEN` (secretKeyRef), `XROAD_SECRET_STORE_SCHEME`, `XROAD_COMMON_RPC_CHANNEL_CONFIGURATION_CLIENT_HOST`, `XROAD_DB_DS_ISSUER_SERVICE_HIBERNATE_CONNECTION_URL`, `XROAD_DB_DS_ISSUER_SERVICE_HIBERNATE_CONNECTION_USERNAME`, `XROAD_DB_DS_ISSUER_SERVICE_HIBERNATE_CONNECTION_PASSWORD` (secretKeyRef), `EDC_IAM_OAUTH2_JWKS_URL`, `EDC_IAM_DID_WEB_USE_HTTPS`, `EDC_ENCRYPTION_STRICT`, `DEBUG` | 8182, 10011, 10012, 10013, 10100, 9999 | `httpGet :8383/api/check/health` (HTTP) | `db-ds-issuer-service` → `wait-db`; `mock-jwks-server` → `wait-mock-jwks` | compose lines 173–213 |
| `mock-jwks-server` | `services.mock-jwks-server` in `values.yaml` + `templates/services/all.yaml` + `templates/dsp/mock-jwks-server-keys-configmap.yaml` | — (keys via mounted ConfigMap from `files/jwks/public_key.json`) | 8080 | `httpGet :8080/jwks.json` (HTTP) — 5s period, failureThreshold 3, initialDelay 5s | — (no depends_on, no initContainers) | compose lines 215–232 |
| `db-serverconf-ds-control-plane-config-seed` | `templates/dsp/db-serverconf-ds-control-plane-config-seed-job.yaml` | `PGPASSWORD` (secretKeyRef → `db-serverconf/password`) | — (Job, no Service) | — (no probe; `ON CONFLICT DO NOTHING` idempotency) | `db-serverconf-init` → `wait-db-serverconf` (polls `configuration_properties` table; `activeDeadlineSeconds: 300s`) | compose lines 2–17 |

## 6. Single-release-per-namespace constraint (dsp)

DSP application Services render with bare names (`ds-control-plane`,
`ds-identity-hub`, `ds-issuer-service`, `mock-jwks-server`) — NOT
prefixed with `.Release.Name`. The corresponding database Services
(`db-ds-control-plane`, `db-ds-identity-hub`, `db-ds-issuer-service`)
are provisioned externally and also use bare names. This is deliberate:
the compose source-of-truth hard-codes these hostnames in env vars like
`XROAD_DB_DS_*_HIBERNATE_CONNECTION_URL`, and compose-exact DNS parity
is a merge-block AC.

Consequence: **two Helm releases with DSP services enabled (`replicas >
0`) cannot coexist in the same namespace** — the second `helm install`
fails on resource-name collision. Release-scoped isolation requires
separate namespaces. Only the `.Release.Name`-prefixed resources
(mock-jwks-server keys ConfigMap, serverconf seed Job) are
multi-release-safe by construction.

## 7. Seed Job behaviour

The chart always renders a Kubernetes `Job` that seeds the
trusted-issuer configuration property row into the existing
`db-serverconf` database used by the core Security Server's
`ds-control-plane` component:

- **Name:** `{{ .Release.Name }}-db-serverconf-ds-control-plane-config-seed`
  (prefixed per chart convention).
- **SQL:** inserts `configuration_properties('edc.iam.trusted-issuer.issuer.id',
  'did:web:ds-issuer-service%3A10100:issuer', 'ds-control-plane', …)`
  into `db-serverconf`, compose-verbatim.
- **Idempotent:** the SQL uses `ON CONFLICT DO NOTHING` — safe on
  re-install and `helm upgrade`.
- **Hook strategy:** runs as a `post-install,post-upgrade` Helm hook
  with `helm.sh/hook-weight: "10"` and
  `helm.sh/hook-delete-policy: before-hook-creation,hook-succeeded`.

> **Rollback gap.** `helm rollback` does **not** re-fire the seed Job.
> If a rollback restores a DB snapshot taken before the seed row was
> inserted, the `ds-control-plane` `wait-serverconf-seed` initContainer
> will hang until its retry budget is exhausted. Re-seed manually or
> `helm upgrade` to re-trigger the hook.

## 8. Chart version discipline

The `Chart.yaml` `version` key is bumped only when the chart's public
contract changes materially:

- **Current version:** `0.3.0` — bumped from `0.2.0` alongside the
  seed Job + README rewrite.
- `appVersion` tracks the X-Road runtime version and is independent of
  the chart version.
