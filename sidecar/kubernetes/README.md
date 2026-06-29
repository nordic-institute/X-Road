# X-Road Security Server Sidecar - Kubernetes Deployment

This directory contains Kubernetes manifests for deploying the X-Road Security Server Sidecar.

## Deployment Variants

| Manifest | Database | Secrets | Use Case |
|----------|----------|---------|----------|
| `security-server-sidecar.yaml` | Local (embedded) | Inline values | Development / testing |
| `security-server-sidecar-slim.yaml` | Local (embedded) | Inline values | Development / testing (slim image) |
| `security-server-sidecar-external-db.yaml` | External (RDS, Cloud SQL, etc.) | Kubernetes Secrets | Production |

## Production Deployment with External Database

For production environments, use an external managed database (e.g., AWS RDS, Azure Database
for PostgreSQL, Google Cloud SQL) for better reliability, backups, and scaling.

### Prerequisites

- Kubernetes 1.24+
- External PostgreSQL 14+ with network access from the cluster
- [External Secrets Operator](https://external-secrets.io) (recommended) or manually created Kubernetes Secrets

### Quick Start

1. **Set up secrets** — choose one method:

   **Option A: External Secrets Operator (recommended for cloud)**
   ```bash
   # Edit external-secrets-example.yaml with your secret paths
   kubectl apply -f external-secrets-example.yaml
   ```

   **Option B: Manual Kubernetes Secrets**
   ```bash
   kubectl create secret generic sidecar-db-credentials \
     --from-literal=username=postgres \
     --from-literal=password='your-db-password'

   kubectl create secret generic sidecar-admin-credentials \
     --from-literal=admin-user=xrd \
     --from-literal=admin-password='your-admin-password' \
     --from-literal=token-pin='your-token-pin'
   ```

2. **Edit the deployment manifest:**
   ```bash
   # Update XROAD_DB_HOST with your database endpoint
   vi security-server-sidecar-external-db.yaml
   ```

3. **Deploy:**
   ```bash
   kubectl apply -f security-server-sidecar-external-db.yaml
   kubectl rollout status deployment/security-server-sidecar --timeout=300s
   ```

4. **Access admin UI:**
   ```bash
   kubectl port-forward svc/security-server-sidecar-admin 4000:4000
   # Open https://localhost:4000
   ```

### Database Setup

The Sidecar entrypoint automatically creates the required databases and schemas on the
external PostgreSQL instance. The user specified in `XROAD_DB_PWD` must have privileges
to create databases and roles. Typically this is the master/admin user of the managed database.

The following databases are created automatically:
- `serverconf` (or `{XROAD_DATABASE_NAME}_serverconf`)
- `messagelog` (if messagelog addon is installed)
- `op-monitor` (if opmonitor addon is installed)

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `XROAD_TOKEN_PIN` | Yes | PIN code for the software token |
| `XROAD_ADMIN_USER` | Yes | Admin UI username |
| `XROAD_ADMIN_PASSWORD` | Yes | Admin UI password |
| `XROAD_ADMIN_PWD_HASH` | No | Alternative: pre-hashed password (SHA-512). Overrides `XROAD_ADMIN_PASSWORD` |
| `XROAD_DB_HOST` | Yes | External database hostname |
| `XROAD_DB_PORT` | No | Database port (default: 5432) |
| `XROAD_DB_PWD` | Yes | Database superuser password |
| `XROAD_DATABASE_NAME` | No | Database name prefix (default: serverconf) |
| `XROAD_TOKEN_<id>_PIN` | No | PIN for additional hardware tokens (v7.8+) |
| `XROAD_PROXY_UI_API_ACME_CHALLENGE_PORT` | No | Custom ACME HTTP challenge port (v7.8+) |
| `XROAD_LOG_LEVEL` | No | Log level: ALL, DEBUG, INFO, WARN, ERROR, OFF |

### Probes Configuration

The external database manifest includes readiness and liveness probes tuned for production:

```yaml
readinessProbe:
  httpGet:
    path: /
    port: 4000
    scheme: HTTPS
  initialDelaySeconds: 120    # Sidecar needs time for DB migration
  periodSeconds: 10
  failureThreshold: 20
livenessProbe:
  httpGet:
    path: /
    port: 4000
    scheme: HTTPS
  initialDelaySeconds: 200    # Allow full startup before killing
  periodSeconds: 30
  failureThreshold: 10
```

**Important:** The Sidecar requires significant startup time (90-180 seconds) for database
migrations and service initialization. Setting `initialDelaySeconds` too low will cause
restart loops.

### Resource Recommendations

| Workload | CPU Request | CPU Limit | Memory Request | Memory Limit |
|----------|-------------|-----------|----------------|--------------|
| Light (< 10 req/s) | 250m | 1000m | 768Mi | 2Gi |
| Medium (10-100 req/s) | 500m | 2000m | 1Gi | 3Gi |
| Heavy (> 100 req/s) | 1000m | 4000m | 2Gi | 4Gi |

### Security Considerations

- **Never expose port 4000 (admin UI) to the internet.** Use `ClusterIP` service or
  internal load balancer for admin access.
- **Never store credentials in plain text** in manifest files. Use Kubernetes Secrets
  or External Secrets Operator.
- **Enable SSL** for database connections. Set `sslmode=require` in your database
  configuration if supported.
- **Drop NET_RAW capability** as shown in the manifests to reduce container attack surface.

### Upgrading

When upgrading the Sidecar image version, the entrypoint automatically runs database
migrations. Ensure the PVC (`sidecar-config-claim`) is retained between deployments
to preserve configuration and certificates.

```bash
# Update image version
kubectl set image deployment/security-server-sidecar \
  security-server-sidecar=niis/xroad-security-server-sidecar:7.8.0

# Monitor rollout
kubectl rollout status deployment/security-server-sidecar --timeout=300s
```

## Other Files

- `security-server-sidecar-local.yaml` — Local database variant (for development)
- `grant-user-access-to-cluster.sh` — Helper script for cluster RBAC
- `testRequest.xml` — Sample X-Road SOAP request for testing
