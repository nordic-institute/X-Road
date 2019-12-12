# X-Road Security Server Sidecar

X-Road Security Server sidecar is a Docker container that is optimized as a consumer Security Server, and intended to be installed as a sidecar next to a consumer information system. In other words,  it runs in the same context (virtual host or e.g. Kubernetes Pod) as the consumer information system.

X-Road Security Server sidecar docker image contains a custom set of modules instead of xroad-securityserver:

- xroad-proxy
- xroad-addon-metaservices
- xroad-addon-wsdlvalidator
- xroad-autologin

The image is built from sources of version bionic-6.21.0 or later.

## Prerequisites to installation

The X-Road Security Server Sidecar installation requires an existing installation of Docker.

## Requirements to the X-Road Security Server Sidecar

Minimum recommended docker engine configuration to run sidecar security server container in a local development environment:

- CPUs: 4
- Memory: 8 GiB
- Swap: 2 GiB

## Installation

Clone the X-Road repository:

  ```bash
  git clone https://github.com/nordic-institute/X-Road.git
  ```

Set the environment variable XROAD_HOME with the root folder for the cloned X-Road repository:

  ```bash
  export XROAD_HOME=<X-Road source root>
  ```

Run the following scripts:

  ```bash
  ./dockerize_security_server_sidecar.sh
  ./setup_security_server_sidecar.sh <name of the sidecar container> <admin UI port>
  ```

The script dockerize_security_server_sidecar.sh will:

- Create a builder image and container to compile and deploy the source code in an Ubuntu operating system
- Setup initial builder image and container and compile and deploy the sources
- Create a bridge-type network called xroad-network to provide container to container communication in a local development environment

The script setup_security_server_sidecar.sh will:

- Create a new security server sidecar image and start a new security server sidecar container with the given arguments.
