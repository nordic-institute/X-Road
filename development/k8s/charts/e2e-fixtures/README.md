# e2e-fixtures Helm chart

> **NOT production-ready / dev+E2E-only.** This chart reproduces the
> non-Central-Server parts of the E2E suite's compose aux stack
> (`core/src/security-server/e2e-test/src/intTest/resources/compose.aux.yaml`)
> in-cluster: a test CA, mock information systems, a mail sink, and a hurl
> bootstrap Job. Every rendered resource carries the label
> `xroad.niis.org/production-ready: "false"`, matching the `Chart.yaml`
> annotation.

## 1. Overview

Kept as a separate chart from `central-server` (`deployment/central-server/k8s/charts/central-server`)
so the CS chart stays on a clean production track while these fixtures stay
unambiguously dev/E2E-only. Structure mirrors the central-server chart: a
values-driven `services` map for the four simple test-double workloads
(`testca`, `isopenapi`, `issoap`, `isrest`, `mailpit`), using the same
`xroad.labels` / `xroad.service` / `xroad.deployment` helper templates. None
of the fixtures calls the k8s API — config arrives via kubelet-mediated
envFrom/ConfigMap volumes — so they run with no ServiceAccount and
`automountServiceAccountToken: false`. The hurl bootstrap is a dedicated
`batch/v1` Job, since it does not fit the long-running `services` map shape.

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
`security-server/e2e-test/src/intTest/resources/wiremock_mappings` at
`/home/wiremock/mappings`, where WireMock auto-loads every `*.json` stub at
startup) are deployed as a ConfigMap
(`templates/isrest-mappings-configmap.yaml`), using the same "inject real
files at render time, don't fork them into the chart" mechanism as the hurl
scenario files (section 4) — see `values.yaml`'s `services.isrest.mappings`
comment for the `--set-file` recipe.

## 3. DS TLS certificates (provisioned via the admin API)

There is no keystore fixture: `setup.hurl` provisions each server's DS TLS
certificate through the admin API (generate key, DN-only CSR, signing at the
in-cluster test CA with a per-server SAN list, upload) and registers the test
CA's root as the DS TLS trust anchor. The ds-* pods wait at startup on the
empty `tls/ds-https` vault slot until the hurl bootstrap runs; the
scenario's DSP readiness gates assert their convergence. The DN/SAN values
live in `values.yaml` under `hurl.vars`.

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
(the `core/scripts/env-k8s` bring-up) points `--set-file` at the real files.
Left unset, the ConfigMap renders with empty `data` and the Job fails fast on
a missing `/hurl-src/setup.hurl`.

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

This topology is fully in-cluster — there is no external CS/CA to bootstrap
against, so `setup.hurl` is the only scenario this chart runs.

## 5. Out of scope

- Topology wiring (ansible `inventory/e2e`, namespaces, the
  `core/scripts/env-k8s` bring-up pipeline) — owned by `development/k8s`.
- Scenario health — this chart's contract is a correct render and a bootstrap
  Job that runs; the E2E suite owns the assertions.

## 6. Rendering

Local `helm lint` is broken under the installed Helm version even against
the unmodified security-server chart (`invalid Yaml document separator`) —
a known local tooling issue, not specific to this chart. Use `helm template`:

```bash
# Default render (no --set-file): hurl ConfigMap renders with empty data.
helm template test-fixtures . --set imagePullSecrets[0]=ghcr-pull-secret

# Full render, with the real hurl scenario files and isrest stub mappings injected:
helm template test-fixtures . \
  --set imagePullSecrets[0]=ghcr-pull-secret \
  --set-file hurl.files.setupHurl=$XROAD_HOME/core/development/hurl/scenarios/setup.hurl \
  --set-file hurl.files.varsEnv=$XROAD_HOME/core/development/hurl/scenarios/vars.env \
  --set-file services.isrest.mappings.is_rest_1=$XROAD_HOME/core/src/security-server/e2e-test/src/intTest/resources/wiremock_mappings/is_rest_1.json \
  --set-file services.isrest.mappings.is_rest_2=$XROAD_HOME/core/src/security-server/e2e-test/src/intTest/resources/wiremock_mappings/is_rest_2.json \
  --set-file services.isrest.mappings.is_rest_3=$XROAD_HOME/core/src/security-server/e2e-test/src/intTest/resources/wiremock_mappings/is_rest_3.json
```
