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

### 1.2 Reference Data

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

### 1.3 Installation

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


### 1.4 Volume support

It is possible to configure security server sidecar to use volume support. This will allow us to  create sidecar-config and sidecar-config-db directory on the host and mount it into the /etc/xroad and /var/lib/postgresql/10/main  config directories on the container.
For adding volume support we have to modify the docker run sentence inside the setup_security_server_sidecar.sh script and add the volume support:

`-v (sidecar-config-volume-name):/etc/xroad -v (sidecar-config-db-volume-name):/var/lib/postgresql/10/main`

For example:
  ```bash
  [...]
    docker run -v sidecar-config:/etc/xroad -v sidecar-config-db:/var/lib/postgresql/10/main -detach -p $2:4000 -p $httpport:80 -p 5588:5588 --network xroad-network -e XROAD_TOKEN_PIN=$3 -e XROAD_ADMIN_USER=$4 -e XROAD_ADMIN_PASSWORD=$5 -e XROAD_DB_HOST=$postgresqlhost -e XROAD_DB_PORT=$postgresqlport -e XROAD_DB_PWD=$XROAD_DB_PASSWORD --name $1 xroad-sidecar-security-server-image
  [...]
  ```

### 1.5 Finnish settings
  To install the Security Server Sidecar in a local development environment with Finnish settings, modify the image build in the setup_security_server_sidecar.sh changing the path "sidecar/Dockerfile" to "sidecar/fi/Dockerfile"

### 1.6 Security Server Sidecar
  To install the Security Server Sidecar provider, modify the docker image build path in the setup_security_server_sidecar.sh script by changing the path "sidecar/Dockerfile" to "sidecar/provider/Dockerfile". The Sidecar provider is based on the Sidecar image and adds support for message logging, both for internal or remote database setup (more info about remote database support in section 1.6).
  To install the Security Server Sidecar provider with Finnish settings, modify the docker image build path in the setup_security_server_sidecar.sh script by changing the path "sidecar/Dockerfile" to "sidecar/provider/fi/Dockerfile"


### 1.7 Estimated time for new Security Server Sidecar Installation

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


### 1.8 Logging Level

It is possible to configure the Security Server Sidecar to adjust the logging level so that it is less verbose. To do this, we must set the environment variable XROAD_LOG_LEVEL, the value of this variable could be one of the case-sensitive string values: TRACE, DEBUG, INFO, WARN, ERROR, ALL or OFF. By default, if the environment variable is not set, the logging level will be INFO.
For setting the environment variable we can either edit the /etc/environment file or run:

 ```bash
  export XROAD_LOG_LEVEL=<logging level value>
  ./setup_security_server_sidecar.sh <name of the sidecar container> <admin UI port> <software token PIN code> <admin username> <admin password>
  ```

## 2 Key Points and Limitations for X-Road Security Server Sidecar Deployment

- The current security server sidecar implementation is a Proof of Concept and it is meant for testing and development purposes.
- The current security server sidecar implementation does not support message logging, operational monitoring nor environmental monitoring functionality, which is recommended for a service provider's security server role. This functionality will be included in future releases.
- The security server sidecar creates and manages its own internal TLS keys and certificates and does TLS termination by itself. This configuration might not be fully compatible with the application load balancer configuration in a cloud environment.
- The xroad services are run inside the container using supervisord as root, although the processes it starts are not. To avoid potential security issues, it is possible to set up Docker so that it uses Linux user namespaces, in which case root inside the container is not root (user id 0) on the host. For more information, see <https://docs.docker.com/engine/security/userns-remap/>.

## 3 Kubernetes jobs readiness, liveness and startup probes
### 3.1 Readiness probes
The readiness probes will perform a health check periodically in a specific time. If the health check fails, the pod will remain in a not ready state until the health check succeeds. The pod in a not ready state will be accessible through his private IP but not from the balancer and the balancer will not redirect any message to this pod. We use readiness probes instead of liveliness probes because with readiness probes we still can connect to the pod for configuring it (adding certificates...) instead of the liveliness probes that will restart the pod until the health check succeeds.

The readiness probes are useful when the pod it's not ready to serve traffic but we don't want to restart it maybe because the pod needs to be configured to be ready,  for example,  adding the certificates.

We will use the following parameters in the Kubernetes configuration file to set up the readiness probe:
 - initialDelaySeconds:  Number of seconds after the container has started before readiness probes are initiated. For this example we will use 200 seconds to have enough time for the image be downloaded and the services are ready.
 - periodSeconds:  How often (in seconds) to perform the probe.
 - successThreshold: Minimum consecutive successes for the probe to be considered successful after having failed.
 - failureThreshold:  When a probe fails, Kubernetes will try failureThreshold times before giving up and mark the container as not ready.
 - port: Healthcheck port
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

### 3.2 Liveness probes
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
  periodSeconds: 10
  successThreshold: 1
  failureThreshold: 5
  [...]
  ```

### 3.3 Startup probes
The startup probes indicate whether the application within the container is started. All other probes are disabled if a startup probe is provided until it succeeds.

Startup probes are useful for Pods that have containers that take a long time to come into service. This is not really useful in the Sidecar pod because it takes to short to start.
In a different scenario where the Sidecar would take a long time to start, the startup probe can be used in combination with the liveness probe, so that it waits until the startup probe has succeeded before starting the liveness probe. The tricky part is to set up a startup probe with the same command, HTTP or TCP check, with a failureThreshold * periodSeconds long enough to cover the worse case startup time.

 ```bash
 [...]
containers:
livenessProbe:
 httpGet:
  path: /
  port: 80
 periodSeconds: 10
 successThreshold: 1
 failureThreshold: 50
 [...]
 ```

## 4 Kubernetes secrets
### 4.1 Create secret
In this example we are going to create a secret for the X-Road Security Server Sidecar environment variables with sensitive data.
Create a manifest file called for example "secret-env-variables.yaml" and fill it with the desired values of the environment variables.
- replace <namespace_name> with the name of the namespace if it's different from `default`. If we want to use `default` namespace, we can delete the line.
```bash
apiVersion: v1
kind: Secret
metadata:
  name: secret-sidecar-variables
  namespace: <namespace_name>
type: Opaque
stringData:
  XROAD_TOKEN_PIN: "1234"
  XROAD_ADMIN_USER: "xrd"
  XROAD_ADMIN_PASSWORD: "secret"
  XROAD_DB_HOST: "<db_host>"
  XROAD_DB_PWD: "<db_password>"
  XROAD_DB_PORT: "5432"
  XROAD_LOG_LEVEL: "INFO"
```
Apply the manifest:
```bash
$ kubectl apply -f secret-env-variables.yaml
```

### 4.2 Consume secret
Modify your deployment pod definition in each container that you wish to consume the secret. The key from the Secret becomes the environment variable name in the Pod:
```bash
[...]
containers:
 - name: security-server-sidecar
   image: niis/xroad-security-server-sidecar:latest
   imagePullPolicy: "Always"
   envFrom:
   - secretRef:
     name: secret-sidecar-variables
[...]
```
