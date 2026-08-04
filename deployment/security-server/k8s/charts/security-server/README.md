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
`ds-issuer-service`) are folded into the standard `services.*` map
alongside core Security Server services. They follow the same schema
(imageName, replicas, env, envFromSecrets, ports, probes, resources,
volumeMounts).

**Default behavior:** all three DSP services default to `replicas: 0` —
no DSP Pods are created. DSP infrastructure resources rendered by the
chart (serverconf seed Job) always render regardless of DSP replica
counts. **DSP database clusters are not rendered by this chart** — they
must be provisioned externally (in the dev stack by the `cloudnative_pg`
ansible role, in production by whatever operator owns DSP state). Each DS
service expects a bare `db-<name>` Service plus a basic-auth Secret named
`db-<name>` with key `password`.

**Enabling a DSP service:** set `replicas` to 1 (or higher) and supply
the full image reference via `imageName`. If `imageName` is empty when
`replicas > 0`, Helm will abort with a named `required` error.

```bash
helm template . \
  --set services.ds-control-plane.replicas=1 \
  --set services.ds-control-plane.imageName=myregistry/ds-control-plane:1.0 \
  --set services.ds-identity-hub.imageName=myregistry/ds-identity-hub:1.0 \
  --set services.ds-issuer-service.imageName=myregistry/ds-issuer-service:1.0 \
  --set postgres.image=postgres:17
```

To enable all three DSP services:

```bash
helm template . \
  --set services.ds-control-plane.replicas=1 \
  --set services.ds-control-plane.imageName=myregistry/ds-control-plane:1.0 \
  --set services.ds-identity-hub.replicas=1 \
  --set services.ds-identity-hub.imageName=myregistry/ds-identity-hub:1.0 \
  --set services.ds-issuer-service.replicas=1 \
  --set services.ds-issuer-service.imageName=myregistry/ds-issuer-service:1.0 \
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
| `ds-control-plane` | `services.ds-control-plane` in `values.yaml` + `templates/services/all.yaml` | `HOSTNAME`, `XROAD_SECRET_STORE_HOST`, `XROAD_SECRET_STORE_TOKEN` (secretKeyRef), `XROAD_SECRET_STORE_SCHEME`, `XROAD_COMMON_RPC_CHANNEL_CONFIGURATION_CLIENT_HOST`, `XROAD_DB_DS_CONTROL_PLANE_HIBERNATE_CONNECTION_URL`, `XROAD_DB_DS_CONTROL_PLANE_HIBERNATE_CONNECTION_USERNAME`, `XROAD_DB_DS_CONTROL_PLANE_HIBERNATE_CONNECTION_PASSWORD` (secretKeyRef), `EDC_IAM_DID_WEB_USE_HTTPS`, `XROAD_EDC_IAM_TRUSTED_ISSUER_ISSUER_ID`, `DB_CONFIG_SOURCE_URL`, `DB_CONFIG_SOURCE_USERNAME`, `DB_CONFIG_SOURCE_PASSWORD` (secretKeyRef) | 8181, 8182, 8183, 8184, 9999 | `httpGet :4099/q/health` (HTTP) — 5s/40/initialDelay=5s | `db-ds-control-plane` → `wait-db`; `configuration-client` → `wait-config-client`; `ds-identity-hub` → `wait-identity-hub` | compose lines 44–91 |
| `ds-identity-hub` | `services.ds-identity-hub` in `values.yaml` + `templates/services/all.yaml` | `HOSTNAME`, `EDC_IH_DID_PUBLIC_HOSTNAME`, `XROAD_SECRET_STORE_HOST`, `XROAD_SECRET_STORE_TOKEN` (secretKeyRef), `XROAD_SECRET_STORE_SCHEME`, `XROAD_DB_DS_IDENTITY_HUB_HIBERNATE_CONNECTION_URL`, `XROAD_DB_DS_IDENTITY_HUB_HIBERNATE_CONNECTION_USERNAME`, `XROAD_DB_DS_IDENTITY_HUB_HIBERNATE_CONNECTION_PASSWORD` (secretKeyRef), `EDC_IAM_DID_WEB_USE_HTTPS`, `DEBUG` | 9999, 8182, 10001, 10100 | `httpGet :8181/api/check/health` (HTTP) | `db-ds-identity-hub` → `wait-db` | compose lines 116–148 |
| `ds-issuer-service` | `services.ds-issuer-service` in `values.yaml` + `templates/services/all.yaml` | `HOSTNAME`, `XROAD_SECRET_STORE_HOST`, `XROAD_SECRET_STORE_TOKEN` (secretKeyRef), `XROAD_SECRET_STORE_SCHEME`, `XROAD_COMMON_RPC_CHANNEL_CONFIGURATION_CLIENT_HOST`, `XROAD_DB_DS_ISSUER_SERVICE_HIBERNATE_CONNECTION_URL`, `XROAD_DB_DS_ISSUER_SERVICE_HIBERNATE_CONNECTION_USERNAME`, `XROAD_DB_DS_ISSUER_SERVICE_HIBERNATE_CONNECTION_PASSWORD` (secretKeyRef), `EDC_IAM_DID_WEB_USE_HTTPS`, `DEBUG` | 8182, 10011, 10012, 10013, 10100, 9999 | `httpGet :8383/api/check/health` (HTTP) | `db-ds-issuer-service` → `wait-db` | compose lines 173–213 |

## 6. Single-release-per-namespace constraint (dsp)

DSP application Services render with bare names (`ds-control-plane`,
`ds-identity-hub`, `ds-issuer-service`) — NOT prefixed with
`.Release.Name`. The corresponding database Services
(`db-ds-control-plane`, `db-ds-identity-hub`, `db-ds-issuer-service`)
are provisioned externally and also use bare names. This is deliberate:
the compose source-of-truth hard-codes these hostnames in env vars like
`XROAD_DB_DS_*_HIBERNATE_CONNECTION_URL`, and compose-exact DNS parity
is a merge-block AC.

Consequence: **two Helm releases with DSP services enabled (`replicas >
0`) cannot coexist in the same namespace** — the second `helm install`
fails on resource-name collision. Release-scoped isolation requires
separate namespaces. Only the `.Release.Name`-prefixed resources
(serverconf seed Job) are multi-release-safe by construction.

## 7. ds-control-plane EDC configuration

`ds-control-plane` needs the trusted-issuer participant DID before it
starts. It travels as an ordinary env var on the service:

- **Values:** `services.ds-control-plane.env.XROAD_EDC_IAM_TRUSTED_ISSUER_ISSUER_ID`
  (default `did:web:ds-issuer-service%3A10100:issuer`, the in-cluster
  issuer). Hybrid dev envs override it with the dataspace-wide issuer —
  the ansible overlay sets it from `trusted_issuer_host`.
- **How it reaches EDC:** the runtime's packaged `application.yaml`
  declares `edc.iam.trusted-issuer.issuer.id:
  ${xroad.edc.iam.trusted-issuer.issuer.id}`, and the env var supplies
  the X-Road key. The EDC key itself cannot be set by an env var: EDC
  reads it through `QuarkusConfigBridge`, which snapshots
  `config.getPropertyNames()`, and SmallRye enumerates an env var with
  dots in place of every underscore — so
  `EDC_IAM_TRUSTED_ISSUER_ISSUER_ID` enumerates as
  `edc.iam.trusted.issuer.issuer.id` and never matches the hyphenated
  key. Declaring the EDC key in the packaged yaml makes it enumerable;
  the interpolation then resolves the X-Road key by direct lookup, which
  does handle hyphens.
- **Unset is a startup error**, on purpose: the interpolation has no
  fallback, so an unconfigured deployment fails with SmallRye naming
  both properties rather than registering an empty trusted issuer that
  silently rejects every credential.

Earlier revisions seeded this value as a `configuration_properties` row
in `db-serverconf` (a `post-install,post-upgrade` hook Job plus a
`wait-serverconf-seed` initContainer). That worked only while the
Quarkus DB→SmallRye config source was registered; it was retired with
that bridge.

## 8. Chart version discipline

The `Chart.yaml` `version` key is bumped only when the chart's public
contract changes materially:

- **Current version:** `0.3.0` — bumped from `0.2.0` alongside the
  seed Job + README rewrite.
- `appVersion` tracks the X-Road runtime version and is independent of
  the chart version.
