# Kubernetes Security Server Sidecar User Guide <!-- omit in toc -->

## Version history <!-- omit in toc -->

 Date       | Version | Description                                                     | Author
 ---------- | ------- | --------------------------------------------------------------- | --------------------
 05.01.2021 | 1.0     | Initial version                                                 | Alberto Fernandez Lorenzo


# 1 Introduction
## 1.1 Target Audience
The intended audience of this User Guide are X-Road Security Server system administrators responsible for installing and using X-Road Security Server Sidecar in AWS EKS environment.

The document is intended for readers with a moderate knowledge of Linux server management, computer networks, Docker, Kubernetes, AWS EKS and X-Road.

# 2 Deployment Options
## 2.1 Single Pod Deployment with internal database
The simplest deployment option is to use a single Pod that runs a Security Server Sidecar container with a local database running inside the container.
![Security Server with local database](img/ig-single_pod_local_database.svg)

With this deployment, the Pod will be assigned a private IP and will only be accessible from within the private network. This deployment is only recommended for testing or developing environment since it does not allow scaling of nodes or pods.
This is the template for a simple deployment:
/**
apiVersion: v1
kind: Pod
metadata:
  name: security-server-sidecar
  labels:
    run: security-server-sidecar
spec:
   volumes:
   containers:
   - name: security-server-sidecar
     image: niis/xroad-security-server-sidecar:6.24.0
     imagePullPolicy: "Always"
     env:
     - name: XROAD_TOKEN_PIN
       value: "1234"
     - name: XROAD_ADMIN_USER
       value: "xrd"
     - name: XROAD_ADMIN_PASSWORD
       value: "secret"
     - name: XROAD_DB_HOST
       value: "127.0.0.1"
     ports:
     - containerPort: 80
     - containerPort: 443
     - containerPort: 4000
     - containerPort: 5500
     - containerPort: 5577
     - containerPort: 5588

*/
