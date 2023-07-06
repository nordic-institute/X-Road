# Security Server Sidecar User Guide <!-- omit in toc -->

Version: 1.10  
Doc. ID: UG-SS-SIDECAR

## Version history <!-- omit in toc -->

 Date       | Version | Description                                             | Author
 ---------- |---------|---------------------------------------------------------| --------------------
 13.11.2020 | 1.0     | Initial version                                         | Alberto Fernandez Lorenzo
 24.12.2020 | 1.1     | Add description of features of different image versions | Petteri Kivimäki
 21.01.2021 | 1.2     | Removal of kubernetes related sections                  | Alberto Fernandez Lorenzo
 10.02.2021 | 1.3     | Modify description of different supported platforms     | Raul Martinez Lopez
 06.05.2021 | 1.4     | Updated X-Road version                                  | Raul Martinez Lopez
 12.07.2021 | 1.5     | Added 6.25.0 to 6.26.0 upgrade steps                    | Raul Martinez Lopez
 15.10.2021 | 1.6     | Minor documentation updates                             | Janne Mattila
 02.11.2021 | 1.7     | Updates for Sidecar 7.0.0                               | Jarkko Hyöty
 28.11.2021 | 1.8     | Add license info                                        | Petteri Kivimäki
 11.10.2022 | 1.9     | Minor documentation updates regarding upgrade process   | Monika Liutkute
 06.07.2023 | 1.10    | Sidecar repo migration                                  | Eneli Reimets

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 4.0 International License.
To view a copy of this license, visit <https://creativecommons.org/licenses/by-sa/4.0/>

## Table of Contents
<!-- vim-markdown-toc GFM -->

* [License](#license)
* [1 Introduction](#1-introduction)
  * [1.1 X-Road Security Server Sidecar images](#11-x-road-security-server-sidecar-images)
  * [1.2 References](#12-references)
* [2 Installation](#2-installation)
  * [2.1 Prerequisites](#21-prerequisites)
  * [2.2 Reference data](#22-reference-data)
  * [2.3 Network](#23-network)
  * [2.4 Running the Sidecar Container](#24-running-the-sidecar-container)
    * [Verifying that the Sidecar is Running](#verifying-that-the-sidecar-is-running)
  * [2.5 Using an External Database](#25-using-an-external-database)
    * [Reconfiguring the external database address after initialization](#reconfiguring-the-external-database-address-after-initialization)
  * [2.6 Changing the logging Level](#26-changing-the-logging-level)
  * [2.7 Using Volumes](#27-using-volumes)
  * [2.8 Automatic backups](#28-automatic-backups)
  * [2.9 Message log archives](#29-message-log-archives)
* [3 Initial configuration](#3-initial-configuration)
* [4 Upgrading](#4-upgrading)
  * [4.1 Upgrading from version 6.26.0 to 7.0.0](#41-upgrading-from-version-6260-to-700)
* [5 High Availability Setup](#5-high-availability-setup)

<!-- vim-markdown-toc -->

## 1 Introduction

X-Road Security Server Sidecar is containerized, production ready, version of the X-Road Security Server. This document describes the installation and maintenance of the Sidecar, to the extent it differs from the X-Road Security Server for Ubuntu server. For additional details, see [IG-SS](#Ref_IG-SS) and [UG-SS](#Ref_UG_SS).

### 1.1 X-Road Security Server Sidecar images

The Security Server Sidecar has several images with alternative configurations:

**Image**                                                    | **Description**
------------------------------------------------------------ | -----------------------------------------------------------------------------------------------------------------
niis/xroad-security-server-sidecar:\<version>-slim           | Slim image with the minimum required packages and configuration to function.
niis/xroad-security-server-sidecar:\<version>                | Full image uses the slim as the base and adds message logging, and environmental and operational monitoring.
niis/xroad-security-server-sidecar:\<version>-slim-\<variant>| Same as the slim image but with the NIIS member/partner country variant (ee,fi,fo,is) settings included.
niis/xroad-security-server-sidecar:\<version>-\<variant>     | Same as the full image but with the NIIS member/partner country variant configuration settings included.

All images can act as a provider or consumer Security Server. The images with a country code suffix (e.g., `-fi`) include NIIS member/partner -specific configuration.

**Feature**                      | **Sidecar** | **Sidecar Slim** |
---------------------------------|-------------|------------------|
Consume services                 | Yes         | Yes              |
Provide services                 | Yes         | Yes              |
Message logging                  | Yes         | No               |
Environmental monitoring         | Yes         | No               |
Operational monitoring           | Yes         | No               |

### 1.2 References

1. <a id="Ref_IG-SS">[IG-SS]</a> [X-Road: Security Server Installation Guide](../Manuals/ig-ss_x-road_v6_security_server_installation_guide.md)
2. <a id="Ref_IG-SS-Annex-D">[IG-SS-Annex-D]</a> [X-Road: Security Server Installation Guide](../Manuals/ig-ss_x-road_v6_security_server_installation_guide.md#annex-d-create-database-structure-manually)
3. <a id="Ref_UG-SS">[UG-SS]</a> [X-Road: Security Server User Guide](../Manuals/ug-ss_x-road_6_security_server_user_guide.md)

## 2 Installation

### 2.1 Prerequisites

The X-Road Security Server Sidecar can be deployed using Docker (Linux, x86-64 architecture) or [Kubernetes](kubernetes_security_server_sidecar_user_guide.md).
Docker Desktop for Windows or macOS (x86-64) can be used in testing and development, but is not supported for production use.

Minimum container resource limits for running the Security Server Sidecar container:
* CPUs: 2
* Memory: 3 GiB (slim, 4GiB or more for a full container)
* 3 GiB free disk space

### 2.2 Reference data

The following parameters are used in example commands:

| **Value**                           | **Explanation**
|-------------------------------------| ----------------------------------------------------------
| \<container name>                   | Name of the Security Server Sidecar container
| \<admin port>                       | Port for admin user interface (default 4000)
| \<healthcheck port>                 | Port for service health check (default 5588)
| \<consumer information system port> | Consumer information system port (default 8080 (http), 8443 (https))
| \<token pin>                        | Software token PIN code
| \<admin user>                       | Admin username
| \<admin password>                   | Admin password
| \<database host>                    | (Optional) host for external database
| \<database port>                    | (Optional) port for external database, default 5432
| \<database password>                | (Optional) External database super user password
| \<log level>                        | (Optional) Logging level, one of: TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF
| \<database name>                    | (Optional) Database name prefix ('serverconf' becomes '\<database-name>\_serverconf'), useful when using a shared database server
| \<config volume name>               | Name of the configuration volume
| \<database volume name>             | Name of the local database volume
| \<archive volume name>              | Name of the archive/backup volume

### 2.3 Network

The table below lists the required connections between different components.

| Connection | Source                      | Target                       | Target Ports     | Protocol     | Note                    |
-------------|-----------------------------|------------------------------|------------------|--------------|-------------------------|
| Inbound    | Other Security Servers      | Sidecar                      | 5500, 5577       | tcp          |                         |
| Inbound    | Consumer Information System | Sidecar                      | 8080, 8443       | tcp          | From "internal" network |
| Inbound    | Admin                       | Sidecar                      | 4000             | https        | From "internal" network |
| Outbound   | Sidecar                     | Central Server               | 80, 4001         | http(s)      |                         |
| Outbound   | Sidecar                     | OCSP Service                 | 80 / 443 / other | http(s)      |                         |
| Outbound   | Sidecar                     | Timestamping Service         | 80 / 443 / other | http(s)      | Not used by *slim*      |
| Outbound   | Sidecar                     | Other Security Server(s)     | 5500, 5577       | tcp          |                         |
| Outbound   | Sidecar                     | Producer Information System  | 80, 443, other   | http(s)      | To "internal" network   |

Notes:
* Using a firewall to protect the Security Server is recommended. The firewall can be applied to both incoming and outgoing connections, depending on the security requirements of the environment where the Security Server will be deployed.
* The inbound target ports are *container ports*. It necessary to publish (`docker run ... -p <host port>:<container port>` ..) those so that the container is accessible from the outside.
* The inbound ports 5500/tcp and 5577/tcp for communicating with other Security Servers must be published to the same ports on the host (or the public interface if NAT/firewall is in use)
  * If the Security Server is only consuming services, it is not necessary to publish ports 5500 and 5577.
* 4000, 8080, 8443 should be only accessible to internal clients (can be mapped to other ports)

See also [Docker Networking](https://docs.docker.com/network/)

### 2.4 Running the Sidecar Container

To run X-Road Security Server Sidecar, use one of the images published in [Docker Hub](https://hub.docker.com/r/niis/xroad-security-server-sidecar).
Alternatively, you can build container images locally using the [docker-build.sh script](../../sidecar/docker-build.sh).

```bash
docker run --detach \
  --name <container name> \
  -p 127.0.0.1:<admin port>:4000 \
  -p 127.0.0.1:<healthcheck port>:5588 \
  -p <consumer information system port>:8443 \
  -p 5500:5500 \
  -p 5577:5577 \
  -e XROAD_TOKEN_PIN=<token pin> \
  -e XROAD_ADMIN_USER=<admin user> \
  -e XROAD_ADMIN_PASSWORD=<admin password> \
  -e XROAD_LOG_LEVEL=INFO \
  # Optional parameters - BEGIN
  -v <config-volume>:/etc/xroad \
  -v <archive-volume>:/var/lib/xroad \
  -v <database-volume>:/var/lib/postgresql/12/main \
  -e XROAD_DB_HOST=<database-host> \
  -e XROAD_DB_PORT=<database-port> \
  -e XROAD_DB_PWD=<postgres password> \
  # Optional parameters - END
  niis/xroad-security-server-sidecar:<version[-type[-variant]>
```

Note! This command persists all configuration inside the Sidecar container which means that state is lost when the container is destroyed.
In production use, either persistent volumes should be used. Using a separate database is recommended.

#### Verifying that the Sidecar is Running

1. Ensure from the command line that the container is running:

    ```bash
    docker ps --filter "name=<container name>"
    CONTAINER ID   IMAGE                                            COMMAND                 CREATED             STATUS         PORTS     NAMES
    b3031affa4b7   niis/xroad-security-server-sidecar:<image tag>   "/root/entrypoint.sh"   10 minutes ago      Up 10 minutes  ...       <container name>
    ```

2. Ensure from the command line that the X-Road services are running in the container:
    ```bash
    docker exec -t <container name> supervisorctl status
    xroad-autologin                  RUNNING    Nov 04 12:23 PM
    xroad-confclient                 RUNNING   pid 468, uptime 0:15:55
    xroad-monitor                    RUNNING   pid 471, uptime 0:15:55
    xroad-opmonitor                  RUNNING   pid 470, uptime 0:15:55
    xroad-proxy                      RUNNING   pid 473, uptime 0:15:55
    xroad-proxy-ui-api               RUNNING   pid 476, uptime 0:15:55
    xroad-signer                     RUNNING   pid 472, upt|ime 0:15:55
    ```

3. Ensure that you can open the admin user interface URL `https://127.0.0.1:<admin port>` in a web browser. To log in, use the credentials you set during the installation (\<admin user>, \<admin password>). While the user interface is still starting up, the web browser may display a connection refused -error.

### 2.5 Using an External Database

For full compatibility, the external database must be PostgreSQL version 12 (for example backup and restore does not work if the version differs).
When starting the container, provide the external database server hostname, server port, and superuser credentials (for creating the necessary users and tables) as parameters. 
For example:

```bash
# Create a network for the container(s)
docker network create -d bridge xroad-network

# Start a postgresql server for the external database
docker run -d \
-v postgres-data:/var/lib/postgresql/data \
-e POSTGRES_PASSWORD=<postgres password> \
--name remote-db \
--network xroad-network \
postgres:12

# Run sidecar
docker run -d \
-v <config volume name>:/etc/xroad \
-p 127.0.0.1:<admin port>:4000 \
-p 127.0.0.1:<healthcheck port>:5588 \
-p <consumer information system port>:8443 \
-p 5500:5500 \
-p 5577:5577 \
--network xroad-network \
-e XROAD_TOKEN_PIN=<token pin> \
-e XROAD_ADMIN_USER=<admin user> \
-e XROAD_ADMIN_PASSWORD=<admin password> \
-e XROAD_DB_HOST=remote-db \
-e XROAD_DB_PWD=<postgres password> \
--name <container name> \
niis/xroad-security-server-sidecar:<version[-type[-variant]>
```

X-Road Security Server supports a variety of cloud databases including AWS RDS and Azure Database for PostgreSQL. 
See [IG-SS](#Ref_IG-SS) for more information about using remote database.

#### Reconfiguring the external database address after initialization

It is possible to change the external database host after the initialization while the Sidecar container is running. This will not recreate the database, so make sure that you have already created the database(s) and a users. See [IG-SS](#Ref_IG-SS) for details about creating the database structure.

To change the database host, you need to:

1. Edit db.properties inside the container

    ```bash
    docker exec -it <sidecar container name> nano /etc/xroad/db.properties
    ```
2. Replace the connection host, the username and password with the properties of the new database:

    ```bash
    [...]
    # db.properties
    serverconf.hibernate.connection.url = jdbc:postgresql://<new host ip>:5432/serverconf
    serverconf.hibernate.connection.username = <new user>
    serverconf.hibernate.connection.password = <new password>
    [...]
    ```

    If other database like `messagelog` or `op-monitor` are also configured in the `/etc/xroad/db.properties`, you must change their properties in the same way as in the example above.

3. Update the admin users by editing `/etc/xroad.properties` file and replace the admin users and passwords with the new ones:

    ```bash
    [...]
    docker exec -it <sidecar container name> nano /etc/xroad.properties
    # xroad.properties
    serverconf.database.admin_user = <new serverconf admin>
    serverconf.database.admin_password = <new serverconf password>
    [...]
    ```

    If you are using the regular version of the Security Server Sidecar with the admin users for the `messagelog` and `op-monitor` databases, you must do the same for the admin users.

4. After you have changed the properties, restart the container

    ```bash
    docker restart <sidecar container name>
    ```

### 2.6 Changing the logging Level

It is possible to adjust the logging level. To do this, set the environment variable XROAD_LOG_LEVEL when starting the container. The value of this variable can be one of the case-sensitive string values: TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. If the environment variable is not set, the logging level will be INFO by default.

### 2.7 Using Volumes

It is recommended to configure persistent [storage](https://docs.docker.com/storage) for the files in the following locations:

| Mount point                  | Description                                               |
|------------------------------|-----------------------------------------------------------|
| /etc/xroad                   | X-Road configuration                                      |
| /var/lib/xroad               | Backups and messagelog archives                           |
| /var/lib/postgresql/12/main  | Local database files (not applicable to external database |

For example, to use a volume for the configuration folder, add the following parameter to the docker run command:
```bash
docker run ... -v <config volume name>:/etc/xroad ...
```

### 2.8 Automatic backups

The Security Server backs up its configuration automatically once every day, by default. Backups older than 30 days are automatically removed from the server.
If needed, you can adjust the automatic backup policies by editing the `/etc/cron.d/xroad-proxy` file.

Automatic backups will be stored in the folder `/var/lib/xroad/backup/`.

### 2.9 Message log archives

Does not apply to *slim* image.

The Security Server Sidecar periodically archives message log records in the folder `/var/lib/xroad/`.
It is recommended to store the archives to a volume by adding a volume mapping for the archive directory.

## 3 Initial configuration

To configure the X-Road Security Server Sidecar, open a browser to `https://127.0.0.1:<admin port>` (assuming the container admin port 4000 is published to localhost) and log in using the admin credentials.
See [IG-SS](#Ref_IG-SS) for configuration details.

## 4 Upgrading

Upgrading to a new image is supported, provided that:

* The new container image has the same or subsequent minor version of the X-Road Security Server
  * As an exception, upgrading from 6.26.0 to 7.0.x is supported despite the major version change.
* A volume is used for `/etc/xroad`
* A remote database is used, or a volume is mapped to `/var/lib/postgresql/12/data`
* The `xroad.properties` file with `serverconf.database.admin_user` etc. credentials is either mapped to `/etc/xroad.properties` or present in `/etc/xroad/xroad.properties`
* The same image type (slim or full) and variant (ee, fi, ...) are used for the new container

If the prerequisites are met, upgrading is straightforward:

* Stop the old container.
  ```
  docker stop <container name>
  docker rename <container name> <container name prev>
  ```
* Run a container using the new (or refreshed) image, using the volumes from the old container.
  ```
  docker run -d \
  --volumes-from <container name prev> \
  ... published ports and other parameters, e.g network ...
  -e XROAD_ADMIN_USER=<admin user> \
  -e XROAD_ADMIN_PASSWORD=<admin password> \
  --name <container name> \
  niis/xroad-security-server-sidecar:<new version[-type[-variant]>
  ```

Notes:
* If the old container was ephemeral, it is necessary to manually map the volumes (can not use --volumes-from)
* Admin user needs to be created every time since it is not part of the persistent configuration
* If `xroad.properties` file containing the database administrator credentials will be missing during the upgrade, 
then non admin user credentials from `db.properties` will be used, which might cause issues if those credentials won't have enough 
permissions to execute database updates

### 4.1 Upgrading from version 6.26.0 to 7.0.0

Upgrading from 6.26.0 to 7.0.0 is supported, if the above prerequisites are met. However, due to a problem in installer scripts, 
it is necessary to verify that the `/etc/xroad.properties` file has been correctly populated.
(see [IG-SS, Annex D](#Ref_IG-SS-Annex-D) for details describing expected file content and manual creation instructions).
Backups are not compatible between 6.26.0 and 7.0.0, so upgrading using a backup is not possible.

In case the prerequisites are not fully met, it is possible to manually prepare an intermediate container for the upgrade:
(this example assumes local database)

* Create an image of the current container:
  ```
  docker commit <container name> sidecar-temp-image
  docker stop <container name>
  ```
* Create a new container with volume mounts using the temporary image, and make a copy of the xroad.properties file
  ```
  docker run -v sidecar-config:/etc/xroad -v sidecar-db:/var/lib/postgresql/12/data ... -n <container-name-temp> sidecar-temp-image
  ```
  Copy `/etc/xroad.properties` into the volume unless it is a bind mounted file. If `/etc/xroad.properties` is a bind mounted file, verify that the `serverconf.database.admin_user` etc. credentials exist and are correct.
  ```
  docker exec sidecar-temp-image cp /etc/xroad.properties /etc/xroad/xroad.properties
  docker stop <container-name-temp>
  ```
* Upgrade using the upgrade instructions above

## 5 High Availability Setup

For a high availability setup, see [Running Sidecar in Kubernetes](kubernetes_security_server_sidecar_user_guide.md)
