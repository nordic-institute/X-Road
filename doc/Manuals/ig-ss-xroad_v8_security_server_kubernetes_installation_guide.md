# Security Server Installation Guide for Kubernetes

This guide describes how to install X-Road 8 Security Server on a Kubernetes cluster using Helm charts from the command line.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Architecture Overview](#architecture-overview)
3. [Deployment Steps](#deployment-steps)

## Prerequisites

- Kubernetes cluster up & running
- `kubectl` to communicate with your cluster
- `helm`

## Architecture Overview

The X-Road 8 Security Server deployment consists of:

1. **OpenBao Secret Store**
    - PostgreSQL database
    - OpenBao server
    - OpenBao initializer

2. **Security Server Components**
    - Configuration Client
    - Signer
    - Proxy
    - Proxy UI API
    - Monitor
    - Operational Monitor
    - Backup Manager

3. **Databases**
    - Serverconf
    - Messagelog
    - Operational Monitor

## Deployment Steps

>**Note:** Before beginning the deployment, make sure the Kubernetes cluster is configured for dynamic volume provisioning and management for your pods. Additionally, a default storage class must be configured.

### 1. Create Namespace

```bash
kubectl create namespace security-server
```

### 2. Add Helm repositories
```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add openbao https://openbao.github.io/openbao-helm
helm repo update
```

>**Note:** The `bitnami` repository is utilized for deploying PostgreSQL databases, whereas the `openbao` repository is used for deploying OpenBao.

### 3. Deploy OpenBao with PostgreSQL storage

```bash
# Install PostgreSQL for OpenBao's storage
helm install openbao-db bitnami/postgresql \
  --version 18.0.12 \
  --namespace security-server \
  --set fullnameOverride=db-openbao \
  --set image.repository=bitnamilegacy/postgresql \
  --set image.tag=16.6.0 \
  --set auth.database=openbao \
  --set auth.username=openbao \
  --set auth.password=<openbao_user_password> \
  --set primary.resources.requests.memory=64Mi \
  --set primary.resources.limits.memory=256Mi
```

```bash
# Generate TLS private key 
openssl ecparam -genkey -name secp384r1 -out openbao.key
```

```bash
# Generate self-signed TLS certificate
openssl req -new -x509 -sha256 -key openbao.key -out openbao.crt -days 1825 -subj "/CN=openbao" -addext "subjectAltName = DNS:localhost,DNS:openbao,DNS:openbao.ss.svc.cluster.local" -addext "keyUsage = digitalSignature, keyEncipherment" -addext "extendedKeyUsage = serverAuth"
```

```bash
# Create Kubernetes secret containing OpenBao's TLS credentials
kubectl create secret tls openbao-server-tls \
  --namespace=security-server \
  --cert=openbao.crt \
  --key=openbao.key
```

```bash
# Install OpenBao
helm install openbao openbao/openbao \
  --version 0.19.0 \
  --namespace security-server \
  --set global.tlsDisable=false \
  --set server.ha.enabled=true \
  --set server.ha.replicas=1 \
  --set-string 'server.ha.config=ui = true
listener "tcp" {
  address = "[::]:8200"
  cluster_address = "[::]:8201"
  tls_cert_file = "/openbao/userconfig/server-tls/tls.crt"
  tls_key_file = "/openbao/userconfig/server-tls/tls.key"
}
storage "postgresql" {
  ha_enabled = "true"
}
service_registration "kubernetes" {}' \
  --set 'server.extraSecretEnvironmentVars[0].envName=BAO_PG_PASSWORD' \
  --set 'server.extraSecretEnvironmentVars[0].secretName=db-openbao' \
  --set 'server.extraSecretEnvironmentVars[0].secretKey=password' \
  --set 'server.extraEnvironmentVars.BAO_PG_CONNECTION_URL=postgres://openbao:$(BAO_PG_PASSWORD)@db-openbao:5432/openbao' \
  --set 'server.volumes[0].name=userconfig-openbao-server-tls' \
  --set 'server.volumes[0].secret.secretName=openbao-server-tls' \
  --set 'server.volumeMounts[0].mountPath=/openbao/userconfig/server-tls' \
  --set 'server.volumeMounts[0].name=userconfig-openbao-server-tls' \
  --set 'server.volumeMounts[0].readOnly=true'
```

### 5. Initialize OpenBao

```bash
# Initialize OpenBao
helm install openbao-init oci://artifactory.niis.org/xroad8-snapshot-helm/openbao-init \
  --version 8.0.0-beta2 \
  --namespace security-server
```

### 5. Deploy Security Server Databases

```bash
# Serverconf database
helm install serverconf-db bitnami/postgresql \
  --version 18.0.12 \
  --namespace security-server \
  --set fullnameOverride=db-serverconf \
  --set image.repository=bitnamilegacy/postgresql \
  --set image.tag=16.6.0 \
  --set auth.database=serverconf \
  --set auth.username=serverconf \
  --set auth.password=<serverconf_user_password> \
  --set auth.enablePostgresUser=true \
  --set auth.postgresPassword=<postgres_password> \
  --set primary.resources.requests.memory=64Mi \
  --set primary.resources.limits.memory=256Mi

# Messagelog database
helm install messagelog-db bitnami/postgresql \
  --version 18.0.12 \
  --namespace security-server \
  --wait
  --set fullnameOverride=db-messagelog \
  --set image.repository=bitnamilegacy/postgresql \
  --set image.tag=16.6.0 \
  --set auth.database=messagelog \
  --set auth.username=messagelog \
  --set auth.password=<messagelog_user_password> \
  --set auth.enablePostgresUser=true \
  --set auth.postgresPassword=<postgres_password> \
  --set primary.resources.requests.memory=64Mi \
  --set primary.resources.limits.memory=256Mi

# Operational Monitor database
helm install opmonitor-db bitnami/postgresql \
  --version 18.0.12 \
  --namespace security-server \
  --set fullnameOverride=db-opmonitor \
  --set image.repository=bitnamilegacy/postgresql \
  --set image.tag=16.6.0 \
  --set auth.database=op-monitor \
  --set auth.username=opmonitor \
  --set auth.password=<opmonitor_user_password> \
  --set auth.enablePostgresUser=true \
  --set auth.postgresPassword=<postgres_password> \
  --set primary.resources.requests.memory=64Mi \
  --set primary.resources.limits.memory=256Mi
```

### 6. Deploy Security Server

```bash
helm install security-server oci://artifactory.niis.org/xroad8-snapshot-helm/security-server \
  --version 8.0.0-beta2 \
  --namespace security-server \
  --wait \
  --set init.serverconf.dbUsername=serverconf \
  --set init.serverconf.proxyUiSuperuser=<proxy_ui_superuser> \
  --set init.serverconf.proxyUiSuperuserPassword=<proxy_ui_superuser_password> \
  --set init.messagelog.dbUsername=messagelog \
  --set init.opmonitor.dbUsername=opmonitor \
  --set services.configuration-client.env.QUARKUS_VAULT_TLS_CA_CERT=/etc/xroad/ssl/openbao.crt \
  --set 'services.configuration-client.extraVolumes[0].name=configuration-client-openbao-tls-certificate' \
  --set 'services.configuration-client.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.configuration-client.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.configuration-client.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.configuration-client.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.configuration-client.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.configuration-client.extraVolumeMounts[0].name=configuration-client-openbao-tls-certificate' \
  --set services.signer.env.QUARKUS_VAULT_TLS_CA_CERT=/etc/xroad/ssl/openbao.crt \
  --set 'services.signer.extraVolumes[0].name=signer-openbao-tls-certificate' \
  --set 'services.signer.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.signer.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.signer.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.signer.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.signer.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.signer.extraVolumeMounts[0].name=signer-openbao-tls-certificate' \
  --set services.proxy.env.XROAD_PROXY_ADDON_OP_MONITOR_ENABLED=\"true\" \
  --set services.proxy.env.QUARKUS_VAULT_TLS_CA_CERT=/etc/xroad/ssl/openbao.crt \
  --set 'services.proxy.extraVolumes[0].name=proxy-openbao-tls-certificate' \
  --set 'services.proxy.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.proxy.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.proxy.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.proxy.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.proxy.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.proxy.extraVolumeMounts[0].name=proxy-openbao-tls-certificate' \
  --set services.proxy-ui-api.env.SPRING_CLOUD_VAULT_SSL_TRUST_STORE=file:/etc/xroad/ssl/openbao.crt \
  --set services.proxy-ui-api.env.SPRING_CLOUD_VAULT_SSL_TRUST_STORE_TYPE=PEM \
  --set 'services.proxy-ui-api.extraVolumes[0].name=proxy-ui-openbao-tls-certificate' \
  --set 'services.proxy-ui-api.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.proxy-ui-api.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.proxy-ui-api.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.proxy-ui-api.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.proxy-ui-api.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.proxy-ui-api.extraVolumeMounts[0].name=proxy-ui-openbao-tls-certificate' \
  --set services.monitor.env.QUARKUS_VAULT_TLS_CA_CERT=/etc/xroad/ssl/openbao.crt \
  --set 'services.monitor.extraVolumes[0].name=monitor-openbao-tls-certificate' \
  --set 'services.monitor.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.monitor.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.monitor.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.monitor.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.monitor.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.monitor.extraVolumeMounts[0].name=monitor-openbao-tls-certificate' \
  --set services.op-monitor.enabled=true \
  --set services.op-monitor.env.QUARKUS_VAULT_TLS_CA_CERT=/etc/xroad/ssl/openbao.crt \
  --set 'services.op-monitor.extraVolumes[0].name=op-monitor-openbao-tls-certificate' \
  --set 'services.op-monitor.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.op-monitor.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.op-monitor.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.op-monitor.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.op-monitor.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.op-monitor.extraVolumeMounts[0].name=op-monitor-openbao-tls-certificate' \
  --set services.backup-manager.env.SERVERCONF_INITIALIZED_WITH_PROXY_UI_SUPERUSER=\"true\" \
  --set services.backup-manager.env.PROXY_UI_SUPERUSER=<proxy_ui_superuser> \
  --set services.backup-manager.env.QUARKUS_VAULT_TLS_CA_CERT=/etc/xroad/ssl/openbao.crt \
  --set 'services.backup-manager.extraVolumes[0].name=backup-manager-openbao-tls-certificate' \
  --set 'services.backup-manager.extraVolumes[0].secret.secretName=openbao-server-tls' \
  --set 'services.backup-manager.extraVolumes[0].secret.items[0].key=tls.crt' \
  --set 'services.backup-manager.extraVolumes[0].secret.items[0].path=openbao.crt' \
  --set 'services.backup-manager.extraVolumeMounts[0].mountPath=/etc/xroad/ssl/openbao.crt' \
  --set 'services.backup-manager.extraVolumeMounts[0].subPath=openbao.crt' \
  --set 'services.backup-manager.extraVolumeMounts[0].name=backup-manager-openbao-tls-certificate'
```

**Note:** The installation of the `security-server` chart may take up to several minutes to complete.

>**Note:**
>* `proxyUiSuperuser` s the default administrative user (with full privileges) that can be used to log in to the Proxy UI admin web application.
>* The value of `init.serverconf.proxyUiSuperuserPassword` must be provided in Argon2 hash format.
>* Proxy UI relies on [Spring Security's default Argon2 parameters](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/argon2/Argon2PasswordEncoder.html#defaultsForSpringSecurity_v5_8()) for password hashing, so the hash must conform to these settings.
>* You can generate an Argon2 hash using https://argon2.online. For example, a valid Argon2 hash of the password `secret` would be: `$argon2id$v=19$m=16384,t=2,p=1$YXF3YXN6eHh6c2F3cQ$+llp8EbxlqZaF2uO/BLoFLwfqxe1Yn6BvC/DOegq6A0`.

### Appendix

To gain access to the Security Server, you’ll need to either use port forwarding or deploy a gateway, depending on your setup.<br/>
For instance, you can forward the proxy-ui-api and proxy services to your local machine with the following command:
```bash
kubectl port-forward service/proxy-ui-api 4000:4000 -n security-server & \
kubectl port-forward service/proxy 5500:5500 5577:5577 8080:8080 8443:8443 -n security-server & \
wait
```

This repository also includes Terraform scripts for seamless local deployment of the security server on Kubernetes. Detailed instructions are available [here](../../development/k8s/README.md).
