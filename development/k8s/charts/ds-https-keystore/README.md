# ds-https-keystore Helm chart

> **NOT production-ready / dev+E2E-only.** Every rendered resource carries the
> label `xroad.niis.org/production-ready: "false"`, matching the `Chart.yaml`
> annotation. Explicit stop-gap (self-signed, file-based keystore)
> until ACME-from-OpenBao lands.

## 1. Why this is its own chart

The DS-HTTPS keystore is a **shared prerequisite**: the co-located Central
Server Issuer Service reads it at boot
(`edc.web.https.keystore.path=/etc/xroad/ds-ssl/ds-https.p12` in the CS dev
image's `local-ds-issuer-service.yaml` — without it the Issuer Service
crash-loops and CS initialization fails with a gRPC timeout), and when DSP is
enabled the Security Server ds_tls mode consumes it too. So it must be
provisioned **before** the `central-server` and `security-server` charts, not
bundled with the (last-deployed) `e2e-fixtures` chart. It is installed as an
early ansible step — the `ds_https_keystore` role in `playbooks/site.yml`,
after `openbao` and before `security_server`/`central_server` — the k8s
analogue of compose's `depends_on: ds-https-keystore-init`.

## 2. Recipe

`templates/keystore-job.yaml` is the k8s analogue of compose's
`ds-https-keystore-init` service: it generates a **self-signed** cert (not
CA-anchored) with a SAN set covering the in-cluster CS/SS/DS service names,
exports it as `ds-https.p12`, and imports it into a copy of the JRE's own
`cacerts` — the same two-artifact recipe compose uses (`openssl` PKCS12
export + `keytool -import` into a copied `cacerts`), publishing the result as
a `ds-https-keystore` Secret (keys `ds-https.p12`, `cacerts`) via the
Kubernetes API instead of a shared docker volume. The container
idempotency-checks for an existing Secret before regenerating, mirroring
compose's `[ -f ds-https.p12 ] && [ -f cacerts ] && exit 0` guard.

This chart's fully in-cluster topology has no external CA to anchor to, so it
mirrors compose's self-signed recipe rather than a CA-anchored one.

The `security_server` role reads the truststore under the key this Job
publishes (`cacerts`, a JRE-copy JKS, matching compose), and the
`ds_https_keystore` role replicates the Secret into every Security Server
namespace listed in `security_server_instances`.

## 3. SAN set

`values.yaml`'s `dsHttpsKeystore.extraSanDnsNames` defaults mirror compose's
list, substituted with this tree's k8s service names (`central-server` for the
CS chart's service, the security-server chart's bare `ds-*` service names, and
`ds-*.ss0`/`ds-*.ss1` for cross-namespace addressing). Getting this list wrong
is flagged in the PRD as a known risk area — a stale/mismatched SAN was the
root cause of an earlier hybrid k8s-SS-to-LXD-CS PKIX failure. Override via
`--set dsHttpsKeystore.extraSanDnsNames={...}` if the namespace names change.

## 4. Rendering

```bash
helm lint development/k8s/charts/ds-https-keystore
helm template kt development/k8s/charts/ds-https-keystore
```
