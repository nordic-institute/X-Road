# X-Road Security Server Sidecar

X-Road Security Server sidecar is a Docker container that is optimized as a consumer Security Server, and intended to be installed as a sidecar next to a consumer information system. In other words, it runs in the same context (virtual host or e.g. Kubernetes Pod) as the consumer information system.

X-Road Security Server sidecar docker image contains a custom set of modules instead of xroad-securityserver:

- xroad-proxy
- xroad-addon-metaservices
- xroad-addon-wsdlvalidator
- xroad-autologin

The X-Road Security Server sidecar software is built from pre-built packages downloaded from the official X-Road repository at [https://artifactory.niis.org/xroad-release-deb](https://artifactory.niis.org/xroad-release-deb).

## 1 Security Server Sidecar Installation

### 1.1 Supported Platforms

The Security Server sidecar can be installed both on physical and virtualized hardware. The installation script setup_security_server_sidecar.sh runs on Unix-based operating systems (of the latter, Mac OS and Ubuntu have been tested).

### 1.2 Prerequisites to installation

The Security Server sidecar installation requires an existing installation of Docker.

Building with Docker BuildKit can slightly reduce the size of the resulting container image.
See <https://docs.docker.com/develop/develop-images/build_enhancements/> for more information.

### 1.3 Requirements for the Security Server Sidecar

Minimum recommended docker engine configuration to run the security server sidecar container:

- CPUs: 2
- Memory: 2 GiB
- Swap: 1 GiB
- Disk space: 2 GiB

### 1.4 Reference Data

*Note*: The information in empty cells should be determined before the server's installation, by the person performing the installation.


 **Ref** | **Value**                                | **Explanation**
 ------ | ----------------------------------------- | ----------------------------------------------------------
 1.1    | &lt;choose name of sidecar container&gt;  | Name of the security server sidecar container
 1.2    | &lt;choose port number&gt;                | Port for admin user interface
 1.3    | &lt;choose PIN for software token&gt;     | Software token PIN code
 1.4    | &lt;choose admin username and password&gt;| Admin username and password
 1.5    | TCP 5500                                  | Ports for inbound connections (from the external network to the security server)<br> Message exchange between security servers
 &nbsp; | TCP 5577                                  | Ports for inbound connections (from the external network to the security server)<br> Querying of OCSP responses between security servers
 1.6    | TCP 5500                                  | Ports for outbound connections (from the security server to the external network)<br> Message exchange between security servers
 &nbsp; | TCP 5577                                  | Ports for outbound connections (from the security server to the external network)<br> Querying of OCSP responses between security servers
 &nbsp; | TCP 80 (1)                                | Ports for outbound connections (from the security server to the external network)<br> Downloading global configuration
 &nbsp; | TCP 80 (1),443                            | Ports for outbound connections (from the security server to the external network)<br> Most common OCSP service
 1.7    | TCP 80 (1)                                | Ports for information system access points (in the local network)<br> Connections from information systems
 &nbsp; | TCP 443                                   | Ports for information system access points (in the local network)<br> Connections from information systems
 1.8    | TCP 5588                                  | Port for health check (local network)
 1.9    | TCP 4000 (2)                              | Port for admin user interface (local network)
 1.10   |                                           | Internal IP address and hostname(s) for security server sidecar
 1.11   |                                           | Public IP address, NAT address for security server sidecar

Note (1): The TCP port 80 in the container is mapped to the user-defined TCP port number provided (ref. data 1.2) plus one on the Docker host.

Note (2): The TCP port 4000 in the container is mapped to the user-defined TCP port number provided (ref. data 1.2) on the Docker host.

### 1.5 Installation

To install the Security Server sidecar in a local development environment, run the script setup_security_server_sidecar.sh providing the parameters in the order shown (reference data 1.1, 1.2, 1.3, 1.4):

  ```bash
  ./setup_security_server_sidecar.sh <name of the sidecar container> <admin UI port> <software token PIN code> <admin username> <admin password> (<remote database server hostname> <remote database server port>)
  ```

The script setup_security_server_sidecar.sh will:

- Create a docker bridge-type network called xroad-network to provide container-to-container communication.
- Build xroad-sidecar-security-server-image performing the following configuration steps:
  - Downloads and installs the packages xroad-proxy, xroad-addon-metaservices, xroad-addon-wsdlvalidator and xroad-autologin from the public NIIS artifactory repository (version bionic-6.22.0 or later).
  - Removes the generated serverconf database and properties files (to be re-generated in the initial configuration script).
  - Removes the default admin username (to be re-generated in the initial configuration script).
  - Removes the generated internal and nginx certificates (to be re-generated in the initial configuration script).
  - Enables health check port and interfaces (by default all available interfaces).
  - Backs up the read-only xroad packages' configuration to allow security server sidecar configuration updates.
  - Copies the xroad security server sidecar custom configuration files.
  - Exposes the container ports 80 (HTTP), 443 (HTTPS), 4000 (admin UI), 5500 (proxy), 5577 (proxy OCSP) and 5588 (proxy health check).
- Start a new security server sidecar container from the xroad-sidecar-security-server-image and execute the initial configuration script, which will perform the following configuration steps:
  - Maps ports 4000 (admin UI) and 80 (HTTP) to user-defined ones (reference data 1.2).
  - Maps port 5588 (proxy health check) to the same host port.
  - Updates security server sidecar configuration on startup if the installed version of the image has been updated.
  - Configures xroad-autologin custom software token PIN code with user-supplied PIN (reference data 1.3).
  - Configures admin credentials with user-supplied username and password (reference data 1.4).
  - Generates new internal and admin UI TLS keys and self-signed certificates to establish a secure connection with the client information system.
  - Recreates serverconf database and properties file with serverconf username and random password.
  - Optionally configures the security server sidecar to use a remote database server.
  - Starts security server sidecar services.
  - Replace 'initctl' for 'supervisorctl' in 'xroad_restore.sh' for start and stop the services.
  - Create sidecar-config directory on the host and mount it into the /etc/xroad config directory on the container.

### 1.6 Installation with remote server configuration database

It is possible to configure the security server sidecar to use a remote database, instead of the default locally installed one. To do that, you need to provide the remote database server hostname and port number as arguments when running the setup_security_server_sidecar.sh script in the order described below. Before running the script, you must also set the environment variable XROAD_DB_PASSWORD with the remote database administrator master password:

  ```bash
  export XROAD_DB_PASSWORD=<remote database administrator master password>
  ./setup_security_server_sidecar.sh <name of the sidecar container> <admin UI port> <software token PIN code> <admin username> <admin password> <remote database server hostname> <remote database server port>
  ```
The user for the connection will be the default database user "postgres".
The following configuration is needed on the remote database server to allow external access to the remote PostgreSQL database from the security server sidecar:

- Edit the PostgreSQL configuration file in `/etc/postgresql/10/main/postgresql.conf` to enable listening on external addresses and to verify the port. NOTE: If you change these settings, the postgresql service must be restarted.

  ```bash
  [...]
    # - Connection Settings -

    listen_addresses = '*'  # what IP address(es) to listen on;
                            # comma-separated list of addresses;
                            # defaults to 'localhost'; use '*' for all
                            # (change requires restart)
    port = 5432             # (change requires restart)
  [...]
  ```

- Edit the PostgreSQL client authentication configuration file in `pg_hba.conf` to enable connections from outside localhost. Replace the IP `127.0.0.1/32` with `0.0.0.0/0`.

  ```bash
  [...]
  # IPv4 local connections:
  host    all             all             0.0.0.0/0            md5
  [...]
  ```
  
- If the database is in your local machine you have to use the interface ip that uses the host to connect to the docker containers. You can check this ip by running "docker inspect container_name" and checking the gateway property.

- The external database has been tested both for external PostgreSQL database running in our local machine, in a remote server or inside another docker container. It also could be integrated with AWS RDS, it has been tested for PostgreSQL engine and Aurora PostegreSQL engine, both with version 10 of the PostgreSQL database. 

#### 1.6.1 Reconfigure external database address after initialization

It is possible to change the external database after the initialization while the Sidecar container is running. This will not recreate the database, so we need to make sure that the 'serverconf' database and a user with granted permissions to access it are already created. To change the database host we need to:
- Run a new command on the sidecar container:
```bash
docker exec -it <sidecar_container_name> bash
  ```
- Inside the container open in a text editor (we can install any of the command line text editors like nano, vi ...) the `etc/xroad/db.properties` file:
 ```bash 
nano etc/xroad/db.properties
  ``` 
- Replace the connection host, the username and password with the properties of the new database:
```bash
  [...]
    # -db.properties -
serverconf.hibernate.connection.url = jdbc:postgresql://<new_host_ip>:5432/serverconf
serverconf.hibernate.connection.username = <new_user>
serverconf.hibernate.connection.password = <new_password>
  [...]
  ```
  If other components like 'message_log' or 'op_monitor' are also configured in the `etc/xroad/db.properties` file to use an external database, we must change their properties in the same way as in the example above.

- After the properties are changed, save and close the  `etc/xroad/db.properties` file  and restart the services by running:
```bash
 supervisorctl restart all
  ``` 

### 1.7 Volume support

It is possible to configure security server sidecar to use volume support. This will allow us to  create sidecar-config and sidecar-config-db directory on the host and mount it into the /etc/xroad and /var/lib/postgresql/10/main  config directories on the container.
For adding volume support we have to modify the docker run sentence inside the setup_security_server_sidecar.sh script and add the volume support:

`-v (sidecar-config-volume-name):/etc/xroad -v (sidecar-config-db-volume-name):/var/lib/postgresql/10/main`

For example:
  ```bash
  [...]
    docker run -v sidecar-config:/etc/xroad -v sidecar-config-db:/var/lib/postgresql/10/main -detach -p $2:4000 -p $httpport:80 -p 5588:5588 --network xroad-network -e XROAD_TOKEN_PIN=$3 -e XROAD_ADMIN_USER=$4 -e XROAD_ADMIN_PASSWORD=$5 -e XROAD_DB_HOST=$postgresqlhost -e XROAD_DB_PORT=$postgresqlport -e XROAD_DB_PWD=$XROAD_DB_PASSWORD --name $1 xroad-sidecar-security-server-image
  [...]
  ```

### 1.8 Finnish settings
  To install the Security Server Sidecar in a local development environment with Finnish settings, modify the image build in the setup_security_server_sidecar.sh changing the path "sidecar/Dockerfile" to "sidecar/fi/Dockerfile"

### 1.9 Security Server Sidecar Provider
  To install the Security Server Sidecar provider, modify the docker image build path in the setup_security_server_sidecar.sh script by changing the path "sidecar/Dockerfile" to "sidecar/provider/Dockerfile". The Sidecar provider is based on the Sidecar image and adds support for message logging, both for internal or remote database setup (more info about remote database support in section 1.6).
  To install the Security Server Sidecar provider with Finnish settings, modify the docker image build path in the setup_security_server_sidecar.sh script by changing the path "sidecar/Dockerfile" to "sidecar/provider/fi/Dockerfile"

#### 1.9.1 Environmental Monitoring for Provider

Environmental monitoring for the Security Server Sidecar provider can be  used to obtain information about the platform it's running on, check more information in <https://github.com/nordic-institute/X-Road/blob/master/doc/EnvironmentalMonitoring/Monitoring-architecture.md/>

#### 1.9.2 Operational monitoring for provider

Operational monitoring for the Security Server Sidecar provider can be  used to obtain information about the services it is running. The operational monitoring processes operational statistics (such as which services have been called, how many times, what was the size of the response, etc.) of the security servers. The operational monitoring will create a database named "op-monitor" for store the data, this database can be configured internally in the container or externally (check 1.6). More information about how to test it can be found here <https://github.com/nordic-institute/X-Road/blob/master/doc/OperationalMonitoring/Testing/test-opmon_x-road_operational_monitoring_testing_plan_Y-1104-2.md/>

#### 1.9.3 Environmental and Operational monitoring for consumer

If we need to add environmental and operational monitoring in the consumer Sidecar, we can use for this the provider Sidecar that could be use as a consumer too.

### 1.10 Logging Level

It is possible to configure the Security Server Sidecar to adjust the logging level so that it is less verbose. To do this, we must set the environment variable XROAD_LOG_LEVEL, the value of this variable could be one of the case-sensitive string values: TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. By default, if the environment variable is not set, the logging level will be INFO.
For setting the environment variable we can either edit the /etc/environment file or run:

 ```bash
  export XROAD_LOG_LEVEL=<logging level value>
  ./setup_security_server_sidecar.sh <name of the sidecar container> <admin UI port> <software token PIN code> <admin username> <admin password> 
  ```

## 2 Security Server Sidecar Initial Configuration

### 2.1 Reference Data

 **Ref** | **Value**                                                | **Explanation**
 ---- | ----------------------------------------------------------- | -------------------------------------------------------
 2.1  | &lt;global configuration anchor file&gt; or &lt;URL&gt;     | Global configuration anchor file or provider URL (1) (2)
 2.2  | &lt;security server owner's member class&gt;<br>E.g.<br> COM - Commercial<br> ORG - Organisation            | Member class of the security server owner for the sidecar (2)
 2.3  | &lt;security server owner's member code&gt;                 | Member code of the security server owner for the sidecar (2) (3)
 2.4  | &lt;security server code&gt;                                | Security server code for the sidecar (4)
 2.5  | &lt;PIN for software token&gt;                              | Software token PIN code (same as ref. data 1.3)

Note (1): The global configuration provider's download URL and TCP port 80 must be reachable from the security server sidecar network.

Note (2): Reference items 2.1 - 2.3 are provided to the security server owner by the X-Road central server's administrator.

Note (3): The security server member code usually refers to the organization's business code, although there can be other conventions depending on the X-Road governing authority's rules.

Note (4): The security server code uniquely identifies the security server in an X-Road instance. X-Road instance's governing authority may dictate rules how the code should be chosen.

### 2.2 Configuration

To perform the initial configuration, navigate to the Admin UI address:

  ```bash
    https://SECURITY_SERVER_SIDECAR_IP:ADMIN_UI_PORT/
  ```

(reference data 1.10, 1.2) and accept the self-signed certificate. To log in, use the admin username and password chosen during the installation (reference data 1.4).

Upon first log-in, the system asks for the following information:

- The global configuration anchor file (reference data: 2.1).

Then, if the configuration is successfully downloaded, the system asks for the following information:

- The security server owner's member class for the sidecar (reference data: 2.2)
- The security server owner's member code for the sidecar (reference data: 2.3). If the member class and member code are correctly entered, the system displays the security server sidecar owner's name as registered in the Central Server
- The security server code for the sidecar (reference data: 2.4), it has to be unique across the whole X-Road instance.
- Software token PIN code (reference data: 2.5). The PIN will be used to protect the keys stored in the software token. The process xroad-autologin will automatically enter the PIN code after some time.

## 3 Key Points and Limitations for X-Road Security Server Sidecar Deployment

- The current security server sidecar implementation is a Proof of Concept and it is meant for testing and development purposes.
- The current security server sidecar implementation does not support message logging, operational monitoring nor environmental monitoring functionality, which is recommended for a service provider's security server role. This functionality will be included in future releases.
- The security server sidecar creates and manages its own internal TLS keys and certificates and does TLS termination by itself. This configuration might not be fully compatible with the application load balancer configuration in a cloud environment.
- The xroad services are run inside the container using supervisord as root, although the processes it starts are not. To avoid potential security issues, it is possible to set up Docker so that it uses Linux user namespaces, in which case root inside the container is not root (user id 0) on the host. For more information, see <https://docs.docker.com/engine/security/userns-remap/>.

## 4 Kubernetes jobs readiness, liveness and startup probes
### 4.1 Readiness probes
The readiness probes will perform a health check periodically in a specific time. If the health check fails, the pod will remain in a not ready state until the health check succeeds. The pod in a not ready state will be accessible through his private IP but not from the balancer and the balancer will not redirect any message to this pod. We use readiness probes instead of liveliness probes because with readiness probes we still can connect to the pod for configuring it (adding certificates...) instead of the liveliness probes that will restart the pod until the health check succeeds.

The readiness probes are useful when the pod it's not ready to serve traffic but we don't want to restart it maybe because the pod needs to be configured to be ready, such as adding the certificates...

We will use the following parameters in the Kubernetes configuration file to set up the readiness probe:
 - initialDelaySeconds:  Number of seconds after the container has started before readiness probes are initiated. For this example we will use 200 seconds to have enough time for the image be downloaded and the services are ready.
 - periodSeconds:  How often (in seconds) to perform the probe. 
 - successThreshold: Minimum consecutive successes for the probe to be considered successful after having failed.
 - failureThreshold:  When a probe fails, Kubernetes will try failureThreshold times before giving up and mark the container as not ready.
 - port: Healthcheck port.
 - path: Healthcheck path

  ```bash
  [...]
containers:
  readinessProbe:
    httpGet:
      path: /
      port: 5588
    initialDelaySeconds: 200
    periodSeconds: 30
    successThreshold: 1
    failureThreshold: 1
  [...]
  ```

### 4.2 Liveness probes
The liveness probes are used to know when restart a container. The liveness probes will perform a health check each period of time and restart the container if it fails.

The liveness probes are useful when the pod is not in a live state and can not be accessed through the UI, for example, due to the pod being caught in a deadlock or one of the services running in the container has stopped.

The parameters for the liveness probes are the same than for the readiness probes, but using the port 80 to check if nginx is running and serving the application instead of using port 5588 to check if the Sidecar pod is ready to serve traffic. It is recommended also to increase the failureThreshold value.

  ```bash
  [...]
containers:
livenessProbe:
  httpGet:
   path: /
   port: 80
  initialDelaySeconds: 100
  periodSeconds: 30
  successThreshold: 1
  failureThreshold: 5
  [...]
  ```