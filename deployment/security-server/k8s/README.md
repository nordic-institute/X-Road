## Containerized Security Server in Kubernetes Cluster

### Prerequisites

- Docker (with Kubernetes enabled) installed on the host
- OpenTofu installed on the host
- kubectl Installed on the host

### Deployment

1. Start local Docker registry

```bash
docker run -d -p 5555:5000 --name registry registry:2
```

2. Push required images into the local registry:

```bash
./build-images.sh
```

3. Initialize the cluster:

```bash
./init-env.sh
```

**Note:** Ensure the local registry is up & running, as the cluster will pull images from it during initialization.


4. Provision the security server:

```bash
./init-ss2.sh
```

**Note:** This script provisions the security server as **ss2** within the [native-lxd-stack](../../../development/native-lxd-stack/README.md) environment.
Ensure that the LXD environment is up and running before executing this script.
  