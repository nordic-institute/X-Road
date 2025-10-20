Can you improve on following markdown:

# Containerized Security Server in Kubernetes Cluster

## Prerequisites

- Docker (with Kubernetes enabled) installed on the host
- OpenTofu installed on the host
- kubectl Installed on the host

## Deployment

### Development Environment (dev)

In the `dev` environment, Helm charts and Docker images are pulled from a local registry.

1. Start local Docker registry

```bash
docker run -d -p 5555:5000 --name registry registry:2
```

2. Build and push required images into the registry:

```bash
./build-images.sh
```

3. Initialize the cluster:

```bash
./init-dev-env.sh
```

**Note:** Ensure the local registry is running. The cluster relies on it to pull images during initialization.

### Test Environment (test)

In the `test` environment, Helm charts and Docker images are pulled directly from NIIS’ Artifactory.

Initialize the cluster:

```bash
./init-test-env.sh
```

### Provisioning the security server

```bash
./init-ss2.sh
```

**Note:** This script provisions the security server as **ss2** within the [native-lxd-stack](../native-lxd-stack/README.md) environment.
Ensure that the LXD environment is up and running before executing this script.
  