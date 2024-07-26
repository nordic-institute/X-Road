# Kubernetes Security Server Sidecar User Guide <!-- omit in toc -->

Version: 1.11  
Doc. ID: UG-K-SS-SIDECAR

## Version history <!-- omit in toc -->

| Date       | Version | Description                                           | Author                    |
|------------|---------|-------------------------------------------------------|---------------------------|
| 05.01.2021 | 1.0     | Initial version                                       | Alberto Fernandez Lorenzo |
| 08.03.2021 | 1.1     | Add Horizontal Pod Autoscaler                         | Alberto Fernandez Lorenzo |
| 11.03.2021 | 1.2     | Add setup examples                                    | Alberto Fernandez Lorenzo |
| 15.03.2021 | 1.3     | Add IP address options                                | Alberto Fernandez Lorenzo |
| 22.03.2021 | 1.4     | Add Load Balancer setup example                       | Alberto Fernandez Lorenzo |
| 16.11.2021 | 1.5     | Update documentation for Sidecar 7.0                  | Jarkko Hyöty              |
| 11.10.2022 | 1.6     | Minor documentation updates regarding upgrade process | Monika Liutkute           |
| 06.07.2023 | 1.7     | Sidecar repo migration                                | Eneli Reimets             |
| 10.08.2023 | 1.8     | Typo error fixes in yml scripts                       | Eneli Reimets             |
| 02.04.2024 | 1.9     | Add Azure Kubernetes Service (AKS) references         | Madis Loitmaa             |
| 13.05.2024 | 1.10    | Add additional upgrade details for Sidecar 7.5        | Ovidijus Narkevicius      |
| 10.07.2024 | 1.11    | Fix incorrect section numbering                       | Petteri Kivimäki          |
## License

This document is licensed under the Creative Commons Attribution-ShareAlike 4.0 International License.
To view a copy of this license, visit <https://creativecommons.org/licenses/by-sa/4.0/>

## Table of Contents

<!-- vim-markdown-toc GFM -->

- [License](#license)
- [Table of Contents](#table-of-contents)
- [1 Introduction](#1-introduction)
  - [1.1 Target Audience](#11-target-audience)
- [2 Deployment Options](#2-deployment-options)
  - [2.1 Single Pod Deployment with internal database](#21-single-pod-deployment-with-internal-database)
  - [2.2 Single Pod Deployment with external database](#22-single-pod-deployment-with-external-database)
  - [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer)
- [3 X-Road Security Server Sidecar images for Kubernetes](#3-x-road-security-server-sidecar-images-for-kubernetes)
- [4 Installation](#4-installation)
  - [4.1 Minimum resource requirements](#41-minimum-resource-requirements)
  - [4.2 Prerequisites to Installation](#42-prerequisites-to-installation)
  - [4.3 Network configuration](#43-network-configuration)
  - [4.4 Reference Data](#44-reference-data)
  - [4.5 Installation Instructions](#45-installation-instructions)
    - [4.5.1 Namespaces](#451-namespaces)
    - [4.5.2 Single Pod deployment](#452-single-pod-deployment)
    - [4.5.3 Kubernetes Volumes](#453-kubernetes-volumes)
    - [4.5.4 Kubernetes Secrets](#454-kubernetes-secrets)
      - [Store keys in Secrets](#store-keys-in-secrets)
      - [Secrets for environmental variables](#secrets-for-environmental-variables)
      - [Consume secrets](#consume-secrets)
    - [4.5.5 Kubernetes readiness, liveness and startup probes](#455-kubernetes-readiness-liveness-and-startup-probes)
      - [Startup and liveness probe](#startup-and-liveness-probe)
      - [Readiness probe](#readiness-probe)
    - [4.5.6 Multiple Pods using a Load Balancer deployment](#456-multiple-pods-using-a-load-balancer-deployment)
      - [Prerequisites](#prerequisites)
      - [Primary Pod installation](#primary-pod-installation)
      - [Secondary Pods installation](#secondary-pods-installation)
    - [4.5.7 Load Balancer address options](#457-load-balancer-address-options)
- [5 Backup and Restore](#5-backup-and-restore)
- [6 Monitoring](#6-monitoring)
- [7 Version update](#7-version-update)
  - [7.1 Upgrading from 6.26.0 to 7.0.0](#71-upgrading-from-6260-to-700)
  - [7.2 Upgrading from version 7.4.2 to 7.5.x with local database](#72-upgrading-from-version-742-to-75x-with-local-database)
- [8 Message log archives](#8-message-log-archives)
- [9 Automatic scaling of the secondary pods](#9-automatic-scaling-of-the-secondary-pods)
- [10 Load Balancer setup example](#10-load-balancer-setup-example)

<!-- vim-markdown-toc -->

## 1 Introduction

### 1.1 Target Audience

This User Guide is meant for X-Road Security Server system administrators responsible for installing and using X-Road Security Server Sidecar in Amazon Elastic Kubernetes Service (Amazon EKS) or Azure Kubernetes Service (AKS) environment.

The document is intended for readers with at least a moderate knowledge of Linux server management, computer networks, Docker, Kubernetes, Amazon EKS, Azure AKS and X-Road.

## 2 Deployment Options

### 2.1 Single Pod Deployment with internal database

The simplest deployment option is to use a single Pod that runs a Security Server Sidecar container with a local database running inside the container.

It's recommended to use this deployment only for testing or developing environments since it does not allow scaling of Nodes or Pods.

### 2.2 Single Pod Deployment with external database

This deployment is the same as the previous deployment except that it uses an external database.

You can find more information about [using an external database on the Security Server Sidecar](security_server_sidecar_user_guide.md#25-using-an-external-database).

### 2.3 Multiple Pods using a Load Balancer

This option enables scaling the number of Nodes and Pods on the cluster. The option includes the following resources:

* *Primary Pod*: Manages the Security Server configuration, message log archiving, and backups. This Pod will be unique per deployment.
* *Secondary Pods*: Process messages, synchronize configuration from the Primary Pod.
* *Headless service*: This will refer to the Primary Pod and will be used so that the Secondary Pods can connect to the Primary.
* *Load Balancer*: Redirects traffic from external Security Servers to the Secondary Pods.
* *External database*: PostgreSQL instance that contains the Security Server configuration, message log, and operational monitoring database.

![Load balancer deployment](img/ig-load_balancer_deploy.svg)

## 3 X-Road Security Server Sidecar images for Kubernetes

All of the X-Road Security Server Sidecar images described in the [Security Server user guide](security_server_sidecar_user_guide.md#11-x-road-security-server-sidecar-images) are suitable to be used for a Kubernetes deployment. Additionally, there are images suitable to be used for a Load Balancer Kubernetes deployment as described in [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer). These images include the necessary configuration so that the Pods can act as Primary or Secondary.

| **Image**                                                               | **Description**                                                                                                                      |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| niis/xroad-security-server-sidecar:\<version>-slim-primary              | Image for the Primary Pod deployment using the slim version of the Security Server Sidecar                                           |
| niis/xroad-security-server-sidecar:\<version>-slim-secondary            | Image for the Secondary Pod deployment using the slim version of the Security Server                                                 |
| niis/xroad-security-server-sidecar:\<version>-primary                   | Image for the Primary Pod deployment using the regular (with message logging and operational monitor) version of the Security Server |
| niis/xroad-security-server-sidecar:\<version>-secondary                 | Image for the Secondary Pod deployment using the regular version of the Security Server.                                             |
| niis/xroad-security-server-sidecar:\<version>-slim-primary-\<variant>   | Image for the Primary Pod deployment using the slim version of the Security Server Sidecar with NIIS member settings                 |
| niis/xroad-security-server-sidecar:\<version>-slim-secondary-\<variant> | Image for the Secondary Pod deployment using the slim version of the Security Server with NIIS member settings                       |
| niis/xroad-security-server-sidecar:\<version>-primary-\<variant>        | Image for the Primary Pod deployment using the regular version of the Security Server with NIIS member settings                      |
| niis/xroad-security-server-sidecar:\<version>-secondary-\<variant>      | Image for the Secondary Pod deployment using the regular version of the Security Server with NIIS member settings                    |

## 4 Installation

### 4.1 Minimum resource requirements

The resource requirements depend on the messaging workload, a minimum for the slim variant is 3 GB of memory and 2 CPUs.

### 4.2 Prerequisites to Installation

In this guide, the `kubectl` command line utility is used. It is expected, that `kubectl` is configured to connect to existing Kubernetes cluster. For the details of setting up Kubernetes cluster and connecting to it with `kubectl`, see:
* [Getting started with Amazon EKS](https://docs.aws.amazon.com/eks/latest/userguide/getting-started.html)
* [Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/)
* [kubectl reference](https://kubernetes.io/docs/reference/kubectl/)

### 4.3 Network configuration

The table below lists the required connections between different components.

| Connection | Source                      | Target                      | Target Ports     | Protocol | Note                          |
|------------|-----------------------------|-----------------------------|------------------|----------|-------------------------------|
| Inbound    | Other Security Servers      | Sidecar                     | 5500, 5577       | tcp      |                               |
| Inbound    | Consumer Information System | Sidecar                     | 8080, 8443       | tcp      | From "internal" network       |
| Inbound    | Admin                       | Sidecar                     | 4000             | https    | From "internal" network       |
| Outbound   | Sidecar                     | Central Server              | 80, 4001         | http(s)  |                               |
| Outbound   | Sidecar                     | OCSP Service                | 80 / 443 / other | http(s)  |                               |
| Outbound   | Sidecar                     | Timestamping Service        | 80 / 443 / other | http(s)  | Not used by *slim*            |
| Outbound   | Sidecar                     | Other Security Server(s)    | 5500, 5577       | tcp      |                               |
| Outbound   | Sidecar                     | Producer Information System | 80, 443, other   | http(s)  | To "internal" network         |
| Inbound    | Sidecar (secondary)         | Sidecar (primary)           | 22               | ssh      | Configuration synchronization |

### 4.4 Reference Data

This is an extension of the Security Server Sidecar [Reference Data](security_server_sidecar_user_guide.md#22-reference-data)

| **Ref** | **Value**               | **Explanation**                                                                                                                                                                                                      |
|---------|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 3.1     | \<namespace name>       | Name of the Kubernetes namespace for provisioning the set of Kubernetes objects inside the cluster.                                                                                                                  |
| 3.2     | \<pod name>             | Unique name that identifies a Pod inside a Cluster namespace. If the Pod belongs to a deployment object a unique alphanumeric code will be concatenated to distinguish it from the other pods inside the deployment. |
| 3.3     | \<pod label>            | Label that identifies a set of objects. This is used, for example, so that a Load Balancer can know to which Pods it has to redirect.                                                                                |
| 3.4     | \<pvc name>             | Unique name that identifies the PersistentVolumeClaim inside a Cluster namespace.                                                                                                                                    |
| 3.5     | \<container name>       | Name of the image container deployed in a Kubernetes pod.                                                                                                                                                            |
| 3.6     | \<manifest volume name> | Unique name that identifies a volume inside a manifest.                                                                                                                                                              |
| 3.7     | \<secret name>          | Unique name that identifies a secret inside a Cluster namespace.                                                                                                                                                     |
| 3.8     | \<service name>         | Unique name that identifies a Kubernetes Service object                                                                                                                                                              |
| 3.9     | \<number replicas>      | Number of Pod replicas to be deployed.                                                                                                                                                                               |
| 3.10    | \<service selector>     | Name that identifies a Load Balancer with the Pods.                                                                                                                                                                  |
| 3.11    | \<primary DNS>          | DNS of the service that identifies the Primary Pod composed by \<service name>.\<namespace name>.svc.cluster.local .                                                                                                 |
| 3.12    | \<cluster name>         | Name of the Kubernetes cluster.                                                                                                                                                                                         |
| 3.13    | \<cluster region>       | Region where the Amazon EKS cluster is deployed.                                                                                                                                                                        |

### 4.5 Installation Instructions

#### 4.5.1 Namespaces

It's recommended to use namespaces in a Kubernetes deployment since namespaces will allow you to organize the resources of a shared cluster better. The use of a namespace for the Security Server Sidecar resources is optional. If no namespace is created, they will be included in the "default" namespace.

Create a new namespace by running (**Reference Data: 3.1**):

```bash
kubectl create namespace <namespace name>
```

#### 4.5.2 Single Pod deployment

For installing the scenario described in [2.1 Single Pod Deployment with internal database](#21-single-pod-deployment-with-internal-database) it is possible to use the following `yaml` manifest (**Reference Data: 3.1, 3.2, 3.5, 1.4, 1.5, 1.6, 1.10**):

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: <pod-name>
  namespace: <namespace name>
spec:
  containers:
  - name: <container name>
    image: niis/xroad-security-server-sidecar:<image tag>
    imagePullPolicy: "Always"
    env:
    - name: XROAD_TOKEN_PIN
      value: "<token pin>"
    - name: XROAD_ADMIN_USER
      value: "<admin user>"
    - name: XROAD_ADMIN_PASSWORD
      value: "<admin password>"
    - name: XROAD_DB_HOST
      value: "127.0.0.1"
    - name: XROAD_LOG_LEVEL
      value: "<xroad log level>"
    ports:
    - containerPort: 8443
    - containerPort: 4000
    - containerPort: 5500
    - containerPort: 5577
```

Any of the Security Server Sidecar images described in the [Security Server Sidecar user guide](security_server_sidecar_user_guide.md#11-x-road-security-server-sidecar-images) can be used as image tag.
Optionally, you can use an external database by adding the following environment variables of the deployment (**Reference Data: 1.7, 1.8, 1.9, 1.11**):

```yaml
    - name: XROAD_DB_HOST
      value: "<database host>"
    - name: XROAD_DB_PORT
      value: "<database port>"
    - name: XROAD_DB_PWD
      value: "<xroad db password>"
    - name: XROAD_DATABASE_NAME
      value: "<database name>"
```

Once the deployment is ready save it on a file and run:

```bash
kubectl apply -f /path/to/manifest-file-name.yaml
```

Check that the Pod is deployed by running (**Reference Data: 3.1**):

```bash
kubectl get pods -n <namespace name>
```

Get the Pod information by running (**Reference Data: 3.1, 3.2**):

```bash
kubectl describe pod -n <namespace name> <pod name>
```

Get a shell to the container running in the Pod by running (**Reference Data: 3.1, 3.2**):

```bash
kubectl exec -it -n <namespace name> <pod name> -- bash
```

Delete the Pod by running:

```bash
kubectl delete -f /path/to/manifest-file-name.yaml
```

#### 4.5.3 Kubernetes Volumes

Kubernetes has multiple types of persistent volumes. For the purposes of this guide, [AWS EBS](https://github.com/kubernetes-sigs/aws-ebs-csi-driver) or [Azure Disk](https://github.com/kubernetes-sigs/azuredisk-csi-driver) storage driver with dynamic provisioning is assumed. For more information see:
- [Kubernetes storage documentation](https://kubernetes.io/docs/concepts/storage/volumes/#volume-types)
- [Amazon EKS Storage](https://docs.aws.amazon.com/eks/latest/userguide/storage.html)
- [AKS Storage](https://learn.microsoft.com/en-us/azure/aks/concepts-storage#volumes)

It is recommended to configure persistent volumes for the files in the following locations:

| Mount point                 | Description                                                                        |
|-----------------------------|------------------------------------------------------------------------------------|
| /etc/xroad                  | X-Road configuration                                                               |
| /var/lib/xroad              | Backups and messagelog archives                                                    |
| /var/lib/postgresql/16/main | Local database files (not applicable to load balancer or external DB configuration |

#### 4.5.4 Kubernetes Secrets

[Kubernetes Secrets](https://kubernetes.io/docs/concepts/configuration/secret/) allows you to store and manage sensitive information.

For example the following configuration could be stored as a Kubernetes secret:

* SSH keys for the load balancer configuration synchronization
* Sensitive Sidecar environment variables:
  * Software token PIN code:
    * `XROAD_TOKEN_PIN`
  * Security server GUI admin user:
    * `XROAD_ADMIN_USER`
    * `XROAD_ADMIN_PASSWORD`
  * Database master password (required if the Sidecar initializes the database):
    * `XROAD_DB_PWD`

##### Store keys in Secrets

For the [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer) scenario you need to create a Kubernetes secret, this secret will store the SSH keys used by the Secondary Pods to synchronize the configuration with the Primary Pod.

If you don't have an SSH key you can create one by running:

```bash
ssh-keygen -f /path/to/.ssh/id_rsa
```

Then create a Kubernetes Secret for storing the SSH keys by running (**Reference Data: 3.1, 3.7**):

```bash
kubectl create secret generic <secret name> --from-file=private-key=/path/to/.ssh/id_rsa --from-file=public-key=/path/to/.ssh/id_rsa.pub --namespace=<namespace name>
```

##### Secrets for environmental variables

This example shows how to create a secret for the Security Server Sidecar environment variables with sensitive data.

1. Create a manifest file called for example 'secret-env-variables.yaml' and fill it with the desired values of the environment variables ( **Reference Data: 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 3.1**):

    ```yaml
    apiVersion: v1
    kind: Secret
    metadata:
      name: secret-sidecar-variables
      namespace: <namespace_name>
    type: Opaque
    stringData:
      XROAD_TOKEN_PIN: "<token pin>"
      XROAD_ADMIN_USER: "<admin user>"
      XROAD_ADMIN_PASSWORD: "<admin password>"
      XROAD_DB_PWD: "<database password>"
    ```

2. Apply the manifest:

    ```bash
    kubectl apply -f secret-env-variables.yaml
    ```

##### Consume secrets

The Secrets that store keys can be consumed in a similar way to volumes. To do this, you will have to include the Secret in the definition of volumes within the Pod deployment manifest, select the key and assign permissions to it, then mount the volume in a folder on the container (**Reference Data: 3.6, 3.7**):

```yaml
[...]
volumes:
- name: <manifest volume name>
  secret:
    secretName: <secret name>
    items:
    - key: public-key
      path: id_rsa.pub
      mode: 0644
[...]
  volumeMounts:
  - name: <manifest volume name>
    mountPath: "/etc/.ssh/"
[...]
```

For consuming the Secrets for environmental variables, modify the deployment Pod definition in each container that needs to consume the secret. The key from the Secret becomes the environment variable name in the Pod:

```yaml
[...]
containers:
- name: security-server-sidecar
  image: niis/xroad-security-server-sidecar:<image tag>
  imagePullPolicy: "Always"
  envFrom:
  - secretRef:
    name: secret-sidecar-variables
[...]
```

#### 4.5.5 Kubernetes readiness, liveness and startup probes

See [Kubernetes documentation](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/) for details.

##### Startup and liveness probe

The X-Road proxy consumer interface can be used for checking that the server is alive. Since the startup can take some time, it is recommended to also use a startup probe.

```yaml
  startupProbe:
    httpGet:
      path: /
      port: 8080
    periodSeconds: 10
    failureThreshold: 60

  livenessProbe:
    httpGet:
      path: /
      port: 8080
    periodSeconds: 10
    successThreshold: 1
    failureThreshold: 5
```

##### Readiness probe

Readiness probe on the Security Server health check interface is useful for clustered Security Server secondary containers.

```yaml
  readinessProbe:
    httpGet:
      path: /
      port: 5588
    periodSeconds: 10
    timeoutSeconds: 6
    failureThreshold: 1
```

#### 4.5.6 Multiple Pods using a Load Balancer deployment

##### Prerequisites

* A Persistent Volume Claim is bound to a Persistent Volume to store the Primary Pod configuration [4.5.3 Kubernetes Volumes](#453-Kubernetes-volumes).
* A Kubernetes Secret with an SSH key pair is stored [4.5.4 Kubernetes Secrets](#454-Kubernetes-secrets).

##### Primary Pod installation

An example of how to install the Primary Pod is shown in the manifest below (**Reference Data: 3.1, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.10 1.4, 1.5, 1.6, 1.10**):

```yaml
apiVersion: v1
kind: Service
metadata:
  name: <service name>
  namespace: <namespace name>
spec:
  clusterIP: None
  selector:
    app: <service selector>
---
apiVersion: v1
kind: Pod
metadata:
  name: <pod name>
  namespace: <namespace name>
  labels:
    run: <service selector>
spec:
  volumes:
  - name: <manifest volume name>
    persistentVolumeClaim:
      claimName: <pvc name>
  - name: <manifest volume name_2>
    secret:
      secretName: <secret name>
      items:
      - key: public-key
        path: id_rsa.pub
        mode: 0644
  containers:
  - name: <container name>
    image: niis/xroad-security-server-sidecar:<image tag>
    imagePullPolicy: "Always"
    volumeMounts:
    - name: <manifest volume name>
      mountPath: /etc/xroad/
    - name: <manifest volume name_2>
      mountPath: "/etc/.ssh/"
    env:
    - name: XROAD_TOKEN_PIN
      value: "<token pin>"
    - name: XROAD_ADMIN_USER
      value: "<admin user>"
    - name: XROAD_ADMIN_PASSWORD
      value: "<admin password>"
    - name: XROAD_LOG_LEVEL
      value: "<xroad log level>"
    - name: XROAD_DB_HOST
      value: "<database host>"
    - name: XROAD_DB_PORT
      value: "<database port>"
    - name: XROAD_DB_PWD
      value: "<xroad db password>"
    - name: XROAD_DATABASE_NAME
      value: "<database name>"
  startupProbe:
    httpGet:
      path: /
      port: 8080
    periodSeconds: 10
    failureThreshold: 60
  livenessProbe:
    httpGet:
      path: /
      port: 8080
    periodSeconds: 10
    successThreshold: 1
    failureThreshold: 5
    ports:
    - containerPort: 4000
    - containerPort: 5588
    - containerPort: 22
```

The manifest has two Kubernetes objects:

* A Headless Service, this service is used so that the secondary pods can connect to the primary one via SSH using a fixed Kubernetes cluster DNS name.
* A Pod with the primary image of the Security Server Sidecar, as image tag you can choose between the "primary" or "primary-slim" described in [3 X-Road Security Server Sidecar images for Kubernetes](#3-x-road-security-server-sidecar-images-for-Kubernetes).
The Pod defines two volumes: one volume to store the secret public key described in [4.5.4 Kubernetes Secrets](#454-Kubernetes-secrets), and a second volume to store the `/etc/xroad` configuration.

Once the Primary Pod is deployed, you need to configure it (complete initial configuration, create the certificates, register in the Central Server) following the [User Guide](security_server_sidecar_user_guide.md#3-initial-configuration).

Once the configuration is completed, verify the installation by running a healthcheck to the Pod running the Security Server Sidecar container from the internal network and check that the result is OK:

```bash
curl -i <private pod ip>:5588
```

##### Secondary Pods installation

An example of how to install the Secondary Pod is shown in the manifest below (**Reference Data: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 1.4, 1.5, 1.6, 1.10**). The example uses Kubernetes Service with `LoadBalancer` type (https://kubernetes.io/docs/concepts/services-networking/service/#loadbalancer). Cloud providers may require additional deployment and configuration for `LoadBalancer` Service type. For more details see:
-  [AWS Load Balancer Controller](https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html)
-  [Use a public standard load balancer in Azure Kubernetes Service (AKS)](https://learn.microsoft.com/en-us/azure/aks/load-balancer-standard)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: <service name>
  namespace: <namespace name>
spec:
  type: LoadBalancer
  selector:
    run: <service selector>
  ports:
  - port: 5500
    targetPort: 5500
    protocol: TCP
    name: xroad-message-transport
  - port: 5577
    targetPort: 5577
    protocol: HTTP
    name: xroad-ocsp
---
apiVersion: v1
kind: Service
metadata:
  name: <service name>-consumer
  namespace: <namespace name>
spec:
  selector:
    run: <service selector>
  ports:
  - port: 8443
    targetPort: 8443
    protocol: TCP
    name: xroad-message-protocol
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: <pod name>
  namespace: <namespace name>
spec:
  selector:
    matchLabels:
      run: <service selector>
  replicas: <number replicas>
  template:
    metadata:
      labels:
        run: <pod name>
        role: secondary
    spec:
      volumes:
      - name: <manifest volume name>
        secret:
          secretName: <secret name>
          items:
          - key: private-key
            path: id_rsa
            mode: 0644
          - key: public-key
            path: id_rsa.pub
            mode: 0644
      containers:
      - name: <container name>
        image: niis/xroad-security-server-sidecar:<image tag>
        imagePullPolicy: "Always"
        volumeMounts:
        - name: <manifest volume name>
          mountPath: "/etc/.ssh/"
        env:
        - name: XROAD_TOKEN_PIN
          value: "<token pin>"
        - name: XROAD_ADMIN_USER
          value: "<admin user>"
        - name: XROAD_ADMIN_PASSWORD
          value: "<admin password>"
        - name: XROAD_LOG_LEVEL
          value: "<xroad log level>"
        - name: XROAD_PRIMARY_DNS
          value: "<primary DNS>"
        startupProbe:
          httpGet:
            path: /
            port: 8080
          periodSeconds: 10
          failureThreshold: 30
        readinessProbe:
          httpGet:
            path: /
            port: 5588
          periodSeconds: 10
          timeoutSeconds: 6
          successThreshold: 1
          failureThreshold: 1
        ports:
        - containerPort: 5500
        - containerPort: 5577
        - containerPort: 5588
```

The manifest has two Kubernetes objects:

* A `LoadBalancer` type Service which will be in charge of redirecting the traffic to the secondary pods. It has the required ports "5500" and "5577" for receiving messages from other Security Servers.
* An internal Service for the consumer information systems that proxies requests to the secondary pods.
* A Deployment for the secondary pods. As image tag, you can choose between the "secondary" or "secondary-slim" described in [3 X-Road Security Server Sidecar images for Kubernetes](#3-x-road-security-server-sidecar-images-for-Kubernetes).

The pods have a secrets volume for the public and private SSH keys which are required for the synchronization with the primary pod via SSH.

The secondary pods also have a readiness probe, this test will run a healthcheck every 10 seconds. As long as the healthcheck result is not positive, the Pod status will remain in "NotReady" and will not be included in the Load Balancer Service.

After the manifest is deployed, you can scale the secondary pods by running (**Reference Data: 3.1, 3.9**):

```bash
kubectl scale -n <namespace name> --replicas=<number replicas> deployment/<pod name>
```

The Secondary Pods will synchronize the configuration at initialization and through a cron job that runs every minute. Once the configuration is synchronized, the secondary Pods can process the messages independently of the primary one. This means that if the primary Pods crashes, the cron job that synchronizes the configuration will fail but the Secondary Pods can continue to process the messages.

#### 4.5.7 Load Balancer address options

In the described scenario [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer) the messages will be sent to the Security Server secondary pods through the Load Balancer. Therefore, the address of the Load Balancer needs to be provided as the Security Server address when registering the Security Server to the X-Road instance. As the global configuration supports only one address for a Security Server, but a load balancer typically has multiple, a DNS name needs to be used. There are multiple ways of implementing a stable DNS name for the load balancer:

* Deploy the Load Balancer Service separately and create a CNAME (or alias) DNS record for the load balancer.
* Use cloud provider specific annotations for Load Balancer Service to specify IP address and DNS name
  * [AWS load balancer controller annotations](https://kubernetes-sigs.github.io/aws-load-balancer-controller/v2.7/guide/ingress/annotations/)
  * [Azure AKS LoadBalancer annotations](https://cloud-provider-azure.sigs.k8s.io/topics/loadbalancer/#loadbalancer-annotations)
* Automate DNS record assignment, e.g. by using [Kubernetes External DNS](https://github.com/kubernetes-sigs/external-dns/).

## 5 Backup and Restore

The backup system of the Security Servers described in the [User Guide](../Manuals/ug-ss_x-road_6_security_server_user_guide.md#13-back-up-and-restore) is also valid for the installation using Kubernetes. If your Kubernetes deployment uses volumes to store the configuration, you can additionally back up each volume using e.g. volume snapshots.

## 6 Monitoring

**Amazon CloudWatch** monitors the Amazon Web Services (AWS) resources and the applications that run on AWS in real time. CloudWatch can be used to collect and track metrics, which are variables that can be uses to measure resources and applications. For more information about CloudWatch check the [Amazon CloudWatch documentation](https://docs.aws.amazon.com/cloudwatch/index.html).

**CloudWatch container insights** is a tool available for Amazon EKS that can be used to collect, aggregate, and summarize metrics and logs from containerized applications and microservices. See [Setting up Container Insights on Amazon EKS and Kubernetes](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/deploy-container-insights-EKS.html) for more details.

**Azure Monitor** is a monitoring solution for cloud and on-premises environments. See [AKS documentation](https://learn.microsoft.com/en-us/azure/aks/monitor-aks) for details on integration with Azure Monitor.

**Fluentd** is an open-source data collector that can be set up on Kubernetes nodes to tail container log files, filter and transform the log data, and deliver it to the Elasticsearch cluster, where it will be indexed and stored. See [Fluentd documentation](https://docs.fluentd.org/container-deployment/kubernetes) for details.

## 7 Version update

Upgrading to a new Sidecar container image is supported, provided that:

* The new container image has the same or subsequent minor version of the X-Road Security Server.
  As an exception, upgrading from 6.26.0 to 7.0.x is supported despite the major version change.
* A volume is used for `/etc/xroad`.
* An external database is used (or a volume is mapped to `/var/lib/postgresql/16/main`).
* The `xroad.properties` file with `serverconf.database.admin_user` etc. credentials is either mapped to `/etc/xroad.properties` or present in `/etc/xroad/xroad.properties`.
* The same image type (slim or full) and variant (ee, fi, ...) are used for the new container.
* If remote database is used, then upgrade it up to PostgreSQL 16 version when upgrading to 7.5.x.

To update the version of the Security Server Sidecar, re-deploy the Pod with a newer version of the Sidecar container image. In case of the scenario [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer), it is possible to do a rolling upgrade if there are no changes to the database schema. In the case of database schema changes, one needs to take the cluster off-line (scale the secondary replica set to zero), upgrade the primary, and then upgrade (and scale up) the secondaries.

### 7.1 Upgrading from 6.26.0 to 7.0.0

Upgrading from 6.26.0 to 7.0.0 is supported, if the above prerequisites are met. However, due to a problem in 6.26.0 installer scripts, 
it is necessary to verify that the `/etc/xroad.properties` file containing database admin credentials that are needed during schema migrations  
has been correctly populated (see [IG-SS, Annex D](../Manuals/ig-ss_x-road_v6_security_server_installation_guide.md#annex-d-create-database-structure-manually) 
for details describing expected file content and manual creation instructions).
Backups are not compatible between 6.26.0 and 7.0.0, so upgrading using a backup is not possible.

In addition, unless `/etc/xroad.properties` is mounted as secrets file, copy it into the `/etc/xroad` volume before upgrading to 7.0.0:
```
kubectl exec -n <namespace> <sidecar-pod-name> -- cp /etc/xroad.properties /etc/xroad/
```

### 7.2 Upgrading from version 7.4.2 to 7.5.x with local database

Upgrading from 7.4.2 to 7.5.x is supported, if the above prerequisites are met.
However, due to different versions of PostgreSQL (current 16, previously 12), it isn't straightforward to upgrade using the same database volume.

Safest way to upgrade is to create a new database volume and restore X-Road instance from a backup. More information about backup and restore flows can be [User Guide](../Manuals/ug-ss_x-road_6_security_server_user_guide.md#13-back-up-and-restore)

**Note:** Version 7.0.0 introduces changes to the database schemas, so a rolling upgrade in a load balancer scenario is not possible.

## 8 Message log archives

**Note:** Does not apply to slim containers and secondary Pods.

As described in the [Security Server Sidecar User Guide](security_server_sidecar_user_guide.md#29-message-log-archives) it is recommended to use a persistent volume for the message log archives. Note that in the load balancer setup, only the primary node performs archiving.

## 9 Automatic scaling of the secondary pods

It is possible to automatically scale the Sidecar secondary Pods using e.g. [Kubernetes Horizontal Pod Autoscaler](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/). A scaling policy should take into account that the Sidecar container is relatively heavyweight, and start-up and warm-up (achieving full performance) takes some time. Overall, scaling is a complex topic and out of the scope of this guide.

## 10 Load Balancer setup example

The [load_balancer_setup manifest template](files/load_balancer_setup.yaml) contains all the necessary Kubernetes objects to set up the deployment scenario in [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer). The namespace where the objects are deployed is named `sidecar`.

1. Download the file and search for the following variables and replace it with our desired values:

    * \<public key base64> Public key encoded in base64 (`base64 -w0 path/to/id_rsa.pub`).
    * \<private key base64> Private key encoded in base64 (`base64 -w0 path/to/id_rsa`).
    * \<token pin> (**Reference Data: 1.4**)
    * \<admin user> (**Reference Data: 1.5**)
    * \<admin password> (**Reference Data: 1.6**)
    * \<database host> (**Reference Data: 1.7**)
    * \<database password> (**Reference Data: 1.9**)
    * \<database port> (**Reference Data: 1.8**)
    * \<xroad log level> (**Reference Data: 1.10**)
    * \<xroad database name> (**Reference Data: 1.11**)
    * \<version primary>, (`7.0.0-primary[-slim][-variant]`)
    * \<version secondary>, (`7.0.0-secondary[-slim][-variant]`)

2. Once the values are replaced, apply the manifest file:
    ```bash
    kubectl apply -f load_balancer_setup.yaml
    ```

3. Verify that the PersistentVolumeClaim is deployed and bounded:
    ``` bash
    kubectl get pvc -n sidecar
    NAME                 STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
    pvc-config-sidecar   Bound    pvc-2420bb12-08cf-4d1b-82fc-976a0e4dd838   2Gi        RWO            gp2            5m18s
    ```

4. Verify that the secrets are deployed:
    ```bash
    kubectl get secrets -n sidecar

    NAME                       TYPE                                  DATA   AGE
    default-token-zgl8g        kubernetes.io/service-account-token   3      6m28s
    secret-sidecar-variables   Opaque                                8      6m27s
    secret-ssh-keys            Opaque                                2      6m28s
    ```

5. Verify that the services are created:
    ```bash
    kubectl get services -n sidecar

    NAME                                      TYPE           CLUSTER-IP       EXTERNAL-IP                         PORT(S)                        AGE
    balancer-security-server-sidecar          LoadBalancer   10.100.217.185   abc.....elb.amazonaws.com           5500:31086/TCP,5577:30502/TCP  7m37s
    service-security-server-sidecar-primary   ClusterIP      None             <none>                              <none>                         7m37s
    ```

6. Verify that the Primary and Secondary Pods are deployed. The Secondary Pod should remain in the "Not Ready" state until the Primary Pod is configured. If we are using a volume that already has the Primary Pod configuration, the Secondary Pod should switch to the "Ready" state after approximately 3-4 minutes.
    ```bash
    kubectl get pods -n sidecar

    NAME                                                 READY   STATUS    RESTARTS   AGE
    security-server-sidecar-primary                      1/1     Running   0          8m35s
    security-server-sidecar-secondary-7c844c6b5f-ntkx4   1/1     Running   0          8m34s
    ```

7. Delete all the objects by running:
    ```bash
    kubectl delete -f load_balancer_setup.yaml
    ```
