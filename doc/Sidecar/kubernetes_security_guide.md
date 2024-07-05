# Kubernetes Security Server Sidecar Security User Guide <!-- omit in toc -->

Version: 1.5  
Doc. ID: UG-K-SS-SEC-SIDECAR

## Version history <!-- omit in toc -->

| Date       | Version | Description                                  | Author                    |
|------------| ------- |----------------------------------------------| ------------------------- |
| 25.01.2021 | 1.0     | Initial version                              | Alberto Fernandez Lorenzo |
| 09.03.2021 | 1.1     | Add Horizontal Pod Autoscaler best practices | Alberto Fernandez Lorenzo |
| 28.11.2021 | 1.2     | Add license info                             | Petteri Kivimäki          |
| 11.10.2022 | 1.3     | Updating links                               | Monika Liutkute           |
| 06.07.2023 | 1.4     | Sidecar repo migration                       | Eneli Reimets             |
| 11.04.2024 | 1.5     | Updated for AKS                              | Madis Loitmaa             |

## License

This document is licensed under the Creative Commons Attribution-ShareAlike 4.0 International License.
To view a copy of this license, visit <https://creativecommons.org/licenses/by-sa/4.0/>

## Table of contents

- [License](#license)
- [Table of contents](#table-of-contents)
- [1 Introduction](#1-introduction)
  - [1.1 Target Audience](#11-target-audience)
- [2 Reference Data](#2-reference-data)
- [3 Handling passwords and secrets](#3-handling-passwords-and-secrets)
  - [3.1 Secrets as volume](#31-secrets-as-volume)
  - [3.2 Secrets as environment variables](#32-secrets-as-environment-variables)
- [4 User accounts](#4-user-accounts)
  - [4.1 Create a kubeconfig](#41-create-a-kubeconfig)
    - [4.1.2 Create a kubeconfig for Amazon EKS](#412-create-a-kubeconfig-for-amazon-eks)
    - [4.1.2 Create a kubeconfig for Azure AKS](#412-create-a-kubeconfig-for-azure-aks)
    - [4.1.3 Test the configuration](#413-test-the-configuration)
  - [4.2 Grant cluster access to users](#42-grant-cluster-access-to-users)
  - [4.3 Restrict namespace access to a cluster](#43-restrict-namespace-access-to-a-cluster)
  - [4.4 Kubernetes Dashboard](#44-kubernetes-dashboard)
- [5 Network policies](#5-network-policies)
  - [5.1 Create Network policies](#51-create-network-policies)
- [6 Pod Security Admission](#6-pod-security-admission)
- [7 Assign Resources to Containers and Pods](#7-assign-resources-to-containers-and-pods)
- [8 Monitoring](#8-monitoring)
- [9 Backups](#9-backups)
- [10 Message logs](#10-message-logs)
- [11 Horizontal Pod Autoscaler best practices](#11-horizontal-pod-autoscaler-best-practices)

## 1 Introduction

### 1.1 Target Audience

This User Guide is meant for X-Road Security Server system administrators responsible for installing and using X-Road Security Server Sidecar in Amazon Elastic Kubernetes Service (Amazon EKS) or Azure Kubernetes Service (AKS) environment.

This document will discuss how to secure the installation of a Security Server Sidecar cluster explained in the [Kubernetes User Guide](kubernetes_security_server_sidecar_user_guide.md).
The document is intended for readers with a moderate knowledge of Linux server management, computer networks, Docker, Kubernetes (including it's cloud provider's specialties (Amazon EKS or Azure AKS)) and X-Road.

## 2 Reference Data

Please check the Reference data in the [Kubernetes User Guide](kubernetes_security_server_sidecar_user_guide.md#44-reference-data).

| **Ref** | **Value**              | **Explanation**                                                                                                |
| ------- | ---------------------- | -------------------------------------------------------------------------------------------------------------- |
| 4.1     | \<network policy name> | Unique name that identifies a NetworkPolicy inside a namespace.                                                |
| 4.2     | \<resource group>      | Name of resource group in AKS.                                                                                 |

## 3 Handling passwords and secrets

Kubernetes Secrets let you store and manage sensitive information in a safer way than putting it verbatim in a Pod definition or container image. For example, for the scenario [Multiple Pods using a Load Balancer](kubernetes_security_server_sidecar_user_guide.md#23-multiple-pods-using-a-load-balancer) it is recommended to use secrets to store the environmental variables for the database password and the SSH keys using for the communication between the Primary and Secondary pods.
The Secrets can be used with a Pod in three ways:

* As files in a volume mounted on one or more containers.
* As container environment variable.
* By the kubectl when pulling images for the Pod.

For the Security Server Sidecar container, the first option is used to store the SSH keys and the second option is used to store the database password. The third option is only required if the Sidecar image would be in a private repository, which is not the case.

### 3.1 Secrets as volume

If you don't have an existing SSH key, you can create one by running:

```bash
ssh-keygen -f /path/to/.ssh/id_rsa
```

Then create a Kubernetes Secret to store the SSH key by running (**Reference Data: 3.1, 3.7**);

```bash
kubectl create secret generic <secret name> --from-file=private-key=/path/to/.ssh/id_rsa --from-file=public-key=/path/to/.ssh/id_rsa.pub --namespace=<namespace name>
```

We can then consume these secrets as a volume in Kubernetes, i.e. by including the Secret under the definition of volumes in the Pod deployment manifest. We should define the key name, path and permissions, and then mount the volume on a path inside the container.

Below is an example of using Secrets in a Pod deployment manifest file (**Reference Data: 3.6, 3.7**):

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
    - key: private-key
      path: id_rsa.pub
      mode: 0644
[...]
   volumeMounts:
   - name: <manifest volume name>
     mountPath: "/etc/.ssh/"
[...]
```

### 3.2 Secrets as environment variables

This example shows how to create a secret for the Security Server Sidecar as environment variables with sensitive data.

1. Create a manifest file called for example `secret-env-variables.yaml` and fill it with the desired values of the environment variables ( **Reference Data: 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10**):

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
      XROAD_DB_HOST: "<database host>"
      XROAD_DB_PWD: "<database password>"
      XROAD_DB_PORT: "database port"
      XROAD_LOG_LEVEL: "<xroad log level>"
    ```

2. Apply the manifest:

    ```bash
    kubectl apply -f secret-env-variables.yaml
    ```

Then we can consume the Secrets as environment variables by modifying the deployment Pod manifest of each container that requires the secret as follows (the Secret key becomes the environment variable name in the Pod):

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

Alternatively, if we don't want to include all the environment variables in a single Secret, we can reference it by key in the environment variable definition. Below is an example of how to do that with the variable "XROAD_DB_PWD":

```yaml
[...]
containers:
  - name: security-server-sidecar
    image: niis/xroad-security-server-sidecar:<image tag>
    imagePullPolicy: "Always"
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
      valueFrom:
        secretKeyRef:
          name: secret-sidecar-variables
          key: XROAD_DB_PWD
    - name: XROAD_DATABASE_NAME
      value: "<database name>"
[...]
```

## 4 User accounts

Amazon EKS and Azure AKS command line utilities can be used to authenticate user to your Kubernetes cluster. However, for authorization, they still rely on native Kubernetes Role Based Access Control (RBAC). All the permissions required for interacting with the Kubernetes API of your cluster are managed through the native Kubernetes RBAC system.

### 4.1 Create a kubeconfig

A kubeconfig file is a file used to configure access to Kubernetes when used in conjunction with the kubectl command-line tool.


#### 4.1.2 Create a kubeconfig for Amazon EKS
 
To create a kubeconfig file, we should first be authenticated through the AWS CLI. More information on how to authenticate with AWS CLI can be found [here](https://aws.amazon.com/premiumsupport/knowledge-center/authenticate-mfa-cli/).

Then, open a terminal and run the following command (**Reference Data: 3.12, 3.13**):

```bash
aws eks --region <cluster region> update-kubeconfig --name <cluster name>
```

#### 4.1.2 Create a kubeconfig for Azure AKS

To create a kubeconfig file, we should first be authenticated through the Azure CLI. More information on how to authenticate with Azure CLI can be found [here](https://docs.microsoft.com/en-us/cli/azure/authenticate-azure-cli).

Then, open a terminal and run the following command (**Reference Data: 3.12, 4.2**):

```bash
az aks get-credentials --resource-group <resource group> --name <cluster name>
```

#### 4.1.3 Test the configuration

We can test the configuration by running the following command (you should see one Kubernetes service):

```bash
kubectl get svc

NAME                               TYPE       AGE     CLUSTER-IP      EXTERNAL-IP                                                                  PORT(S)                                                                                                                                          
kubernetes                         ClusterIP  190d    10.100.0.1      <none>                                                                          443/TCP            
```

Verify that the kubeconfig file exists by running:

```bash
cat /root/.kube/config
```

### 4.2 Grant cluster access to users

Kubernetes has fine-grained built-in role based access control (RBAC) that can be used to for regulating access to Kubernetes cluster. More information about Kubernetes RBAC can be found in [official documentation](https://kubernetes.io/docs/reference/access-authn-authz/rbac/). 

Both Amazon EKS and Azure AKS provide methods for integrating their IAM roles with Kubernetes RBAC:
- [Grant access to Kubernetes Amazon EKS APIs](https://docs.aws.amazon.com/eks/latest/userguide/grant-k8s-access.html)
- [Use Azure role-based access control for Kubernetes Authorization](https://learn.microsoft.com/en-us/azure/aks/manage-azure-rbac)

### 4.3 Restrict namespace access to a cluster

If the same Cluster is shared by different developers and teams, it is advisable to create isolated environments by creating namespaces and restrict access within each namespace. More information about how to assign permissions to a namespace in:
- [Amazon EKS Cluster](https://aws.amazon.com/premiumsupport/knowledge-center/eks-iam-permissions-namespaces/)
- [Azure AKS Cluster](https://learn.microsoft.com/en-us/azure/aks/concepts-identity)

### 4.4 Kubernetes Dashboard

Dashboard is a web-based Kubernetes user interface. You can use Dashboard to deploy containerized applications to a Kubernetes cluster, troubleshoot your containerized application, and manage the cluster resources.

The Dashboard UI is not deployed by default. To deploy it using `helm` run the following commands:

```bash
# Add kubernetes-dashboard repository
helm repo add kubernetes-dashboard https://kubernetes.github.io/dashboard/
# Deploy a Helm Release named "kubernetes-dashboard" using the kubernetes-dashboard chart
helm upgrade --install kubernetes-dashboard kubernetes-dashboard/kubernetes-dashboard --create-namespace --namespace kubernetes-dashboard
```

You can access Dashboard using the kubectl command-line tool by running the following command:

```bash
kubectl -n kubernetes-dashboard port-forward svc/kubernetes-dashboard-kong-proxy 4433:443
```

Kubectl will make Dashboard available at <https://localhost:4433>.

In the login view, you will be required to enter a token. To try out Kubernetes Dashboard you can [create a sample user](https://github.com/kubernetes/dashboard/blob/master/docs/user/access-control/creating-sample-user.md) and get the token with the following command:

```bash
kubectl -n kubernetes-dashboard create token admin-user
```

Additional information on Kubernetes Dashboard can be found in the following links:
- [Kubernetes Dashboard project page](https://github.com/kubernetes/dashboard)
- [Accessing Dashboard](https://github.com/kubernetes/dashboard/blob/master/docs/user/accessing-dashboard/README.md)
- [Access Control](https://github.com/kubernetes/dashboard/blob/master/docs/user/access-control/README.md)
- [Creating sample user](https://github.com/kubernetes/dashboard/blob/master/docs/user/access-control/creating-sample-user.md)


Installing the Kubernetes Dashboard could have potential security risks:

* The installation recommended in the EKS docs tells users to authenticate when connecting to the dashboard by fetching the authentication token for the dashboard’s cluster service account, which, again, may have cluster-admin privileges. That means a service account token with full cluster privileges and whose use cannot be traced to a human is now floating around outside the cluster.
* Everyone who can access the dashboard can make any queries or changes permitted by the service’s RBAC role.
* The Kubernetes Dashboard has been the subject of a number of CVEs. Because of its access to the cluster’s Kubernetes API and its lack of internal controls, vulnerabilities can be extremely dangerous.

## 5 Network policies

Network policies are objects which allow to explicitly state which traffic is permitted between groups of pods and other network endpoints in a Kubernetes cluster so that all non-conforming traffic will be blocked. It is a Kubernetes equivalent of a cluster-level firewall.

Each network policy specifies a list of allowed (ingress and egress) connections for all the group of pods specified using pod selectors and labels. If at least one network policy is applicable to a pod then the pod is considered isolated and are allowed to make or accept the connections listed in the network policy. On the contrary, if no network policies are applicable to a pod, then all network connections to and from it are allowed. Therefore, it's strongly recommended to implement network policies to restrict the attack surface if there are services exposed to the Internet.

The most relevant network policies to enforce are:

* Explicitly allow internet access for pods that need it: We can do that by setting the flag `allow-internet-access` to true.
* Explicitly allow necessary pod-to-pod communication: If we don't know on beforehand which pods need to communicate, we can allow traffic between all pods which belong to the same namespace or which have a specific label. We can also allow pods from a deployment A to communicate with pods from a deployment B or from a namespace A to a namespace B.

Network Policies are similar to AWS security groups in the sense that allows creating network ingress and egress rules. The difference with Network Policies is that it allows assigning them to pods using pod selectors and labels instead of instances of an AWS security group.

Network Policies can be configured with a network provider with network policy support, such as Calico, Cilium, Kube-router, Romana, Weave Net. In this case, we will be using Calico.

To install Calico, follow the instructions for your cloud provider:
- [Amazon EKS](https://docs.tigera.io/calico/latest/getting-started/kubernetes/managed-public-cloud/eks)
- [Azure AKS](https://docs.tigera.io/calico/latest/getting-started/kubernetes/managed-public-cloud/aks)

Verify the installation by running:

```bash
kubectl get daemonset calico-node --namespace kube-system
```

### 5.1 Create Network policies

In this example, it will be shown how to isolate the Primary Pod described in [Multiple Pods scenario](kubernetes_security_server_sidecar_user_guide.md#456-multiple-pods-using-a-load-balancer-deployment) so that it only allows traffic from the Secondary Pods through port 22.

1. Modify the Primary Pod manifest adding the label "role:primary" to identify it:

    ```yaml
    [...]
    metadata:
      name: <service name>
      labels:
        run:  <service selector>
        role: primary
    [...]
    ```

2. Modify the Secondary Pod manifest adding the label "role:secondary" to identify it:

    ```yaml
    [...]
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
    [...]
    ```

3. Apply the changes in the manifests.

4. Create a NetworkPolicy to deny all the ingress traffic for the Primary Pod (**Reference Data: 3.1, 4.1**):

    ```yaml
    kind: NetworkPolicy
    apiVersion: networking.k8s.io/v1
    metadata:
      namespace: <namespace name>
      name: <network policy name>
    spec:
      podSelector:
        matchLabels:
          role: primary
      policyTypes:
      - Ingress
    ```

5. Apply the manifest:

    ```bash
    kubectl apply -f /path/to/file.yaml
    ```

6. After all the ingress traffic is denied, create a network policy that allows the traffic from the Secondary Pods to the Primary Pod through the port 22 (**Reference Data: 3.1, 4.1**):

    ```yaml
    kind: NetworkPolicy
    apiVersion: networking.k8s.io/v1
    metadata:
      namespace: <namespace name>
      name:  <network policy name>
    spec:
      podSelector:
        matchLabels:
          role: primary
      ingress:
      - from:
        - podSelector:
            matchLabels:
                role: secondary
        ports:
        - protocol: TCP
          port: 22
    ```

7. Apply the manifest:

    ```bash
    kubectl apply -f /path/to/file.yaml
    ```

8. List the NetworkPolicies by running (**Reference Data: 3.1):

    ```bash
    kubectl get networkpolicies -n <namespace name>
    ```

9. Delete the NetworkPolicies by running:

    ```bash
    kubectl delete -f /path/to/file.yaml
    ```

The [security-server-sidecar-network-policy-examples manifest template](files/security-server-sidecar-network-policy-examples.yaml) contains examples of all the above network policies.


## 6 Pod Security Admission

[Pod Security Admission](https://kubernetes.io/docs/concepts/security/pod-security-admission/) places requirements on Pod's Security Context and other related field according to the levels defined in the [Pod Security Standards](https://kubernetes.io/docs/concepts/security/pod-security-standards/). The Pod Security Admission controller is enabled by default in Kubernetes 1.21 and later. It is recommended to enable the Pod Security Admission controller to enforce the Pod Security Standards.

X-Road sidecar container image adheres to `baseline` level of the Pod Security Standards as the container cannot be run as non-root user for now.

Pod Security Admission can be configured using metadata labels for namespaces. To enforce `baseline` level for the namespace sidecar container is running in, add the following label to the namespace:

```yaml
pod-security.kubernetes.io/enforce=baseline
```

To add the label to an existing namespace run the following `kubectl` command (**Reference Data: 3.1**):

```bash
kubectl label --overwrite ns <namespace name> pod-security.kubernetes.io/enforce=baseline
```

## 7 Assign Resources to Containers and Pods

Requests and limits are the mechanisms Kubernetes uses to control resources such as CPU and memory. Requests are what the container is guaranteed to get. If a container requests a resource, Kubernetes will only schedule it on a node that can give it that resource. Limits, on the other hand, make sure a container never goes above a certain value. The container is only allowed to go up to the limit, and then it is restricted.

By configuring the CPU requests and limits of the Containers that run in your cluster, you can make efficient use of the CPU resources available on your cluster Nodes.

Setting a CPU/Memory limit prevents a Container from exhausting all the resources available on the Node.
Pod scheduling is based on requests. A Pod is scheduled to run on a Node only if the Node has enough CPU/Memory resources available to satisfy the Pod CPU request. If you specify a CPU/Memory limit but you don't specify the corresponding request, Kubernetes will automatically assign a CPU/Memory request that matches the limit. If a Container allocates more memory than its limit, the Container becomes a candidate for termination. If the Container continues to consume memory beyond its limit, the Container is terminated.

To specify a CPU request for a container, include the `resources:requests` field in the Container resource manifest. To specify a CPU limit, include `resources:limits`. Modify the X-Road Security Server deployment manifest to add the resources:

```yaml
[...]
containers:
  - name: security-server-sidecar
    image: niis/xroad-security-server-sidecar:<image tag>
    imagePullPolicy: "Always"
    resources:
      limits:
        cpu: "1"
        memory: "200Mi"
      requests:
        cpu: "0.5"
        memory: "200Mi"
[...]
```

* The CPU resource is measured in CPU units. One CPU, in Kubernetes, is equivalent to:
  * 1 AWS vCPU
  * 1 GCP Core
  * 1 Azure vCore
  * 1 Hyperthread on a bare-metal Intel processor with Hyperthreading
Fractional values are allowed. A Container that requests 0.5 CPU is guaranteed half as much CPU as a Container that requests 1 CPU. You can use the suffix m to mean milli. For example 100m CPU, 100 milliCPU, and 0.1 CPU are all the same. PrecisionFractional values are allowed for the CPU units. For example, a Container with a limit of 0.5 CPU is guaranteed half as much CPU as a Container that requests 1 CPU.
You can also combine the value with the suffix `m` to mean milli. For example, 100m CPU, 100 milliCPU, and 0.1 CPU are all the same. A precision smaller than 1m is not allowed.

* The memory resource is measured in bytes. You can express memory as a plain integer or a fixed-point integer with one of these suffixes: E, P, T, G, M, K, Ei, Pi, Ti, Gi, Mi, Ki.

## 8 Monitoring

The following steps are recommended for monitoring using AWS CloudWatch and Azure Monitor so that we can detect potential security risks in your Cluster.

* **Collect control plane logs**: The control plane logs capture Kubernetes audit events and requests to the Kubernetes API server, among other components. Analysis of these logs will help detect some types of attacks against the cluster, and security auditors will want to know that you collect and retain this data.
* Amazon EKS Clusters can be configured to send control plane logs to Amazon CloudWatch. Similarly, Azure AKS control plane logs can be sent to Azure Monitor as resource logs. At a minimum, you will want to collect the following logs:
  * api - the Kubernetes API server log.
  * audit - the Kubernetes audit log.
  * authenticator - the EKS component used to authenticate AWS IAM entities to the Kubernetes API.

* **Monitor container and cluster performance for anomalies**:  Irregular spikes in application load or node usage can be a signal that an application may need programmatic troubleshooting, but they can also signal unauthorized activity in the cluster. Monitoring key metrics provides critical visibility into your workload’s functional health and that it may need performance tuning or that it may require further investigation. For collecting these metrics, it is required to set up Amazon CloudWatch Container Insights for your cluster. Azure has similar capabilities with Container Insights.

* **Monitor Node (EC2 Instance) Health and Security**: EKS provides no automated detection of node issues. Changes in node CPU, memory, or network metrics that do not correlate with the cluster workload activity can be signs of security events or other issues.

## 9 Backups

The restoration of backups is a process that is executed with root permission and therefore it can lead to potential security risks. Please ensure the backup files are coming from trusted sources before restoring them.

## 10 Message logs

The backup of the log messages may contain sensitive information. Therefore, it is recommended to save the automatic backups in an AWS EFS or Azure Files type volume and periodically send the backups to an AWS S3 Bucket or Azure Blob Storage with encryption both in transit and rest. More information can be found in [the Kubernetes User Guide](kubernetes_security_server_sidecar_user_guide.md#8-message-log-archives).

## 11 Horizontal Pod Autoscaler best practices

**Ensure every Pod has Resources Requests defined**: The cluster HPA based on CPU/Memory will scale down any nodes that have a utilization less than a specified threshold. Utilization values are calculated as a percentage of the resource requests of individual pods. Missing resource request values for some containers might throw off the utilisation calculations of the HPA controller leading to suboptimal operation and scaling decisions. More information about how to assign resource request and limits can be found [here](#7-assign-resources-to-containers-and-pods).

**Ensure Resource Availability for the HPA Pod**: Make sure the Node can handle the maximum number of Pods to be autoscaled. Running out of resources may lead to low performance and the Pods being terminated.

**Ensure Resource Requests are Close to Actual Usage**: Over-provisioning resources can lead to situations where Pods are not utilizing the requested resources efficiently, leading to a lower overall Node utilization. It is recommended to measure the historical usage of the Pods and adjust the resources assigned based on it.

**Favour Custom Metrics over External Metrics**: External metrics, i.e. the metrics that do not come from the application itself, can be used for example to scale the Pods based on the utilization of an external server database (Prometheus adapter could be used to get those external metrics). However, it is preferable to use custom metrics rather than external metrics whenever possible since the external metrics API takes a lot more effort to secure than the custom metrics API, potentially allowing access to all metrics.

**Configure Cooldown Period**: The dynamic nature of the metrics being evaluated by the HPA may lead to scaling events in quick succession without a cooldown period between those scaling events. This leads to thrashing where the number of replicas fluctuates frequently and is not desirable. To prevent this, it is recommended to specify a [cooldown period](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/#support-for-cooldown-delay), which specifies the duration that the HPA should wait after a downscale event before initiating another downscale operation, by setting the `--horizontal-pod-autoscaler-downscale-stabilization` flag passed to the `kube-controller-manager`. This flag has a default value of 5 minutes.

**Avoid using HPA and VPA in tandem**: HPA and VPA (Vertical Pod Autoscaler) give us the ability to autoscale the resources of our application. However, both are currently incompatible so it is recommended to avoid using them together for the same set of pods.
