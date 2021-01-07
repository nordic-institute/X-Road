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
   * [4 Installation](#3-installation)
      * [4.1 Minimum system requirements](#31-minimum-system-requirements)
      * [4.2 Prerequisites to Installation](#32-prerequisites-to-installation)
      * [4.3 Network configuration](#33-network-configuration)


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
niis/xroad-security-server-sidecar:&lt;version&gt-slim-secondary    | Image for the Secondary Pod deployment using the slim version of the Security Server
niis/xroad-security-server-sidecar:&lt;version&gt;-primary          | Image for the Primary Pod deployment using the regular (with message logging and operational monitor) version of the Security Server
niis/xroad-security-server-sidecar:&lt;version&gt;-secondary        | Image for the Secondary Pod deployment using the regular (with message logging and operational monitor) version of the Security Server.



# 4 Installation
## 4.1 Minimum system requirements
- An AWS EKS cluster with at list one linux EC2 instance. The instance type will depend on the amount of resources required. For example an instance of type "t3.medium" could support approximately 5 Security Server Sidecar Pods running simultaneously.
## 4.2 Prerequisites to Installation
- The latest AWS CLI and eksctl command line utility must be installed to go through the steps described in this [page](https://docs.aws.amazon.com/eks/latest/userguide/getting-started-eksctl.html).
- We must be authenticated to access to the AWS resources through the AWS CLI. Instructions for authenticated can be found [here](https://aws.amazon.com/premiumsupport/knowledge-center/authenticate-mfa-cli/).
- A ssh key must be uploaded to "Key Pairs" section in Amazon EC2, Instruction for uploading a key can be found [here](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html#prepare-key-pair).

## 4.3 Network configuration
