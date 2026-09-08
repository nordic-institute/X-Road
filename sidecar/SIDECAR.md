# X-Road Security Server Sidecar

X-Road Security Server Sidecar is a containerized version of the Security Server that supports production use. The
Sidecar is a Docker container that runs in the same virtual context (virtual host, Kubernetes Pod, etc.) with an
information system. The Sidecar can be used for both consuming and producing services.

![Security Server Sidecar](../doc/Sidecar/img/security_server_sidecar.png) 

## What Is a Sidecar?

In general, sidecar is a design pattern commonly used in a microservices architecture. A sidecar is an additional
component that is attached to a parent application to extend its functionalities. The original idea of the sidecar
pattern is that multiple copies of the same sidecar are attached to the application so that each instance of the
application has its own sidecar.

Despite its name, the original sidecar pattern does not work very well with the Security Server Sidecar since the
Sidecar requires the same configuration and registration process as the regular Security Server. Also, even if the
Security Server is containerized, the footprint of the Sidecar container is still relatively massive compared to the
footprint of average containers. Therefore, it’s recommended that a single Sidecar container is shared between multiple
instances of an application, and it may also be shared between different applications too. For high availability and
scalability, a Sidecar cluster consisting of a primary node and multiple secondary nodes can be considered.

## Sidecar Docker Image

X-Road Security Server Sidecar Docker images contain a custom set of modules instead of `xroad-securityserver`.

The `slim` image installs:

* xroad-proxy
* xroad-proxy-ui-api
* xroad-database-remote
* xroad-secret-store-remote
* openbao

`xroad-base`, `xroad-confclient` and `xroad-signer` arrive as dependencies of `xroad-proxy`.

The `full` image adds, on top of `slim`:

* xroad-monitor
* xroad-opmonitor
* xroad-auxiliary-service (backup/restore, and the scheduled message log archive/cleanup jobs)
* xroad-message-log-archiver (a one-shot CLI that `xroad-auxiliary-service` invokes on a cron schedule; it has no
  supervisord program of its own)
* xroad-ds-control-plane
* xroad-ds-identity-hub

The image is built from pre-built X-Road software packages, either installed from an X-Road apt repository
selected by the `REPO` build argument, or, for development builds, from a local directory of tree-built `.deb`
packages (`PACKAGE_SOURCE=internal`, see [docker-build.sh](docker-build.sh)'s `--packages-path` option).
Released images are built from the official [X-Road repository](https://artifactory.niis.org/xroad-release-deb);
the default in the Dockerfile is the development repository.

## Security Server Sidecar Installation

See the [User guide](../doc/Sidecar/security_server_sidecar_user_guide.md) for information about how to install and
configure Sidecar.

The Security Server Sidecar Docker image (`niis/xroad-security-server-sidecar`) has been published on
[Docker Hub](https://hub.docker.com/r/niis/xroad-security-server-sidecar).

## Key Points and Limitations for X-Road Security Server Sidecar Deployment

* The Security Server Sidecar `slim` version does not support environmental monitoring, operational monitoring,
  message log archiving, backup/restore nor dataspace services, which are recommended for a service provider's
  Security Server role.
* The Security Server Sidecar embeds [OpenBao](https://openbao.org/) as its secret store by default, running under
  supervisord and initialized on first boot (unseal, PKI/secret mounts, client token). Point the container at an
  external secret store instead with the `XROAD_SECRET_STORE_*` environment variables — see the
  [User guide](../doc/Sidecar/security_server_sidecar_user_guide.md) for details.
* The Security Server Sidecar creates and manages its own internal TLS keys and certificates and does TLS termination
  by itself. In a cluster setup with an external load balancer, the load balancer must use SSL passthrough so that SSL
  termination is done by the Sidecar.
* The `xroad` services are run inside the container using supervisord as root, although the processes it starts are
  not. To avoid potential security issues, it is possible to set up Docker so that it uses Linux user namespaces, in
  which case root inside the container is not root (user id 0) on the host. For more information, see
  <https://docs.docker.com/engine/security/userns-remap/>.
* Executable scripts mounted into `/etc/xroad/entrypoint.d/` run once, on first boot, after the image's own database
  provisioning and seeding and before supervisord starts any service — see the
  [User guide](../doc/Sidecar/security_server_sidecar_user_guide.md) for the full contract.
