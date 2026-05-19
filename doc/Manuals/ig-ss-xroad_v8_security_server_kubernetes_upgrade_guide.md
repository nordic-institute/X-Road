# Security Server Kubernetes Upgrade Guide <!-- omit in toc -->

**X-ROAD 8**

Version: 1.0
Doc. ID: IG-SS-K8S-UPGRADE

---

## Version history <!-- omit in toc -->

| Date       | Version | Description     | Author             |
|------------|---------|-----------------|--------------------|
| 19.05.2026 | 1.0     | Initial version | Egidijus Milierius |

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 3.0 Unported License. To view a copy of this license, visit http://creativecommons.org/licenses/by-sa/3.0/

## Table of Contents <!-- omit in toc -->

- [License](#license)
- [Overview](#overview)
  - [What this guide covers](#what-this-guide-covers)
  - [What this guide does not cover](#what-this-guide-does-not-cover)
  - [Disclaimer](#disclaimer)
- [Prerequisites](#prerequisites)
- [Before you start](#before-you-start)
- [Helm-based upgrade procedure](#helm-based-upgrade-procedure)
- [Post-upgrade verification](#post-upgrade-verification)
- [Rollback](#rollback)
- [Migration from a native 7.8 Security Server to Kubernetes](#migration-from-a-native-78-security-server-to-kubernetes)
- [Troubleshooting](#troubleshooting)
- [References](#references)

## Overview

This guide describes how to upgrade an existing X-Road 8 Security Server deployment running in Kubernetes from one chart version to the next, using Helm. The X-Road 8 Security Server Helm chart lives under `core/deployment/security-server/k8s/charts/security-server/` and is published alongside the X-Road 8 packages.

### What this guide covers

- Upgrading an existing in-cluster X-Road 8 Security Server release to a newer chart version (chart-to-chart upgrade)
- `values.yaml` evolution between chart releases
- Helm-based rollback

### What this guide does not cover

- **Initial installation in Kubernetes** — see [Security Server Kubernetes Installation Guide](ig-ss-xroad_v8_security_server_kubernetes_installation_guide.md)
- **Native package upgrades** — see [Security Server Upgrade Guide](ig-ss_x-road_v8_security_server_upgrade_guide.md)
- **Central Server in Kubernetes** — out of scope for beta 2
- **Cross-cluster migrations** (moving a release from one Kubernetes cluster to another) — out of scope

A short [Migration from a native 7.8 Security Server to Kubernetes](#migration-from-a-native-78-security-server-to-kubernetes) section sketches the green-field-with-data-import path; the full procedure is **not documented for beta 2**.

### Disclaimer

This document applies to X-Road 8 Beta 2. Pre-release software may behave differently from the final X-Road 8.0 release. The Helm chart shipped with beta 2 is at `version: 0.1.0` / `appVersion: 1.0.0`; chart schema and `values.yaml` keys are subject to change before the final 8.0 release.

## Prerequisites

| Requirement | Notes |
|---|---|
| Kubernetes cluster | A working Kubernetes cluster with an existing X-Road 8 Security Server release installed via the official chart |
| `kubectl` / `helm` | `helm` v3.x and a `kubectl` configured for the target cluster |
| OpenBao | A reachable OpenBao instance (in-cluster or external) holding the Security Server's secrets |
| Persistent storage | The Security Server's PVCs (message log, signer) must survive the upgrade |
| Network reachability | Outbound HTTPS to `https://artifactory.niis.org` (chart and container images) |
| Cluster admin access | Required for namespace operations, role bindings, and PVC inspection |

## Before you start

1. **Read the chart release notes.** Breaking changes between chart versions (renamed values, new required fields, image tag bumps) will be documented in the chart's `CHANGELOG.md` and release notes; review them before invoking `helm upgrade`.
2. **Back up the cluster state for this release.**
   ```bash
   helm get values  <release> -n <namespace> > values-before-upgrade.yaml
   helm get manifest <release> -n <namespace> > manifest-before-upgrade.yaml
   kubectl get all,pvc,secret,configmap -n <namespace> -o yaml > namespace-before-upgrade.yaml
   ```
3. **Back up OpenBao.** The Security Server's TLS material and token PINs live in OpenBao under `xrd-secret/`. Use the OpenBao snapshot mechanism appropriate to your deployment (in-cluster OpenBao Raft snapshot, external OpenBao backup tooling).
4. **Back up the PostgreSQL databases.** `serverconf`, `messagelog`, and `op-monitor` should be dumped before the upgrade. Use `pg_dump` against each.
5. **Notify users and pause inbound traffic.** Helm will roll pods; in-flight requests may be interrupted depending on your replica count and PodDisruptionBudget.

## Helm-based upgrade procedure

Run from a workstation with the target `kubeconfig` selected.

**1. Confirm the current release.**

```bash
helm list -n <namespace>
helm get values <release> -n <namespace>
```

**2. Update the chart repository (or pull the new chart locally).**

```bash
helm repo update
helm search repo niis/security-server --versions | head
```

If you vendor charts locally, replace this step with whatever process produces the new chart directory.

**3. Diff the rendered manifests before applying.** This is the single most useful pre-flight step for Helm upgrades.

```bash
helm diff upgrade <release> niis/security-server \
  --version <new-chart-version> \
  -f values.yaml \
  -n <namespace>
```

(`helm-diff` plugin: `helm plugin install https://github.com/databus23/helm-diff`)

Inspect the diff for unexpected changes to PVCs, Services, Secrets, and image tags. PVC modifications in particular can cause data loss if not understood.

**4. Apply the upgrade.**

```bash
helm upgrade <release> niis/security-server \
  --version <new-chart-version> \
  -f values.yaml \
  -n <namespace> \
  --atomic --timeout 10m
```

`--atomic` rolls the release back to the previous revision if the upgrade fails or times out. `--timeout 10m` allows for OpenBao seal/unseal cycles and Liquibase migrations on first pod boot.

**5. Watch the rollout.**

```bash
kubectl -n <namespace> rollout status statefulset/<release>-security-server
kubectl -n <namespace> get pods -w
```

Database schema migrations run as init containers / jobs on first boot of the new image. Tail their logs:

```bash
kubectl -n <namespace> logs -l app.kubernetes.io/component=db-init --tail=200 -f
```

## Post-upgrade verification

**1. Check pod and service health.**

```bash
kubectl -n <namespace> get pods,svc,pvc
kubectl -n <namespace> describe pod <new-pod>
```

All pods should be `Running` / `Ready 1/1`. PVCs should remain bound.

**2. Confirm the app version.**

```bash
helm list -n <namespace>
```

The `APP VERSION` column should reflect the new `appVersion` from the chart.

**3. Open the admin UI** at the configured Ingress / Service URL and sign in. Verify clients, certificates, and global configuration are intact.

**4. Send a test request** through the Security Server and confirm a successful response.

**5. Inspect logs for warnings:**

```bash
kubectl -n <namespace> logs -l app.kubernetes.io/component=proxy --tail=200
kubectl -n <namespace> logs -l app.kubernetes.io/component=signer --tail=200
```

## Rollback

Helm tracks every release revision and can roll back atomically.

```bash
helm history <release> -n <namespace>
helm rollback <release> <previous-revision> -n <namespace>
```

`helm rollback` reverts the rendered manifests to the previous revision. It does **not** roll back data that the new application version migrated forward:

- **PostgreSQL schema migrations** applied by the new version are not reverted by `helm rollback`. If the previous chart version is incompatible with the migrated schema, restore the pre-upgrade PostgreSQL dumps separately.
- **OpenBao secrets** written by the new version (for example new TLS paths) remain in OpenBao after rollback. They are harmless unless the older version conflicts on the same path.
- **PVC contents** survive rollback. If the new version wrote files the old version cannot read, restore from your PVC backup.

For a clean rollback when in doubt: `helm rollback` to the previous revision, then restore the PostgreSQL dumps and the OpenBao snapshot taken in [Before you start](#before-you-start).

## Migration from a native 7.8 Security Server to Kubernetes

A native X-Road 7.8 Security Server cannot be upgraded in place into a Kubernetes deployment. The migration path is:

1. **Provision a new X-Road 8 Security Server release in Kubernetes** following [Security Server Kubernetes Installation Guide](ig-ss-xroad_v8_security_server_kubernetes_installation_guide.md).
2. **Export configuration and signer state** from the native 7.8 Security Server using the standard backup tooling.
3. **Import data** into the new K8s release — schema and config carried by the `serverconf` database dump, signer state imported through the new release's signer pod, TLS material loaded into OpenBao.
4. **Cut over** by changing DNS / load-balancer entries to point at the K8s release.

The detailed step-by-step is **not documented for beta 2**. Operators planning this migration should open a GitHub issue at <https://github.com/nordic-institute/X-Road/issues> to coordinate with the NIIS team.

## Troubleshooting

**Logs:**

```bash
kubectl -n <namespace> logs <pod> --previous            # crashed pod's previous logs
kubectl -n <namespace> describe pod <pod>               # events, OOMKilled, init-container status
kubectl -n <namespace> get events --sort-by=.lastTimestamp
helm history <release> -n <namespace>                   # release-level events
```

**Common failure patterns:**

| Symptom | Likely cause | Resolution |
|---|---|---|
| `helm upgrade` times out, pods stuck `Init:CrashLoopBackOff` | DB init container cannot reach PostgreSQL, or Liquibase migration failed | `kubectl logs <pod> -c <init-container>`; verify PostgreSQL credentials in Secrets and that the DB is reachable |
| Pods running but admin UI returns 5xx | OpenBao unreachable or sealed | `kubectl exec` into the openbao pod and confirm seal status; verify the Security Server's vault token is still valid |
| `helm upgrade` reports `cannot patch ... forbidden` | RBAC: helm service account lacks permission on a resource the new chart introduces | Update the role binding for the helm install service account before retrying |
| PVC `Pending` after upgrade | Storage class changed between chart versions, or the new chart requests a size the storage class does not allow to grow | Restore the previous chart values overriding `storageClassName` / sizes, or re-provision with the new storage class |
| Pods up but message-log pod cannot start | Message-log PVC contains files written by a version not compatible with the new image | Restore PVC from backup, or follow the chart release notes for any required manual migration |

**Getting help:**

X-Road is an open source project. Open a GitHub issue at <https://github.com/nordic-institute/X-Road/issues> with the `helm history`, the relevant pod logs, and the diff from `helm diff upgrade` if you captured one. <!-- TBD beta 2 discussion URL -->

## References

1. [Security Server Kubernetes Installation Guide](ig-ss-xroad_v8_security_server_kubernetes_installation_guide.md)
2. [Security Server Upgrade Guide (native packages)](ig-ss_x-road_v8_security_server_upgrade_guide.md)
3. [X-Road Terms and Abbreviations](../terms_x-road_docs.md)
4. Chart source: `core/deployment/security-server/k8s/charts/security-server/`
