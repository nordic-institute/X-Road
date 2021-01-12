# Kubernetes Security Server Sidecar User Guide <!-- omit in toc -->

## Version history <!-- omit in toc -->

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 05.01.2021 | 1.0     | Initial version                                                 | Alberto Fernandez Lorenzo

# Table of Contents
* [1 Introduction](#1-introduction)
   * [1.1 Target Audience](#11-target-audience)
* [2 Deployment Options](#2-deployment-options)
   * [2.1 Single Pod Deployment with internal database](#21-single-pod-deployment-with-internal-database)
   * [2.2 Single Pod Deployment with external database](#22-single-pod-deployment-with-external-database)
   * [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer)
* [3 X-Road Security Server Sidecar images for Kubernetes](#3-x-road-security-server-sidecar-images-for-kubernetes)
* [4 Installation](#4-installation)
   * [4.1 Minimum system requirements](#41-minimum-system-requirements)
   * [4.2 Prerequisites to Installation](#42-prerequisites-to-installation)
   * [4.3 Network configuration](#43-network-configuration)
   * [4.4 Reference Data](#44-reference-data)
   * [4.2 Installation Instructions](#42-installation-instructions)
      * [4.2.1 Namespaces](#421-namespaces)
      * [4.2.2 Single Pod deployment](#422-single-pod-deployment)
      * [4.2.3 Kubernetes Volumes](#423-kubernetes-volumes)
         * [4.2.3.1 Persistent Volume Claim](#4231-persistent-volume-claim)
         * [4.2.3.2 Persistent Volume hostPath](#4232-persistent-volume-hostpath)
         * [4.2.3.3 Persistent Volume awsElasticBlockStore](#4233-persistent-volume-awselasticblockstore)
         * [4.2.3.4 Persistent Volume AWS Elastic File System](#4234-persistent-volume-aws-elastic-file-system)
         * [4.2.3.5 AWS EBS vs AWS EFS](#4235-aws-ebs-vs-aws-efs)
         * [4.2.3.6 Manage Volumes](#4236-manage-volumes)
         * [4.2.3.7 Mount the Volume to a Pod](#4237-mount-the-volume-to-a-pod)
      * [4.2.4 Kubernetes Secrets](#424-kubernetes-secrets)
         * [4.2.4.1 Secrets for environmental variables](#4241-secrets-for-environmental-variables)
         * [4.2.4.2 Consume secret for environmental variables](#4242-consume-secret-for-environmental-variables)
      * [4.2.5 Kubernetes jobs readiness, liveness and startup probes](#425-kubernetes-jobs-readiness-liveness-and-startup-probes)
         * [4.2.5.1 Readiness probes](#4251-readiness-probes)
         * [4.2.5.2 Liveness probes](#4252-liveness-probes)
         * [4.2.5.3 Startup probes](#4253-startup-probes)
      * [4.2.6 Multiple Pods using a Load Balancer deployment](#426-multiple-pods-using-a-load-balancer-deployment)
         * [4.2.6.1 Prerequisites](#4261-prerequisites)
         * [4.2.6.2 Primary Pod installation](#4262-primary-pod-installation)
         * [4.2.6.3 Secondary Pods installation](#4263-secondary-pods-installation)
* [5 Backup and Restore](#5-backup-and-restore)
* [6 Monitoring](#6-monitoring)
   * [6.1 Setup Container Insights on AWS EKS](#61-setup-container-insights-on-aws-eks)
* [7 Version update](#7-version-update)
* [8 Message logs &amp; disk space](#8-message-logs--disk-space)


# 1 Introduction
## 1.1 Target Audience
The intended audience of this User Guide are X-Road Security Server system administrators responsible for installing and using X-Road Security Server Sidecar in AWS EKS environment.

The document is intended for readers with a moderate knowledge of Linux server management, computer networks, Docker, Kubernetes, AWS EKS and X-Road.

# 2 Deployment Options
## 2.1 Single Pod Deployment with internal database
The simplest deployment option is to use a single Pod that runs a Security Server Sidecar container with a local database running inside the container.

<p align="center">
  <img src="img/ig-single_pod_local_database.svg" />
</p>

With this deployment, the Pod will be assigned a private IP and will only be accessible from within the private network. It's possible to assign a public IP to the Pod by deploying a Service who references the Pod.
This deployment is only recommended for testing or developing environment since it does not allow scaling of nodes or pods.

## 2.2 Single Pod Deployment with external database
This deployment is equal to the [2.1](# 2.1-Single-Pod-Deployment-with-internal-database) but using an external database. The database could be outside the VPC with allowed access from the Pod, could be in the same VPC or even in the same cluster deployed on another pod.

<p align="center">
  <img src="img/ig-single_pod_external_database.svg" />
</p>

More information about using a external database on the Security Server Sidecar can be found [here](https://github.com/nordic-institute/X-Road-Security-Server-sidecar/blob/master/doc/security_server_sidecar_user_guide.md#27-external-database)

## 2.3 Multiple Pods using a Load Balancer
This deployment will allow us to scale the number of Nodes and Pods that we have on our deployment. All of the Pods will point to the same external database.
Within this deployment we will have 4 types of objects.
- Primary Pod: This Pod will be in charge of handling the configuration of the Security Server Sidecar Database, the storage and backups of the message logs and the configuration backups. This Pod will be unique per deployment.
- "n" number of Secondary Pods: These pods will be in charge of processing the messages. These Pods will not change the configuration, instead they will synchronize the configuration of the Primary Pod via SSH at initialization and in a CRON job running each minute.
- Headless service: It will refer to the Primary Pod and will be used so that the secondary pods can connect to the primary one through a fixed DNS.
- Network Load Balancer: It will redirect the traffic between the Secondary Pods. The users will sends the messages through this Load Balancer public IP (We can use the private IP in case we are on the same VPC).

<p align="center">
  <img src="img/ig-load_balancer_deploy.svg" />
</p>

This deployment is the recommended for production environment.

# 3 X-Road Security Server Sidecar images for Kubernetes
All of the X-Road Security Server Sidecar images described in the [Security Server user guide](https://github.com/nordic-institute/X-Road-Security-Server-sidecar/blob/master/doc/security_server_sidecar_user_guide.md#22-x-road-security-server-sidecar-images) are available to use in a Kubernetes deployment. Apart for this images we include the following images to be use in the [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer) deployment. These images include the necessary configuration so that the pods can act as primary or secondary and connect via ssh.

**Image**                                                | **Description**                               
------------------------------------------------------------ | -----------------------------------------------------------------------------------------------------------------
niis/xroad-security-server-sidecar:&lt;version&gt;-slim-primary      | Image for the Primary Pod deployment using the slim version of the Security Server Sidecar
niis/xroad-security-server-sidecar:&lt;version&gt;-slim-secondary    | Image for the Secondary Pod deployment using the slim version of the Security Server
niis/xroad-security-server-sidecar:&lt;version&gt;-primary          | Image for the Primary Pod deployment using the regular (with message logging and operational monitor) version of the Security Server
niis/xroad-security-server-sidecar:&lt;version&gt;-secondary        | Image for the Secondary Pod deployment using the regular (with message logging and operational monitor) version of the Security Server.



# 4 Installation
## 4.1 Minimum system requirements
- An AWS EKS cluster with at least one linux EC2 instance. The instance type will depend on the amount of resources required. For example an instance of type "t3.medium" could support approximately 5 Security Server Sidecar Pods running simultaneously.
## 4.2 Prerequisites to Installation
- The latest AWS CLI and eksctl command line utility must be installed to go through the steps described in this [page](https://docs.aws.amazon.com/eks/latest/userguide/getting-started-eksctl.html).
- We must be authenticated to access to the AWS resources through the AWS CLI. Instructions for authenticated can be found [here](https://aws.amazon.com/premiumsupport/knowledge-center/authenticate-mfa-cli/).
- A ssh key must be uploaded to "Key Pairs" section in Amazon EC2, Instruction for uploading a key can be found [here](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html#prepare-key-pair).

## 4.3 Network configuration

The table below lists the required connections between different components.

**Connection Type** | **Source** | **Target** | **Target Ports** | **Protocol** | **Note** |
-----------|------------|-----------|-----------|-----------|-----------|
Out | Security Server | Central Server | 80, 4001 | tcp | |
Out | Security Server | Management Security Server | 5500, 5577 | tcp | |
Out | Security Server | OCSP Service | 80 / 443 | tcp | |
Out | Security Server | Timestamping Service | 80 / 443 | tcp | |
Out | Security Server | Data Exchange Partner Security Server (Service Producer) | 5500, 5577 | tcp | |
Out | Security Server | Producer Information System | 80, 443, other | tcp | Target in the internal network |
Out  | SSH synchronization | Security Server | 22 | tcp | |
In  | Monitoring Security Server | Security Server | 5500, 5577 | tcp | |
In  | Data Exchange Partner Security Server (Service Consumer) | Security Server | 5500, 5577 | tcp | |
In | Consumer Information System | Security Server | 80, 443 | tcp | Source in the internal network |
In | Admin | Security Server | <ui port> (**reference data 1.2**) | tcp | Source in the internal network |
In  | Healthcheck | Security Server | 5588 | tcp | |
In  | SSH synchronization | Security Server | 22 | tcp | |


## 4.4 Reference Data

This is an extension of the Security Server Sidecar [Reference Data](https://github.com/nordic-institute/X-Road-Security-Server-sidecar/blob/master/doc/security_server_sidecar_user_guide.md#23-reference-data)

**Ref** | **Value**                                | **Explanation**
------- | ----------------------------------------- | ----------------------------------------------------------
3.1    | &lt;namespace name&gt;                    | Name of the Kubernetes namespace for provisioning the set of Kubernetes objects. inside the cluster
3.2    | &lt;pod name&gt;                          | Unique that identifies a Pod inside a Cluster namespace, if the Pods belongs to a deployment object, a unique alphanumeric code will be concatenated to distinguish it from the other pods inside the deployment.
3.3    | &lt;pod label&gt;                         | Label that identifies a set of objects. This is used, for example, so that a Load Balancer can know to which Pods it has to redirect.
3.4    | &lt;pvc name&gt;                         | Unique name that identifies the PersistentVolumeClaim inside a Cluster namespace.
3.5    | &lt;volume storage class name&gt;        | Name that matches the PVC with the PV for dynamic provisioning.
3.6    | &lt;volume access mode&gt;             | Define de access mode to the volume, typically "ReadWriteOnce" wich allows Read/Writte access to a single Pod at a time.  "ReadWritteMany" could be used for EFS volumes wich allows multiple Pods access at same time.
3.7    | &lt;volume size&gt;                       | Requested volume size, for example: 5Gi
3.8    | &lt;pv name&gt;                           | Unique name that identifies the PersistentVolume.
3.9    | &lt;pv host path&gt;                      | Path to the file or directory to mount in the PersistentVolume.
3.10    | &lt;awsElasticBlockStore volume id&gt;   | Volume ID of a AWS Elastic Block Store volume.
3.11    | &lt;efs volume id&gt;                    | Volume ID of a AWS Elastic File System volume.
3.12    | &lt;container name&gt;                    | Name of the image container deployed in a Kubernetes pod.
3.13    | &lt;<template volume name&gt;  | Unique name that identifies a volume inside a template.
3.14    | &lt;secret name&gt;            | Unique name that identifies a secret inside a Cluster namespace.
3.15    | &lt;service name&gt;           | Unique name that identifies a Kubernetes Service object
3.16    | &lt;pod private ip&gt;           | Private IP of a single Pod.
3.17    | &lt;load balancer private ip&gt;  | Fixed private IP of a Load Balancer, defined on a kubernetes template.
3.18    | &lt;number replicas&gt;           | Number of Pod replicas to be deployed.
3.19    | &lt;service selector&gt;           | Name that identifies a Load Balancer with the Pods.
3.20    | &lt;primary DNS&gt;           | DNS of the service that identifies the Primary Pod composed by <service name>.<namespace name>.svc.cluster.local .
3.21    | &lt;cluster name&gt;           | Name of the AWS EKS cluster.
3.22    | &lt;cluster region&gt;           | Region where is deployed the AWS EKS cluster.
3.23    | &lt;cloudwatch agent name&gt;           | Name of the CloudWatch agent that collects the logs and metrics of our cluster, this name is automatically generated during the CloudWatch. setup.
3.24    | &lt;volume mount path&gt;           | Local path where the volume is mounted on the EC2 instance.
3.25    | &lt;bucket name&gt;           | Name of a AWS S3 bucket.
3.26    | &lt;arn encryption key&gt;           | ARor a encryption key using in an AWS S3 bucket, example: arn:aws:kms:eu-west-1:999999999:alias/aws/s3.


## 4.2 Installation Instructions
### 4.2.1 Namespaces

It is recommended to use namespaces in a deployment, namespaces allow us to organize the resources of a cluster when it is shared by several projects or teams.
The use of namespaces is optional, if the namespace is not used in the different resources they will be included in the "default" namespace.
Create a new namespace by running (**reference data: 3.1**):
```
kubectl create namespace <namespace name>
```

### 4.2.2 Single Pod deployment
For installing the scenario described in [2.1 Single Pod Deployment with internal database](#21-single-pod-deployment-with-internal-database) it is possible to use the following "yaml" template (**reference data: 3.1, 3.2, 3.3, 1.4, 1.5, 1.6, 1.10**):
``` yaml
apiVersion: v1
kind: Pod
metadata:
  name: <pod-name>
  namespace: <namespace name>
  labels:
    run: <pod label>
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
     - containerPort: 443
     - containerPort: 4000
     - containerPort: 5500
     - containerPort: 5577
```
As image tag any of the Security Server Sidecar images described [here](https://github.com/nordic-institute/X-Road-Security-Server-sidecar/blob/master/doc/security_server_sidecar_user_guide.md#22-x-road-security-server-sidecar-images) could be used.
It is possible using an external database by add/modify the environment variables of the deployment (**reference data: 1.7, 1.8, 1.9, 1.11**):

``` yaml
     - name: XROAD_DB_HOST
       value: "<database host>"
     - name: XROAD_DB_PORT
       value: "<database port>"
     - name: XROAD_DB_PWD
       value: "<xroad db password>"
     - name: XROAD_DATABASE_NAME
       value: "<database-name>"
```

Once the deployment is ready save it on a file and run on a terminal:
```
kubectl apply -f /path/to/<template-file-name>.yaml
```
Check that the Pod is deployed by running (**reference Data: 3.1**):
```
kubectl get pods -n <namespace name>
```
And get the pod information by running (**reference Data: 3.1, 3.2**):
```
kubectl describe pod -n <namespace name> <pod-name>
```

Get a Shell to a running Pod (**reference Data: 3.1, 3.2**):
```
kubectl exec -it -n <namespace name> <pod-name> bash
```

Delete the Pod by running:
```
kubectl delete -f /path/to/<template-file-name>.yaml
```

### 4.2.3 Kubernetes Volumes
Kubernetes volumes can be used to store different things such as the configuration of the Security Server Sidecar, the storage of message logs ... ensuring that this storage could be shared between Pods and it's not lost when the Pods are deleted.

#### 4.2.3.1 Persistent Volume Claim
First, it is required to create an **PVC(Persistent Volume Claim)** to request physical storage. PVCs are a way for developers to "claim" durable storage without knowing the details of the particular Volume implementation type.
Create the PVC template and save it in a "yaml" file **(reference Data: 3.1, 3.4, 3.5, 3.6, 3.7)**:
``` yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: <pvc name>
  labels:
    usage: <namespace name>
spec:
  storageClassName: <volume storage class name>
  accessModes:
    - <volume access mode>
  resources:
    requests:
      storage: <volume size>
```
Deploy the PVC template:
```
kubectl apply -f /path/to/pvc_file.yaml
```

Kubernetes have multiple types of **PV(Persistent Volumes)** that can be found [here](https://kubernetes.io/docs/concepts/storage/volumes/#volume-types).
The described scenario is focus on 3 types of volume that we can use in AWS: hostPath, awsElasticBlockStore and a AWS EFS (Elastic File System)  (using csi, a Container Storage Interface that defines standard interface for container orchestration systems).

#### 4.2.3.2 Persistent Volume hostPath
A hostPath PV mounts a file or directory from the host node's filesystem into your Pod. This PV is the fastest way of creating a PV but it's only recommended for testing or developing purposes and in a single Node scenario, since only the Pods running on the Node could access to it, also it does not offer any backup solution and the information could be lost if the Node is deleted.
Create the PV template and save it in a "yaml" file **(reference Data: 3.5, 3.6, 3.7, 3.8, 3.9)**:
``` yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: <pv name>
  labels:
    type: local
spec:
  storageClassName: <volume storage class name>
  capacity:
    storage: <volume size>
  accessModes:
    - <volume access mode>
  persistentVolumeReclaimPolicy: Recycle
  hostPath:
    path: "<pv host path>"
```

#### 4.2.3.3 Persistent Volume awsElasticBlockStore
An awsElasticBlockStore PV mounts an AWS EBS volume into our pod. It offers and easy way of backup and keep the informaton even if the Node is deleted. This volume is only recommended for production environment in a single Node scenario, since the awsElasticBlockStore could be attached only to one single instance at a time.
- First, we need to create an  Elastic Block Store Volume from the AWS console, then attach it to the Cluster Node instance, setting "/dev/xvdf" on the device property.
- Once the volume is created, copy the id and create a PV template and save it to a "yaml" file **(reference Data: 3.5, 3.6, 3.7, 3.8, 3.9, 3.10)**:
``` yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: <pv name>
  labels:
    type: local
spec:
  storageClassName: <volume storage class name>
  capacity:
    storage: <volume size>
  accessModes:
    - <volume access mode>
  persistentVolumeReclaimPolicy: Recycle
  awsElasticBlockStore:
    fsType: "ext4"
    volumeID: "<awsElasticBlockStore volume id>"
```
- It is possible to verify the information and get the volume info by running (Authentication through the AWS CLI it's required):
```
aws --region <cluster region> ec2 describe-volumes --volume-id <awsElasticBlockStore volume id>
```
- Copying the property "Device" of the previous command, we can run, from the cluster instance where the volume was mounted, the next command to get more information about the free disk space, the mountpoint... :
```
df -hT <device>
```
```
lsblk <device>
```

#### 4.2.3.4 Persistent Volume AWS Elastic File System
Using the Container Storage Interface provided by Kubernetes it is possible to mount an AWS EFS volume into our pod. This volume it is recommended for production environments in a multiple node scenario since it could be accessed for multiple nodes at same time.
- First, we need to create an Elastic File System from the AWS console, configuring the security groups and allowing access from the Cluster Nodes.

- Deploy the Amazon EFS CSI driver. The Amazon EFS Container Storage Interface (CSI) driver provides a CSI interface that allows Kubernetes clusters running on AWS to manage the lifecycle of Amazon EFS file systems.
```
kubectl apply -k "github.com/kubernetes-sigs/aws-efs-csi-driver/deploy/kubernetes/overlays/stable/ecr/?ref=release-1.0"
```

- Once the volume is created, copy the id and create a PV template and save it to a "yaml" file **(reference Data: 3.5, 3.6, 3.7, 3.8, 3.9, 3.10)**:
``` yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: <pv name>
spec:
  capacity:
    storage: <volume size>
  storageClassName: <volume storage class name>  
  volumeMode: Filesystem
  accessModes:
  - <volume access mode>
  persistentVolumeReclaimPolicy: Retain
  csi:
    driver: efs.csi.aws.com
    volumeHandle: <efs volume id>
```

- It is possible to verify the information and get the volume info by running (Authentication through the AWS CLI it's required) (**reference Data: 3.11**):
```
aws efs describe-mount-targets --file-system-id <efs volume id>
```

- It is possible to mount the volume in a cluster ec2-instance by running from the cluster instance where the volume is going to be mounted (**reference Data: 3.11, 3.22**):
```
sudo mount -t nfs -o nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2,noresvport <efs volume id>.efs.<cluster region>.amazonaws.com:/ <local folder>
```
#### 4.2.3.5 AWS EBS vs AWS EFS
The table below shows us a comparison with the main differences and when it is recommended to use each one to choose the best alternative between AWS EBS and EFS volumes.

|     | **AWS EBS**                                          | AWS EFS                                   |
| --- | ---------------------------------------------------- | ----------------------------------------- |
| Price | General Purpose SSD(gp2) Volumes:  $0.10 per GB-month of provisioned storage.EBS Snapchots: $0.05 per GB-month of data stored. | Standard Storage (GB-Month) : $0.30. |
| Storage  | 16 TB max.No limits in file size.Data stored stays in the same availability zone.Replicas are made within the availability zone for higher durability. | No Limits.47,9TB Maximun file size.Data stored in AWS EFS stays in the region.Replicas are made within the region. |
| Performance | Manually scaled without stopping the instance.It's faster, baseline performance of 3 IOPS per GB for General Purpose volume | Automatically scaled.Supports up to 7000 file system operations per second. |
| Data access | Can only be mounted in a single EC2 instance.EBS PV provide only ReadWriteOnce access mode. (this means it can only be used by a single pod at the same time).Multi atach it's a new feaure but it's only available in us-east-1, us-west-2, eu-west-1, and ap-northeast-2 regions. | Can be montend in multlipe EC2 (from 1 to 1000) instances an accessed at the same time. EFS PV provides ReadWriteMany access mode. |
| Durability | 20 times more reliable than normal hard disks | Highly durable (No public SLA) |
| Availability | 99.99% available.Cannot withstand availability zone failure without snapshots. | Highly available service.  (No public SLA)Every file system object is redundantly stored across multiple Availability Zones so it can survive one AZ failure. |
| Backup | Provides point in time snapshots, witch are backed up to AWS S3 and can be copied across the regions. | EFS doesn’t support any backup mechanism we need to setup backup manually. We must use AWS backup. |
| When to use | Single node scenario.Reduce costs.We want a faster option.We don't need to worry about scalability.The amount of data is not that big.Create and recover backups in an easy way | Multiple node scenario.We need concurrent access.We want automatically scalability.The amount of data is big. |


#### 4.2.3.6 Manage Volumes
Once the volume is chosen, we can deploy it by running:
```
kubectl apply -f /path/to/pv_file.yaml
```
List the PV by running (**reference Data: 3.1**):
```
kubectl get pv -n <namespace name>
```
List the PVC by running (The PVC status should be boud, it means that the claim is attached to a PV):
```
kubectl get pvc
```
Delete the PV or PVC by running:
```
kubectl delete -f /path/to/file.yaml.
```

#### 4.2.3.7 Mount the Volume to a Pod
To mount the volume on a Pod, it is required to have a PVC bounded to a PV, then modify the Pod template, in this case the template defined in the step [2.1 Single Pod Deployment with internal database](#21-single-pod-deployment-with-internal-database) will be modified by mapping the volume to the xroad configuration `etc/xroad` (**reference Data: 3.4, 3.12, 3.13**):

``` yaml
...
spec:
  volumes:
  - name: <template volume-name>
    persistentVolumeClaim:
      claimName: <pvc name>
   containers:
   - name: <container name>
     image: niis/xroad-security-server-sidecar:<image tag>
     imagePullPolicy: "Always"
  volumeMounts:
  - name: <template volume name>
    mountPath: /etc/xroad/
...
```

### 4.2.4 Kubernetes Secrets
Kubernetes Secrets allows us to store and manage sensitive information.
For the [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer) scenario you need to create a Kubernetes secret that will store the ssh keys used by the Secondary Pods to synchronize the configuration with the Primary Pod.
If you don't have an ssh key you can create one by running:
```
ssh-keygen -f /path/to/.ssh/
```
Then create a Kubernetes secret for storing the ssh keys by running (**reference Data: 3.1, 3.14**);
```
kubectl create secret generic <secret name> --from-file=private-key=/path/to/.ssh/id_rsa --from-file=public-key=/path/to/.ssh/id_rsa.pub --namespace=<namespace name>
```

#### 4.2.4.1 Secrets for environmental variables
This example shows how to create a secret for the Security Server Sidecar environment variables with sensitive data.
- Create a manifest file called for example 'secret-env-variables.yaml' and fill it with the desired values of the environment variables ( **reference Data: 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10**):
```bash
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
  XROAD_DB_HOST: "<database host>"
  XROAD_DB_PWD: "<database password>"
  XROAD_DB_PORT: "database port"
  XROAD_LOG_LEVEL: "<xroad log level>"
```
- Apply the manifest:
```bash
$ kubectl apply -f secret-env-variables.yaml
```

#### 4.2.4.2 Consume secret for environmental variables
Modify the deployment pod definition in each container that needs to consume the secret. The key from the Secret becomes the environment variable name in the Pod:
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

### 4.2.5 Kubernetes jobs readiness, liveness and startup probes
#### 4.2.5.1 Readiness probes
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

#### 4.2.5.2 Liveness probes
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

#### 4.2.5.3 Startup probes
The startup probes indicate whether the application within the container is started. All other probes are disabled if a startup probe is provided until it succeeds.

Startup probes are useful for Pods that have containers that take a long time to come into service. This is not really useful in the Sidecar pod because it takes to short to start.
In a different scenario where the Sidecar container would take a long time to start, the startup probe can be used in combination with the liveness probe, so that it waits until the startup probe has succeeded before starting the liveness probe. The tricky part is to set up a startup probe with the same command, HTTP or TCP check, with a failureThreshold * periodSeconds long enough to cover the worse case startup time.

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

### 4.2.6 Multiple Pods using a Load Balancer deployment
#### 4.2.6.1 Prerequisites
- A Persitent Volume Claim bounded to a Persitent Volume for store the Primary Pod configuration [4.2.3 Kubernetes Volumes](#423-kubernetes-volumes).
- A Kubernetes Secret with a ssh key pair stored [4.2.4 Kubernetes Secrets](#424-kubernetes-secrets).

#### 4.2.6.2 Primary Pod installation
An example of how to install the Primary Pod is shown in the template below (**reference Data: 3.1, 3.3, 3.4, 3.12, 3.13, 3.14, 3.15, 3.19 1.4, 1.5, 1.6, 1.10**):
``` yaml
apiVersion: v1
kind: Service
metadata:
  name: <service name>
  labels:
    run: <service name>
  namespace: <namespace name>
spec:
  clusterIP: None
  selector:
    run: <service selector>
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
   - name: <template volume name>
     persistentVolumeClaim:
       claimName: <pvc name>
   - name: <template volume name_2>
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
     - name: <template volume name>
       mountPath: /etc/xroad/
     - name: <template volume name_2>
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
     ports:
     - containerPort: 4000
     - containerPort: 5500
     - containerPort: 5577
     - containerPort: 5588
     - containerPort: 22
```

The template has two kubernetes objects:
- A headless Service, this service is used so that the Secondary Pods can connect to the primary one via SSH through a fixed DNS, this DNS will be "<service name>.<namespace name>.svc.cluster.local"  because the private IP of the primary Pod can change each time it is recreated.
This service has no open port since is not required any communication from outside.
- A Pod with the primary image of the Security Server Sidecar, as image tag we can choose between the "primary" or "primary-slim" described in [3 X-Road Security Server Sidecar images for Kubernetes](#3-x-road-security-server-sidecar-images-for-kubernetes). The Pod defines two volumes, on for store the secret public key described in [4.2.4 Kubernetes Secrets](#424-kubernetes-secrets) and a volume to store the `etc/xroad` configuration, it is possible to choose between the volumes described in [4.2.3 Kubernetes Volumes](#423-kubernetes-volumes).
Once the Primary Pod is deployed we need to configure it (register in the Central Server, create the certificates...) following the [the user guide](https://github.com/nordic-institute/X-Road-Security-Server-sidecar/blob/master/doc/security_server_sidecar_user_guide.md#43-configuration).

Once the configuration is ready, we can verify it by connection via SSH to a instance inside the internal network a run the Healthcheck from the commnad line and verifying that the result is OK:
```
curl -i <private pod ip>:5588
```

#### 4.2.6.3 Secondary Pods installation
An example of how to install the Secondary Pod is shown in the template below (**reference Data: 3.1, 3.3, 3.4, 3.12, 3.13, 3.14,3.15, 3.17, 3.18, 3.19, 3.20, 1.4, 1.5, 1.6, 1.10**):
``` yaml
apiVersion: v1
kind: Service
metadata:
  name: <service name>
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
  labels:
    run: <service name>
  namespace: <namespace name>
spec:
  clusterIP: <load balancer private ip>
  type: LoadBalancer
  selector:
    run: <service selector>
  ports:
  - port: 5500
    targetPort: 5500
    protocol: TCP
    name: messaging
  - port: 5577
    targetPort: 5577
    protocol: TCP
    name: ocsp
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: <pod name>
  namespace: sidecar
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
      - name: <template volume name>
        secret:
          secretName: <secret name>
          items:
          - key: private-key
            path: id_rsa
            mode: 0600
          - key: public-key
            path: id_rsa.pub
            mode: 0644
      containers:
      - name: <container name>
        image: niis/xroad-security-server-sidecar:<image tag>
        imagePullPolicy: "Always"
        volumeMounts:
        - name: <template volume name>
          mountPath: "/root/.ssh/"
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
        readinessProbe:
          httpGet:
            path: /
            port: 5588
          initialDelaySeconds: 200
          periodSeconds: 10
          successThreshold: 1
          failureThreshold: 1
        ports:
        - containerPort: 5500
        - containerPort: 5577
        - containerPort: 5588
        - containerPort: 22
```

The template has two kubernetes objects:
- An NLB (Network Load Balancer) which will be in charge of redirecting the traffic between the different Secondary pods. The ClusterIP property is optional and is used to assign a fixed private IP to the Load Balancer, this can help us in the configuration of the Security Server Sidecar in the central server, if not assigned, a new private IP will be assigned to the Load Balancer in each deployment.
It has the required ports "5500" "5577" for the messages communication between Security Servers open.

- A DeploymentControll for deploy the "n" numbers of Pods with the image for the Secondary Pods, as image tag we can choose between the "secondary" or "secondary-slim" described in [3 X-Road Security Server Sidecar images for Kubernetes](#3-x-road-security-server-sidecar-images-for-kubernetes).
The Pods have one volume for store the public and private SSH keys defined on [4.2.4 Kubernetes Secrets](#424-kubernetes-secrets) require for the synchronization with the Primary Pod via SSH. It is not required to add a new volume for store the configuration since all the secondary Pods synchronize the configuration with the Primary, but could be use to make sure that all the Secondary Pods have the same configuration in case any fail to synchronize.
The Secondary Pods also have a "ReadinessProbe", this test will run a healthcheck every 10 seconds, starting 200 seconds after deployment, as long as the healthcheck result is not positive, the Pod status will remain in "NotReady" and will not be included in the redirection of the Load Balancer.

After the template is deployed we can scale the Secondary Pods by running:
```
scale -n <namespace name> --replicas=<number replicas> deployment/<pod name>
```

The Secondary Pods will synchronize the configuration at initialization and through a cron job that runs every minute. Once the configuration is sync, the secondary Pods can process the messages independently of the primary one, this means that if the primary Pods crashes, the cron that synchronizes the configuration will fail but the Secondary Pods can continue to process the messages.

# 5 Backup and Restore
The backup system of the Security Servers described [here](https://github.com/nordic-institute/X-Road/blob/develop/doc/Manuals/ug-ss_x-road_6_security_server_user_guide.md#13-back-up-and-restore)  is still valid for the installation using Kubernetes.

If your Kubernetes deployment uses volumes to store the configuration, you can back up each volume.
As described in the [4.2.3 Kubernetes Volumes](#423-kubernetes-volumes) section we will have 3 types of volume, each one with a way of create a backup:

- AWS Elastic Block Store: Backups can be stored by creating a snapshot of the volume from the AWS admin UI, these snapshots can be configured automatically also it is possible to restore the snapshots into the volume.
- AWS Elastic File System: It is possible to create and restore backups of this volume using [AWS Backup](https://docs.aws.amazon.com/efs/latest/ug/awsbackup.html).
- Host path: This volume does not have any tool to make backup copies, if necessary it will be required to do them manually by copying the contents of the volume.

# 6 Monitoring
**Amazon CloudWatch** monitors your Amazon Web Services (AWS) resources and the applications you run on AWS in real time. You can use CloudWatch to collect and track metrics, which are variables you can measure for your resources and applications.
The CloudWatch home page automatically displays metrics about every AWS service you use.
You can create alarms that watch metrics and send notifications or automatically make changes to the resources you are monitoring when a threshold is breached.
For more information about Cloudwatch check [here](https://docs.aws.amazon.com/cloudwatch/index.html).

**CloudWatch container insights** is a tool available for AWS EKS that we can use to  collect, aggregate, and summarize metrics and logs from your containerized applications and microservices.
CloudWatch automatically collects metrics for many resources, such as CPU, memory, disk, and network. Container Insights also provides diagnostic information, such as container restart failures, to help you isolate issues and resolve them quickly. You can also set CloudWatch alarms on metrics that Container Insights collects.
Container Insights uses a containerized version of the CloudWatch agent to discover all of the running containers in a cluster. It then collects performance data at every layer of the performance stack.

**Fluentd** is an open-source data collector that we'll set up on our Kubernetes nodes to tail container log files, filter and transform the log data, and deliver it to the Elasticsearch cluster, where it will be indexed and stored.

## 6.1 Setup Container Insights on AWS EKS

- Verify that cluster logging is enabled on the cluster (At least Controller manager logging).
- Deploy container insights by running (**reference Data: 3.21, 3.22**):
```
curl https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluentd-quickstart.yaml | sed "s/{{cluster_name}}/<cluster name>/;s/{{region_name}}/<cluster region>/" | kubectl apply -f -
```
- Verify the installation by running:
```
kubectl get pods -n amazon-cloudwatch
```
- View the logs of the cloudwatch agent by running (**reference Data: 3.23**):
```
kubectl logs <cloudwatch agent name> -n amazon-cloudwatch
```
- It is possible to delete the deployment by running:
```
curl https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluentd-quickstart.yaml | sed "s/{{cluster_name}}/<cluster-name>/;s/{{region_name}}/<cluster-region>/" | kubectl delete -f -
```

Once the agent is installed, you can see the cluster metrics, create alerts ... from the AWS admin console on the CloudWatch => Container Insights screen.


# 7 Version update
It will be possible to update the version of the Security Server Sidecar by re-displaying the image with a higher version in the image tag.

In case of the scenario [2.3 Multiple Pods using a Load Balancer](#23-multiple-pods-using-a-load-balancer) and it is required to no disrupt the connection while the version is updating, it is required to take several steps:

- First, it is required to stop in the Secondary Pods, the cron job service that handles synchronization with the Primary Pod, it is possible to do that by running in a console for each Secondary Pod (**reference Data: 3.2**):
```
kubectl exec <pod name> -n $1 supervisorctl stop cron
```
Note (1) It is possible to use some script like this to stop the service for all the Pods in a namespace (**reference Data: 3.1**):
```
for pod in $(kubectl get po -n <namespace name> -oname | awk -F "/" '{print $2}');
   do kubectl exec $pod -n $1 supervisorctl stop cron;
done;
```

- Update the Primary Pod by open a shell for the Primary Pod container by running:
```
kubectl exec -it <pod name> -n <namespace name> bash
```
Stop the Services:
```
supervisorctl stop all
```
Add the new version repository key:
```
echo "deb https://artifactory.niis.org/xroad-release-deb bionic-current main" >/etc/apt/sources.list.d/xroad.list && apt-key add '/tmp/repokey.gpg'
```
Update the packages:
```
apt-get update && apt-get upgrade
```
Start the Services:
```
supervisorctl start all
```

Note(2): It is possible that a major version update will require extra changes, for doing that check the specific documentation for the version update.

- Once the Primary Pod is update, it is necessary to do the same previous step for all the Secondary Pods one by one, doing this, each Secondary Pod will remain in "NotReady" state and will exit the Load Balancer redirect while it is being updated, but the rest will continue to work with the old version without disrupting the connection.


# 8 Message logs and disk space
As described in the [User Guide](https://github.com/nordic-institute/X-Road-Security-Server-sidecar/blob/master/doc/security_server_sidecar_user_guide.md#8-message-log) the recommend was to store the local storage of message log in a docker container. In kubernetes we can create any of the Kubernetes volumes described in [4.2.3 Kubernetes Volumes](#423-kubernetes-volumes).

It is also recommend to send the logs inside the volume to a AWS S3 Bucket. For doing that it is required to:
- Create an AWS S3 Bucket from the AWS admin console.
- Encrypt the AWS S3 Bucket at rest by selecting the default encryption "AWS-KMS" and "aws/s3" as encryption key.
- From the instance where the volume is mounted run (**reference Data: 3.24, 3.25, 3.26**):
```
aws s3 sync <volume mount path> s3://<bucket name>/path/to/bucket-folder --sse aws:kms --sse-kms-key-id <arn encryption key>
```
