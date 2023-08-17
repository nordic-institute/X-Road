# Security Server Sidecar Security Guide <!-- omit in toc -->

Version: 1.3  
Doc. ID: UG-SS-SEC-SIDECAR

## Version history <!-- omit in toc -->

 Date       | Version | Description            | Author
 ---------- |---------|------------------------| --------------------
 05.02.2021 | 1.0     | Initial version        | Raul Martinez Lopez
 28.11.2021 | 1.1     | Add license info       | Petteri Kivimäki
 11.10.2022 | 1.2     | Updating links         | Monika Liutkute
 06.07.2023 | 1.3     | Sidecar repo migration | Eneli Reimets

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 4.0 International License.
To view a copy of this license, visit <https://creativecommons.org/licenses/by-sa/4.0/>

## Table of Contents

* [License](#license)
* [1 Introduction](#1-introduction)
  * [1.1 Target Audience](#11-target-audience)
  * [1.2 Environment assumptions](#12-environment-assumptions)
* [2 Securing Sidecar container](#2-securing-sidecar-container)
  * [2.1 Securing Host Operating System](#21-securing-host-operating-system)
    * [2.1.1 Host configuration](#211-host-configuration)
      * [2.1.1.1 Ensure separate partition for Docker data](#2111-ensure-separate-partition-for-docker-data)
      * [2.1.1.2 Ensure auditing is configured for the Docker daemon](#2112-ensure-auditing-is-configured-for-the-docker-daemon)
      * [2.1.1.3 Secure Docker socket file](#2113-secure-docker-socket-file)
      * [2.1.1.4 Secure Docker daemon socket connection](#2114-secure-docker-daemon-socket-connection)
    * [2.1.2 Docker Daemon configuration](#212-docker-daemon-configuration)
      * [2.1.2.1 Enable user namespace support](#2121-enable-user-namespace-support)
      * [2.1.2.2 Ensure live restore is enabled](#2122-ensure-live-restore-is-enabled)
      * [2.1.2.3 Ensure Userland Proxy is disabled](#2123-ensure-userland-proxy-is-disabled)
  * [2.2 Docker security best practices](#22-docker-security-best-practices)
    * [2.2.1 Restrict Runtime Capabilities](#221-restrict-runtime-capabilities)
    * [2.2.2 Restrict Resource Limits](#222-restrict-resource-limits)
      * [2.2.2.1 Set Memory and CPU usage limits](#2221-set-memory-and-cpu-usage-limits)
      * [2.2.2.2 Set CPU priority threshold limits](#2222-set-cpu-priority-threshold-limits)
      * [2.2.2.3 Set PIDs limits](#2223-set-pids-limits)
* [3 Securing Sidecar deployment](#3-securing-sidecar-deployment)
  * [3.1 Passwords and secrets](#31-passwords-and-secrets)
    * [3.1.1 Reference data](#311-reference-data)
    * [3.1.2 Store credentials outside the container](#312-store-credentials-outside-the-container)
    * [3.1.3 Secure credentials on environment variables](#313-secure-credentials-on-environment-variables)
  * [3.2 User accounts](#32-user-accounts)
  * [3.3 Network and firewalls](#33-network-and-firewalls)
    * [3.3.1 Restrict network traffic between containers](#331-restrict-network-traffic-between-containers)
    * [3.3.2 Isolate container from host network](#332-isolate-container-from-host-network)
  * [3.4 Management API and keys](#34-management-api-and-keys)
  * [3.5 Backups](#35-backups)

## 1 Introduction

In this document, we will go through the most relevant security recommendations to take into account to deploy Security Server Sidecar containers securely on a Linux-based production environment.

### 1.1 Target Audience

The intended audience of this Security Guide are X-Road Security Server system administrators responsible for installing, using and maintaining X-Road Security Server Sidecar software.

The document is intended for readers with a moderate knowledge of Linux server management, computer networks, Docker, Kubernetes and X-Road.

### 1.2 Environment assumptions

The regular version of the Sidecar includes message log, operational monitoring, and environmental monitoring modules, whereas the Sidecar slim version does not include the aforementioned modules. Both the slim and regular versions of the Sidecar can be used for both consuming and producing services. In addition, there are country-specific configuration versions available, such as the Finnish meta-package (currently the only one). More information can be found on the Security Server Sidecar User Guide for the different [image versions](security_server_sidecar_user_guide.md#22-x-road-security-server-sidecar-images).

Note(1) For the scope of this document, we will assume the regular Security Server Sidecar image version is used.

The Security Server Sidecar can run alongside the client or service information system in the same host but in separate containers. In a production environment, a single Security Server Sidecar container may be shared between different information systems. However, the footprint of the Sidecar container is relatively high compared to the footprint of average containers and it has to be taken into account for dimensioning the host where the containers should run. More information can be found on the Security Server Sidecar User Guide for the [requirements to run a Security Server Sidecar container](security_server_sidecar_user_guide.md#24-requirements-for-the-x-road-security-server-sidecar).

Note(2) For the scope of this document, we will assume a single Security Server Sidecar container is running alongside an information system for consuming or providing services.

The Security Server Sidecar can be configured to use either a local database running inside the container or a remote database running externally. Since the Security Server is a stateful application, it is recommended to configure the Sidecar container to use a remote database and persistent file storage external to the container. More information can be found on the Security Server Sidecar User Guide to [set up an external database](security_server_sidecar_user_guide.md#27-external-database) and [bind an external volume](security_server_sidecar_user_guide.md#29-volume-support).

Note(3) For the scope of this document, we will assume the Security Server sidecar is configured to use an external database for storing server configuration, message logs and operational monitoring data and an external volume to store configuration files.

### 1.3 References

1. <a id="Ref_UG-SS">[UG-SS]</a> [X-Road: Security Server User Guide](../Manuals/ug-ss_x-road_6_security_server_user_guide.md)

## 2 Securing Sidecar container

Running Security Server Sidecar in a Docker container has some security implications inherent to the separation of the application layer from the infrastructure layer. Some noteworthy container breaches can occur derived from misconfiguration.

Before running Security Server Sidecar, it is important to carefully review the overall security considerations when running an application in a Docker container addressed on the [Docker security guide](https://docs.docker.com/engine/security/).

This section describes the most relevant security recommendations for securing the X-Road Security Server Sidecar containers in production.

### 2.1 Securing Host Operating System

Before deploying the Security Server Sidecar, it is strongly recommended to use the [Docker Bench for Security tool](https://github.com/docker/docker-bench-security) for securing the Docker host Operating System where the Security Server Sidecar will run, which is based on the recommendations in the [CIS Docker Benchmark](https://www.cisecurity.org/benchmark/docker/) to secure Docker configuration on the host when deploying Docker containers in production. The tool runs checks against the host configuration, Docker daemon configuration, container images and build files, and container runtime Docker security operations. This tool can also be automated as part of a CI/CD pipeline.

Below are the most common security pitfalls by category along with security recommendations to fix them.

#### 2.1.1 Host configuration

##### 2.1.1.1 Ensure separate partition for Docker data

Docker's data directory is located at `/var/lib/docker`. This directory could fill up quickly causing both Docker and the host to become unusable.

By default, `/var/lib/docker` directory is usually mounted under the `/` or `/var` partitions on the host. To ensure proper isolation, it’s a good idea to create a separate partition for this directory.

For a Linux host new installation, you should create a separate partition for the `/var/lib/docker` mount point. For Linux hosts that have already been installed, you should use the Logical Volume Manager (LVM) within Linux to create a new partition.

In a cloud environment, we can move this directory to an external network-attached block device for example.

##### 2.1.1.2 Ensure auditing is configured for the Docker daemon

The Docker daemon runs with root privileges, so it is important to audit all Docker related files and directories in addition to Linux file system and system calls. The Docker Daemon depends on some key files and directories, including `/etc/docker` which holds TLS keys and certificates used to communicate with the Docker client, which should be audited.

In Linux, we should install and configure the auditing daemon `auditd` to enable logging system operations on some of Docker's files, directories, and sockets. More information about auditd can be found [here](https://linux.die.net/man/8/auditd).

We can check whether there is an auditing rule applied to the `/etc/docker` directory by running:

```bash
auditctl -l | grep /etc/docker
```

Below we can check the configuration rules added at the end of the `/etc/audit/audit.rules` file:

```bash
-w /usr/bin/docker -p wa
-w /var/lib/docker -p wa
-w /etc/docker -p wa
-w /lib/systemd/system/docker.service -p wa
-w /lib/systemd/system/docker.socket -p wa
-w /etc/default/docker -p wa
-w /etc/docker/daemon.json -p wa
-w /usr/bin/docker-containerd -p wa
-w /usr/bin/docker-runc -p wa
```

After editing and saving the changes, restart the audit daemon:

```bash
service auditd restart
```

The auditing daemon stores its log data in `/var/log/audit/` by default. This log data is valuable for system administrators to monitor for suspicious activity. To prevent filling up the disk space, the log data should be rotated and archived periodically. The auditing daemon handles log rotation on log size. It is possible to configure the log file size and location by changing the attributes `log_file` and `max_log_file` attributes. More information can be found [here](https://linux.die.net/man/8/auditd.conf).

It is also recommended to have a separate partition or logical volume created for `/var/log/audit/` directory to avoid filling up any other critical partition. For systems that were previously installed, create a new partition and configure `/etc/fstab` appropriately.

We can check whether `/var/log/audit/` directory is on its own partition by running this command:

```bash
mount | grep "on /var/log/audit"
```

##### 2.1.1.3 Secure Docker socket file

The Docker daemon runs as root so the Docker socket file must be owned by root. Otherwise, a non-privileged user or process can interact with the Docker daemon and therefore with Docker containers running. Additionally, the Docker socket should only be group owned by docker group, whose users have root privileges for running containers and is created and managed by the Docker installer. For these reasons, the default Docker Unix socket file should be owned by root and group owned by docker to maintain the integrity of the socket file.

We should make sure it is owned and group owned by root by running this command:

```bash
chown root:docker /var/run/docker.sock
```

We can check that the Docker socket ownership is correct set by running:

```bash
stat -c %U:%G /var/run/docker.sock | grep -v root:docker
```

The command should return no results.

Similarly to the previous one, we should make sure that only the root user and members of the docker group are allowed to read and write to the Docker socket file.

We can do that by running the following command:

```bash
chmod 660 /var/run/docker.sock.
```

We can check that the Docker socket has the correct permissions by running:

```bash
stat -c %a /var/run/docker.sock
```

It is also important to make sure the Docker socket is not mounted inside a container, since it could allow processes running inside such container to effectively gain full control of the Docker host.

We can check that the Docker socket is not mounted in any container by running:

```bash
docker ps --quiet --all | xargs docker inspect --format '{{ .Id }}: Volumes={{ .Mounts }}' | grep docker.sock
```

The command should return no results.

##### 2.1.1.4 Secure Docker daemon socket connection

We should make sure to avoid exposing the Docker socket to the Internet without additional security measures. The Docker daemon can be reached either through a local socket or HTTPS socket, so it is strongly recommended to protect the Docker daemon socket connection, allowing connections only from authenticated clients.

If external access to the Docker daemon is required, we should ensure that TLS authentication is configured to restrict access to the Docker daemon via IP address and port. More information can be found on the Docker documentation about how to set up [TLS authentication for the Docker daemon connection](https://docs.docker.com/engine/security/https/).

We should also be wary of exposing SSH ports, instead, we could use VPN-only, private network access, high random port numbers, or web proxy authentication.

#### 2.1.2 Docker Daemon configuration

The Docker daemon is a background service running on Linux systems. On a typical Docker installation, the Docker daemon binary dockerd is started by a system utility such as systemctl. To start the Docker daemon service we can run:

```bash
sudo systemctl enable docker
```

We can also start the Docker daemon manually for example to test its configuration, by running `dockerd`. The service will run on the foreground so you will see its logs on the terminal.

There are two ways to configure the Docker daemon:

* By editing its JSON configuration file, located at `/etc/docker/daemon.json`.
* By using flags when starting dockerd service.

More information about the Docker daemon configuration can be found at the [Docker documentation](https://docs.docker.com/config/daemon/).

We will go through the most relevant Docker daemon configuration options to secure Security Server Sidecar container.

##### 2.1.2.1 Enable user namespace support

Linux namespaces provide isolation for running processes. However, we should not share the host's user namespaces with containers running on it. For containers whose processes must run as the root user within the container, we can re-map this user to a non-root user on the Docker host. We can do that by adding `"userns-remap": "default"` to the Docker daemon configuration file, if we want Docker to create a non-root user for us, or using "user:group" notation to remap to an existing non-root host user.

##### 2.1.2.2 Ensure live restore is enabled

By default, when the Docker daemon terminates, it shuts down running containers. We should allow the Security Server Sidecar container to continue running even if the Docker daemon is not up to improve uptime during Docker daemon updates for example. We can do that by adding `"live-restore": true` to the Docker daemon configuration file.

##### 2.1.2.3 Ensure Userland Proxy is disabled

We should disable the use of userland proxy process in Linux if it's not necessary to allow a container to reach another container under the same host by forwarding host ports to containers and replace it with iptables rules to reduce the attack surface. We can do that by adding `"userland-proxy": false` to the Docker daemon configuration file.

### 2.2 Docker security best practices

#### 2.2.1 Restrict Runtime Capabilities

Linux capabilities are a set of root privileges which can be granted or removed to the processes running in the container. Docker starts containers with a restricted set of Linux kernel capabilities, but it is strongly recommended to remove all of the capabilities which are not required for the Security Server Sidecar modules running in the container by following the principle of least privilege.

By default, the following capabilities are configured when running a container:

AUDIT_WRITE, CHOWN, DAC_OVERRIDE, FOWNER, FSETID, KILL, MKNOD, NET_BIND_SERVICE, NET_RAW, SETFCAP, SETGID, SETPCAP, SETUID, SYS_CHROOT.

When running the Security Server Sidecar container, it is recommended to drop NET_RAW capability to prevent the container from creating raw sockets, as it can give an attacker access to a container the ability to create spoofed network traffic. We can remove the NET_RAW capability by using the flag `--cap-drop` when running the Security Server Sidecar container.

```bash
docker run ... --cap-drop NET_RAW ...
```

Note (1): The Security Server Sidecar requires the container to run as root to be able to run X-Road functionalities. Therefore, dropping other capabilities than NET_RAW when running the Sidecar container may result in the container to fail or not run as expected.

#### 2.2.2 Restrict Resource Limits

##### 2.2.2.1 Set Memory and CPU usage limits

By default, Docker containers share their resources equally with no limits. We should specify the amount of memory and CPU needed for the container to prevent possible bottlenecks from a faulty process or malicious DoS attacks that cause other containers to run out of resources.
To limit the amount of memory, we can run the container with the `--memory flag`. To limit the amount of CPUs, we can use the flag `--cpus`.

```bash
docker run ... --memory="4g" --cpus="2" ...
```

##### 2.2.2.2 Set CPU priority threshold limits

By default, the CPU time on the host is divided equally between the containers running. If there are no limits on the CPU cycles, it could cause other containers to become unresponsive. By setting CPU shares, we can enforce thresholds for each container to have a proportion of the host machine’s CPU cycles.

We can set CPU shares by container using the `--cpu-shares` flag. Every new container will have 1024 shares by default that corresponds to 100% of the time. If we set CPU shares to a number lower or higher than 1024 it will receive lower or higher priority respectively compared to the other containers. Setting the CPU shares limit is recommended when running several containers in parallel. However, if the CPU shares number is not set appropriately, the Sidecar container may run out of resources and become unresponsive.

For example, we can set different CPU shares for different containers running in parallel:

```bash
docker run ... --cpu-shares="2048"  --cpus="2"
docker run ... --cpu-shares="1024"  --cpus="2"
```

The first container is allowed to freely consume up to twice as much of the 2 CPUs allocated than the second container. If we wanted to distribute the CPU cycles evenly, we would set both containers CPU shares to 1024.

##### 2.2.2.3 Set PIDs limits

A malicious process can exploit the container with a fork bomb attack, in which the fork system call is recursively used until all the system resources are exhausted, causing other containers to slow down or the host system to crash down, requiring a restart of the host to make the system functional again.

We can limit the number of process forks by using the PIDs cgroup parameter `--pids-limit`. As an example, we can set a PIDs limit of 500 to ensure there are no more than 500 processes running on the container at any given time:

```bash
docker run ... --pids-limit="500" ...
```

However, if the number of processes limit is not set appropriately and the limit is reached the Sidecar container may fail and become unresponsive.

## 3 Securing Sidecar deployment

We should make sure that the cluster where the Security Server Sidecar will be running is secured before the deployment of any workload. This chapter analyzes the most relevant security recommendations for securing a X-Road Security Server Sidecar deployment in production.

### 3.1 Passwords and secrets

#### 3.1.1 Reference data

**Ref** | **Value**                                | **Explanation**
------ | ----------------------------------------- | ----------------------------------------------------------
1.1    | &lt;token pin&gt;                         | Software token PIN code
1.2    | &lt;admin user&gt;                        | Admin username
1.3    | &lt;admin password&gt;                    | Admin password
1.4    | &lt;database host&gt;                     | Database host for external or local database, use '127.0.0.1' for local database
1.5    | &lt;database port&gt;                     | (Optional) remote database server port when using an external database
1.6    | &lt;database password&gt;                 | (Optional) remote database admin password when using a external database

#### 3.1.2 Store credentials outside the container

The Security Server Sidecar makes use of credentials stored in configuration files to access the serverconf, messagelog and opmonitor databases, as well as to make use of the software token PIN code.

The Security Server stores the serverconf, messagelog and opmonitor database passwords on `/etc/xroad/db.properties` file.
Additionally, if the Security Server Sidecar is configured to use a remote database, the database superuser password and the passwords of serverconf, messagelog and opmonitor database admin users are stored on `/etc/xroad.properties` file.

The software token PIN code set up during the initial configuration of the Security Server is stored on `/etc/xroad/autologin` file.

The above-mentioned files should not be stored inside the Security Server Sidecar container. More information can be found on the Security Server Sidecar User Guide for [using volumes to store files outside the container](security_server_sidecar_user_guide.md#27-using-volumes).

#### 3.1.3 Secure credentials on environment variables

During the Security Server Sidecar installation, the user should supply the different database and admin UI credentials as well as the software token PIN code, among other parameters, so that the configuration for the Sidecar container is unique. These user-supplied parameters are passed as environment variables to the docker run command (**reference data: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6**):

```bash
docker run ... -e -e XROAD_TOKEN_PIN=<token pin> -e XROAD_ADMIN_USER=<admin user> -e XROAD_ADMIN_PASSWORD=<admin password> \
-e XROAD_DB_HOST=<database host> -e XROAD_DB_PORT=<database port> -e XROAD_DB_PWD=<database password> ...
```

When deploying the Security Server Sidecar in a production cloud environment, we can make use of Kubernetes Secrets to avoid passing this information as plain text. More information can be found on the Kubernetes Security Server Sidecar User Guide for [Kubernetes Secrets](kubernetes_security_server_sidecar_user_guide.md#454-kubernetes-secrets).

### 3.2 User accounts

The Docker installer creates a Unix group called docker. This group should be tightly controlled by the system administrator, since the users belonging to docker group are granted root capabilities. Users in this group can interact with the Docker daemon, as well as manipulate firewalls and other critical data, so users added to this group should be carefully scrutinized.

We can check which users are currently members of the docker group by running the command:

```bash
getent group docker
```

It is important to remove any untrusted users from the docker group.

### 3.3 Network and firewalls

#### 3.3.1 Restrict network traffic between containers

By default, Docker containers run attached to the default bridge network. This default bridge network allows communication between all containers running on the same Docker host.

To prevent unrelated services or containers running on the same host to reach the Security Server sidecar, we should employ a user-defined bridge network, in which only containers attached to that network are able to communicate.

We can create a user-defined bridge network by using the command:

```bash
docker network create -d bridge xroad-network
```

To connect the Security Server Sidecar to the user-defined bridge network, we can use the flag --network when running the container:

```bash
docker run --network xroad-network ...
```

Therefore, if we want an information system running on the same host to communicate with the Security Server Sidecar, they will have to be attached to the same bridge network.

We can check whether the default network bridge has been configured to restrict inter-container communication by running:

```bash
docker network ls --quiet | xargs docker network inspect --format '{{ .Name }}: {{ .Options }}' 
```

The command should return `com.docker.network.bridge.enable_icc:false` for the default network bridge. Otherwise, we can restrict all inter-container communication by adding `"icc": false` to the Docker daemon configuration file.

Containers connected to the same user-defined bridge network effectively expose all ports to each other.

To expose a port to containers or non-Docker hosts on different networks, we can use the flag -p:

```bash
docker run -p 4000:4000 -p 8080:8080 -p 5588:5588 ...
```

More information can be found on the Docker documentation about [Docker bridge networks](https://docs.docker.com/network/bridge/).

#### 3.3.2 Isolate container from host network

It is important to make sure the Security Server Sidecar container is effectively isolated from the host networking. If Docker is set to use the host networking space, any container process can open reserved low numbered ports as any other root process can. It also allows a container to access network services such as D-bus on the Docker host.

We can check whether the host networking is not set by running the command:

```bash
docker ps --quiet --all | xargs docker inspect --format '{{ .Id }}: NetworkMode={{ .HostConfig.NetworkMode }}'
```

The command should return `NetworkMode=xroad-network` for the Security Server Sidecar container.

### 3.4 Management API and keys

The Management API provides access to all the same administrative operations of the Security Server that can be done using the web UI. The Management API is protected with an API key-based authentication. The process to create new API keys is explained in the [Security Server User Guide](../Manuals/ug-ss_x-road_6_security_server_user_guide.md#191-api-key-management-operations).

Internal and admin UI TLS keys and certificates are generated during the first time the Security Server Sidecar container is run. The internal TLS certificate is used for establishing a TLS connection between the Security Server and the client Information Systems. The admin UI TLS certificate is used to authenticate the Information System when HTTPS protocol is used for connections between the service client's or provider's Security Server and Information System. Those keys and certificates can be found under the `/etc/xroad/ssl/` directory in the container.

The above mentioned files should not be stored inside the Security Server Sidecar container. More information can be found on the Security Server Sidecar User Guide for [configuring volumes to store sensitive files outside the container](security_server_sidecar_user_guide.md#291-store-sensitive-information-in-volumes).

### 3.5 Backups

The internal and admin UI and internal TLS certificates created during the installation process will be overwritten by the ones restored from the backup. The X-Road admin user created during the installation process is not included in the backup and must be re-created manually. More information on the Security Server User Guide for the [installation process](security_server_sidecar_user_guide.md#26-installation).

Note that the backup does not include X-Road admin user account(s) or `/etc/xroad.properties` (database admin credentials; needed when using a remote database). You need to take care of moving these manually.
