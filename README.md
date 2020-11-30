# X-Road Security Server Sidecar

X-Road Security Server sidecar is a Docker container that is optimized as a consumer Security Server, and intended to be installed as a sidecar next to a consumer information system. In other words, it runs in the same context (virtual host or e.g. Kubernetes Pod) as the consumer information system.

X-Road Security Server sidecar docker image contains a custom set of modules instead of xroad-securityserver:

- xroad-proxy
- xroad-addon-metaservices
- xroad-addon-wsdlvalidator
- xroad-autologin

The X-Road Security Server sidecar software is built from pre-built packages downloaded from the official X-Road repository at [https://artifactory.niis.org/xroad-release-deb](https://artifactory.niis.org/xroad-release-deb).

Please check

## 1 Security Server Sidecar Installation

## 1.1 Supported Platforms

The Security Server sidecar can be installed both on physical and virtualized hardware. The installation script setup_security_server_sidecar.sh runs on Unix-based operating systems (of the latter, Mac OS and Ubuntu have been tested).

## 1.2 Installation
See the [User guide](doc/security_server_sidecar_user_guide.md) for information about how to install and configure sidecar.

## 1.3 Estimated time for new Security Server Sidecar Installation

The installation process from scratch has been tested in the following environment:
- Operating System: Ubuntu 18.04.4 LTS.
- Memory: 16 GB .
- Disk: 512GB SSD.
- CPU: Intel(R) Core(TM) i7-8565U CPU @ 1.80GHz.
- Cores: 4.
- Internet connection: 120 mbps download, 106 mbps upload.

The timing results were:
- Download and install pre-requisites (Git): 1 minute.
- Download and install Docker: 30 seconds.
- Clone Security Server Sidecar repository: 10 seconds.
- Run image: 30 seconds.
- Wait until the UI becomes accessible: 50 seconds
- Import anchor and initial configuration: 1 minute
- Pick timestamp service: 30 seconds
- Create CSRs and send them for signing, import certificates and register the auth certificate: about 3 minutes for an advanced user (This measurement does not take into account the time it takes to sign the certificates because it is out of our hands)
- Configure member in central server and register the subsystem: about 2 minutes for an advanced user
- Add client and wait until it's green: 1 minute and 30 seconds

Based on the study, the installation and configuration of the Sidecar from scratch can take approximately 11 minutes for an advanced user.


## 2 Key Points and Limitations for X-Road Security Server Sidecar Deployment

- The current security server sidecar implementation is a Proof of Concept and it is meant for testing and development purposes.
- The current security server sidecar implementation does not support message logging, operational monitoring nor environmental monitoring functionality, which is recommended for a service provider's security server role. This functionality will be included in future releases.
- The security server sidecar creates and manages its own internal TLS keys and certificates and does TLS termination by itself. This configuration might not be fully compatible with the application load balancer configuration in a cloud environment.
- The xroad services are run inside the container using supervisord as root, although the processes it starts are not. To avoid potential security issues, it is possible to set up Docker so that it uses Linux user namespaces, in which case root inside the container is not root (user id 0) on the host. For more information, see <https://docs.docker.com/engine/security/userns-remap/>.

