# X-Road Security Server Sidecar

X-Road Security Server sidecar is a Docker container that is optimized as a consumer Security Server, and intended to be installed as a sidecar next to a consumer information system. In other words, it runs in the same context (virtual host or e.g. Kubernetes Pod) as the consumer information system.

X-Road Security Server sidecar docker image contains a custom set of modules instead of xroad-securityserver:

- xroad-proxy
- xroad-addon-metaservices
- xroad-addon-wsdlvalidator
- xroad-autologin

The X-Road Security Server sidecar software is built from pre-built packages downloaded from the official X-Road repository at [https://artifactory.niis.org/xroad-release-deb](https://artifactory.niis.org/xroad-release-deb).

## Prerequisites to installation

The X-Road Security Server Sidecar installation requires an existing installation of Docker.

## Requirements to the X-Road Security Server Sidecar

Minimum recommended docker engine configuration to run sidecar security server container in a local development environment:

- CPUs: 4
- Memory: 8 GiB
- Swap: 2 GiB

## Installation

Run the following script:

  ```bash
  ./setup_security_server_sidecar.sh <name of the sidecar container> <admin UI port> <software token PIN code> <admin username> <admin password>
  ```

The script setup_security_server_sidecar.sh will:

- Create a bridge-type network called xroad-network to provide container-to-container communication in a local development environment.
- Create a new security server sidecar image and start a new security server sidecar container with the given arguments.
- Configure xroad-autologin software token PIN code.
- Configure admin username and password.
