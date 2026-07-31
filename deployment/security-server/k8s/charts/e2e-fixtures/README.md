# e2e-fixtures Helm chart

> **NOT production-ready / dev+E2E-only.** This chart reproduces the
> non-Central-Server parts of the E2E suite's compose aux stack
> (`core/src/security-server/e2e-test/src/intTest/resources/compose.aux.yaml`)
> in-cluster: a test CA, mock information systems, a mail sink, a DS-HTTPS
> keystore init Job, and a hurl bootstrap Job. Every rendered resource
> carries the label `xroad.niis.org/production-ready: "false"`, matching the
> `Chart.yaml` annotation.

## 1. Overview

Kept as a separate chart from `central-server` (`k8s/charts/central-server`)
so the CS chart stays on a clean production track while these fixtures stay
unambiguously dev/E2E-only. Structure mirrors the central-server chart: a
values-driven `services` map for the four simple test-double workloads
(`testca`, `isopenapi`, `issoap`, `isrest`, `mailpit`), using the same
`xroad.labels` / `xroad.service` / `xroad.deployment` / `xroad.serviceaccount`
helper templates. The DS-HTTPS keystore init and hurl bootstrap are each a
dedicated `batch/v1` Job, since neither fits the long-running `services` map
shape.

## 2. Fixture images

| Fixture | Image | Source |
|---|---|---|
| `testca` | `testca-dev` | Private dev-infra image, `core/scripts/images/build-dev-infra.sh` → same GHCR path/pull-secret mechanism as `central-server-dev` (`global.image.registry` + `imagePullSecrets`). |
| `isopenapi` | `ghcr.io/nordic-institute/xrddev-example-restapi:latest` | Public — full image ref, bypasses `global.image.registry`. |
| `issoap` | `niis/example-adapter:latest` | Public (Docker Hub) — full image ref; command override runs the WAR directly, matching compose's entrypoint. |
| `isrest` | `wiremock/wiremock:latest` | Public (Docker Hub) — full image ref. |
| `mailpit` | `axllent/mailpit:latest` | Public (Docker Hub) — full image ref. |

Ports and readiness paths were verified against each service's entry in
`compose.aux.yaml` (testca: 8887 ACME / 8888 cert API / 8899 TSA, readiness
`/testca/certs`; isopenapi: 8080, `/v3/api-docs`; issoap: 8080,
`/example-adapter/Endpoint?wsdl`; isrest: 8080, `/__admin/health`). mailpit
has no compose healthcheck to mirror — its readiness probe (`/` on 8025) is
chart-added, not mirrored.

`isrest`'s WireMock stub mappings (compose bind-mounts
`security-server/e2e-test/src/intTest/resources/wiremock_mappings`) are not
wired into this chart yet — deploying them as a ConfigMap is deferred to the
topology-wiring slice, using the same "inject real files at render time"
mechanism as the hurl scenario files (section 4), just not connected yet.

## 3. DS-HTTPS keystore init Job

`templates/ds-https-keystore-job.yaml` is the k8s analogue of compose's
`ds-https-keystore-init` service: it generates a **self-signed** cert (not
CA-anchored) with a SAN set covering the in-cluster CS/SS/DS service names,
exports it as `ds-https.p12`, and imports it into a copy of the JRE's own
`cacerts` — the same two-artifact recipe compose uses (`openssl` PKCS12
export + `keytool -import` into a copied `cacerts`), just publishing the
result as a `ds-https-keystore` Secret (keys `ds-https.p12`, `cacerts`) via
the Kubernetes API instead of a shared docker volume. The container
idempotency-checks for an existing Secret before regenerating, mirroring
compose's `[ -f ds-https.p12 ] && [ -f cacerts ] && exit 0` guard.

This is deliberately **not** the same recipe as the existing
`development/k8s/roles/security_server/tasks/ds_https_keystore.yml` ansible
role: that role CA-signs the cert via an *external* LXD test CA for the
`k8-ss2` hybrid flavor (and publishes a PKCS12 **truststore**,
`cacerts.p12`, built with `-jdktrust`, rather than a copied JRE `cacerts`).
This chart's fully in-cluster E2E topology has no external CA to anchor to,
so it mirrors compose's self-signed recipe instead, per this slice's
grounding. **Flag for the topology-wiring slice:** the security-server
chart's existing DSP env wiring
(`development/k8s/roles/security_server/templates/security-server-values.yaml.j2`)
expects the Secret's truststore key to be named `cacerts.p12` (PKCS12); this
Job currently publishes `cacerts` (JRE-copy JKS), matching compose's file
naming exactly as instructed. Reconciling the two — either renaming this
Job's output key or adjusting the SS chart's mount/env — is left to whichever
slice actually wires DSP for the two-SS in-cluster topology (PRD slice 06+),
since getting the SAN set and recipe right here does not by itself require
that mount to be live yet (proving did:web/DSP validation is slice 09).

**SAN set** (`values.yaml`'s `dsHttpsKeystore.extraSanDnsNames`): defaults
mirror compose's list (`cs`, `ds-issuer-service`, `ds-identity-hub`,
`ds-control-plane`, `ss0-`/`ss1-`-prefixed DS hostnames, `xrd-ss0`/`xrd-ss1`,
`localhost`), substituted with this tree's k8s service names
(`central-server` for the CS chart's service, the security-server chart's
bare `ds-*` service names, and `ds-*.ss0`/`ds-*.ss1` placeholders for
cross-namespace addressing). **The `ss0`/`ss1` namespace segments are
placeholders** — the PRD's two-SS-as-two-releases-in-separate-namespaces
layout (slice 06) hasn't chosen concrete namespace names yet. Override via
`--set dsHttpsKeystore.extraSanDnsNames={...}` once it has; getting this list
wrong is flagged in the PRD as a known risk area (a stale/mismatched SAN was
the root cause of an earlier hybrid k8s-SS-to-LXD-CS PKIX failure).

## 4. hurl bootstrap Job

`templates/hurl-bootstrap-job.yaml` runs the **existing**
`development/hurl/scenarios/setup.hurl` unmodified — hurl is repointed, not
forked. Helm's `.Files.Get` cannot read outside the chart directory, so
instead of embedding a copy of the scenario, the chart's ConfigMap template
has an empty data slot filled in via `--set-file` at render/install time:

```bash
helm template . \
  --set-file hurl.files.setupHurl=$XROAD_HOME/core/development/hurl/scenarios/setup.hurl \
  --set-file hurl.files.varsEnv=$XROAD_HOME/core/development/hurl/scenarios/vars.env
```

No chart-tracked copy of either file ever exists; whoever installs the chart
(the E2E bring-up script, in a later slice) points `--set-file` at the real
files. Left unset, the ConfigMap renders with empty `data` and the Job would
fail fast on a missing `/hurl-src/setup.hurl` — acceptable here since this
slice's contract is "the chart renders correctly," not "the scenario passes"
(that's slice 06+).

Host variables (`values.yaml`'s `hurl.vars`) override `vars.env`'s
compose-mapped-port defaults (`ss0_proxy=localhost` etc.) with in-cluster
Service DNS names, the same `--variable key=value` mechanism compose's own
`hurl` service uses. `ss0`/`ss1` namespace placeholders as in section 3.

The `fetch-ca-certs` initContainer downloads the CA/OCSP/TSA certificates
setup.hurl needs (`ca/ca.pem`, `ca/ocsp.pem`, `ca/tsa.pem` under
`--file-root`) directly from testca's `/testca/certs/*.cert.pem` HTTP
endpoint, rather than relying on compose's shared `ca-volume` (Jobs and the
`testca` Deployment don't share a filesystem in-cluster). This is the same
mechanism the existing `development/ansible/roles/xroad-hurl/templates/
run-hurl.sh.j2` role's `download_cert()` already uses for the non-compose
dev stack — not a new pattern invented for k8s.

The `k8-ss2` hurl scenario (`development/hurl/scenarios/k8-ss2/`) is the
*hybrid* (k8s-SS-to-external-LXD-CS) model and is intentionally not used
here — this topology is fully in-cluster.

## 5. Out of scope (this slice)

- Wiring this chart into the ansible `inventory/e2e` topology, namespace
  layout, or the `core/scripts/env-k8s` bring-up pipeline.
- The second Security Server, enabling DSP, or reconciling the DS-HTTPS
  Secret's truststore key naming with the security-server chart's existing
  `ds_tls` mount (section 3).
- Asserting any hurl scenario green, or validating did:web/DSP against the
  generated keystore (slice 09).
- WireMock stub mapping content for `isrest` (section 2).
- Replacing the DS-HTTPS stop-gap with ACME-from-OpenBao (XRDADR-42).

## 6. Rendering

Local `helm lint` is broken under the installed Helm version even against
the unmodified security-server chart (`invalid Yaml document separator`) —
a known local tooling issue, not specific to this chart. Use `helm template`:

```bash
# Default render (no --set-file): hurl ConfigMap renders with empty data.
helm template test-fixtures . --set imagePullSecrets[0]=ghcr-pull-secret

# Full render, with the real hurl scenario files injected:
helm template test-fixtures . \
  --set imagePullSecrets[0]=ghcr-pull-secret \
  --set-file hurl.files.setupHurl=$XROAD_HOME/core/development/hurl/scenarios/setup.hurl \
  --set-file hurl.files.varsEnv=$XROAD_HOME/core/development/hurl/scenarios/vars.env
```
