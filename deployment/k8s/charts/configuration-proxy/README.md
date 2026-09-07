# configuration-proxy Helm chart

Deploys the X-Road Configuration Proxy with signer + nginx distribution sidecar.

## Operator-supplied prerequisites

This chart does NOT create the following — provide them before installing.

### Kubernetes Secrets

| Secret name | Keys | Used for |
|---|---|---|
| `xroad-token` | `XROAD_SECRET_STORE_TOKEN` | OpenBao client token |
| `db-confproxy` | `password`, `postgres-password` | confproxy DB credentials (Bitnami / CNPG convention) |
| `xroad-soft-token-pin` | `pin` | Soft-token auto-login PIN for signer and configuration-proxy |

Example for a non-production environment:

```bash
kubectl create secret generic xroad-token \
  --from-literal=XROAD_SECRET_STORE_TOKEN=<openbao-token>

kubectl create secret generic db-confproxy \
  --from-literal=password=<app-password> \
  --from-literal=postgres-password=<superuser-password>

kubectl create secret generic xroad-soft-token-pin \
  --from-literal=pin=<pin>
```

### External services

- PostgreSQL reachable at the host configured in `init.confproxy.host`.
- OpenBao reachable at `services.*.env.XROAD_SECRET_STORE_HOST`, with the
  `xrd-pki/` and `xrd-secret/` mounts seeded (use the `openbao-init` chart).

## HA

Default `replicas: 1` with `strategy: Recreate`. Running multiple replicas
requires RWX-capable PVCs and replica-aware locking, neither provided by this
chart.
