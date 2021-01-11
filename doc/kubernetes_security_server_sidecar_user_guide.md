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
      * [4.2.3 Kubernetes Persistent Volumes](#423-kubernetes-persistent-volumes)
         * [4.2.3.1 Persistent Volume Claim](#4231-persistent-volume-claim)
         * [4.2.3.2 hostPath](#4232-hostpath)
         * [4.2.3.3 awsElasticBlockStore](#4233-awselasticblockstore)
         * [4.2.3.4 AWS Elastic File System](#4234-aws-elastic-file-system)
         * [4.2.3.5 AWS EBS vs AWS EFS](#4235-aws-ebs-vs-aws-efs)
         * [4.2.3.6 Manage Volumes](#4236-manage-volumes)
         * [4.2.3.7 Mount the Volume to a Pod](#4237-mount-the-volume-to-a-pod)


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
3.12    | &lt;container name&gt;                    | Name of the image container deployed in a Kubernetes pod
3.13    | &lt;template volume name&gt;            | Name that identifies a volume inside a kubernetes template
3.14    | &lt;secret name&gt;            | Unique name that identifies a secret inside a Cluster namespace

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
kubectl apply -f /path/to/<file-name>.yaml
```
Check that the Pod is deployed by running (**reference Data: 3.1**):
```
kubectl get pods -n <namespace name>
```
And get the pod information by running (**reference Data: 3.1, 3.2**):
```
kubectl describe pod -n <namespace name> <pod-name>
```
Delete the Pod by running:
```
kubectl delete -f /path/to/<file-name>.yaml
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
First, we need to create an  Elastic Block Store Volume from the AWS console, then attach it to the Cluster Node instance, setting "/dev/xvdf" on the device property. Once the volume is created, copy the id and create a PV template and save it to a "yaml" file **(reference Data: 3.5, 3.6, 3.7, 3.8, 3.9, 3.10)**:
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

#### 4.2.3.4 Persistent Volume AWS Elastic File System
Using the Container Storage Interface provided by Kubernetes it is possible to mount an AWS EFS volume into our pod. This volume it is recommended for production environments in a multiple node scenario since it could be accessed for multiple nodes at same time.
First we need to create an Elastic File System from the AWS console, configuring the security groups and allowing access from the Cluster Nodes. Once the volume is created, copy the id and create a PV template abd save it to a "yaml" file **(reference Data: 3.5, 3.6, 3.7, 3.8, 3.9, 3.10)**:
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

### 4.2.5 Multiple Pods using a Load Balancer deployment
#### 4.2.5.1 Prerequisites
- A Persitent Volume Claim bounded to a Persitent Volume for store the Primary Pod configuration [4.2.3 Kubernetes Volumes](#423-kubernetes-volumes).
- A Kubernetes Secret with a ssh key pair stored [4.2.4 Kubernetes Secrets](#424-kubernetes-secrets).

#### 4.2.5.2 Primary Pod installation
An example of how to install the Primary Pod is shown in the template below:
``` yaml
apiVersion: v1
kind: Service
metadata:
  name: <headless service name>
  labels:
    run: <headless service name>
  namespace: <namespace name>
spec:
  clusterIP: None
  selector:
    run: <pod label>
---
apiVersion: v1
kind: Pod
metadata:
  name: <pod name>
  namespace: <namespace name>
  labels:
    run: <pod label>
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
       value: "1234"
     - name: XROAD_ADMIN_USER
       value: "xrd"
     - name: XROAD_ADMIN_PASSWORD
       value: "secret"
     - name: XROAD_LOG_LEVEL
      value: "<xroad log level>"
     - name: XROAD_DB_HOST
       value: "<database host>"
     - name: XROAD_DB_PORT
       value: "<database port>"
     - name: XROAD_DB_PWD
       value: "<xroad db password>"
     - name: XROAD_DATABASE_NAME
       value: "<database-name>"
     ports:
     - containerPort: 4000
     - containerPort: 5500
     - containerPort: 5577
     - containerPort: 5588
     - containerPort: 22
```
