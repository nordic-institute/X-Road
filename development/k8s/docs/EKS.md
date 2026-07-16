# Deploying X-Road Security Server on AWS EKS

This guide covers the deltas between the local KinD dev/test environments and a real AWS EKS cluster. The `niis.xroad_k8s` collection
targets both; flipping between them is driven by `cluster_provider: eks` in `inventory/eks/group_vars/all.yml`.

> **Out of scope:** EKS *cluster provisioning* (VPC, subnets, node groups, OIDC provider, IAM roles). Use `eksctl`,
> CloudFormation, or Pulumi for that. This guide assumes the cluster already exists and your kubeconfig points at it.

## Prerequisites on the control host

- `aws` CLI v2 configured (`aws configure` or AWS SSO)
- `eksctl` ≥ 0.170 (if you want the quickstart cluster bring-up below)
- `kubectl`, `helm`, `ansible-core >= 2.20, < 2.21`
- Python `kubernetes` client (`pip install -r requirements.txt`)

## One-shot cluster bring-up (quickstart, optional)

```bash
aws configure sso                         # or aws configure
eksctl create cluster \
  --name xroad-prod \
  --region eu-west-1 \
  --version 1.30 \
  --nodegroup-name default \
  --node-type m6i.large \
  --nodes 3 \
  --nodes-min 3 --nodes-max 6 \
  --managed \
  --with-oidc

aws eks update-kubeconfig --name xroad-prod --region eu-west-1
kubectl get nodes
```

Install AWS-specific addons before running the Ansible playbook:

```bash
# EBS CSI driver (for gp3 PVCs)
eksctl create addon --cluster xroad-prod --name aws-ebs-csi-driver --force

# AWS Load Balancer Controller (for NLB/ALB ingress)
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system --set clusterName=xroad-prod

# cert-manager (for OpenBao TLS, if openbao_tls_mode=cert_manager)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml

# External Secrets Operator (optional; for AWS Secrets Manager sync)
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace
```

## Configure EKS inventory

Copy `inventory/eks/group_vars/all.yml` to a local override or edit in-place. Required values:

```yaml
cluster_provider: eks
aws_account: "123456789012"
aws_region: eu-west-1
xroad_image_registry: "{{ aws_account }}.dkr.ecr.{{ aws_region }}.amazonaws.com"
xroad_image_tag: "8.0.0"

security_server_chart_repo: "oci://artifactory.niis.org/xroad8-release-helm"
security_server_chart: security-server
security_server_chart_version: "0.1.0"

openbao_tls_mode: cert_manager      # or self_signed for bring-up
storage_class: gp3
service_type: LoadBalancer
external_service_bridges: [ ]
deploy_prometheus: false            # use Amazon Managed Prometheus or the in-cluster stack separately
```

## Push X-Road images to ECR

```bash
aws ecr create-repository --repository-name xroad/security-server --region eu-west-1
aws ecr get-login-password --region eu-west-1 | \
  docker login --username AWS --password-stdin \
    ${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com

(
  cd core
  IMAGE_REGISTRY=${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com \
    ./scripts/images/build-security-server.sh --push --platforms linux/amd64,linux/arm64
)
```

`core/scripts/images/build-security-server.sh` honours `IMAGE_REGISTRY`; images are tagged and pushed there.

## Deploy

```bash
./scripts/start-env.sh \
  --env=eks \
  --skip-images \
  --skip-forward \
  --skip-init
```

Flags explained:

- `--skip-images` — images already in ECR
- `--skip-forward` — on EKS, access is via LoadBalancer / Ingress (not port-forward)
- `--skip-init` — `init-ss2.sh` targets the local LXD stack and is inapplicable on EKS

## Per-concern EKS deltas

| Aspect                   | KinD (dev/test)                             | EKS                                                                                                          |
|--------------------------|---------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Cluster provisioning     | `kind_cluster` role                         | **Skipped** (`cluster_provider: eks` gates the role off)                                                     |
| Image registry           | `localhost:5555` via containerd mirror      | ECR — `xroad_image_registry` + IRSA on service account for pull                                              |
| OpenBao TLS              | Self-signed P384 cert + k8s Secret          | cert-manager `Certificate` CR (recommended). For quick bring-up, `openbao_tls_mode: self_signed` still works |
| External service bridges | 3× helm releases (→ `host.docker.internal`) | `external_service_bridges: []` — real CS/CA/SS reachable via DNS                                             |
| Access                   | `kubectl port-forward`                      | `Service.type: LoadBalancer` (creates NLB) or Ingress via AWS Load Balancer Controller                       |
| Storage class            | `standard` (local-path)                     | `gp3` — set `storage_class: gp3`; DB value templates parameterise `persistence.storageClass`                 |
| Secrets                  | k8s Secret                                  | External Secrets Operator → AWS Secrets Manager                                                              |
| IAM                      | n/a                                         | IRSA — annotate SAs with `eks.amazonaws.com/role-arn: arn:aws:iam::<account>:role/<role>`                    |
| Node count / sizing      | 1 CP + 2 workers                            | Managed node group; 3+ nodes minimum for HA OpenBao                                                          |

## cert-manager issuer for OpenBao (recommended)

```yaml
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: openbao-selfsigned
  namespace: ss
spec:
  selfSigned: { }
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: openbao-server-tls
  namespace: ss
spec:
  secretName: openbao-server-tls        # name the openbao role expects
  duration: 8760h
  renewBefore: 720h
  privateKey:
    algorithm: ECDSA
    size: 384
  commonName: openbao
  dnsNames:
    - openbao
    - openbao.ss.svc.cluster.local
  issuerRef:
    name: openbao-selfsigned
    kind: Issuer
```

The `openbao` role does **not** currently implement `openbao_tls_mode: cert_manager`; setting
that mode hard-fails. For EKS bring-up, keep `openbao_tls_mode: self_signed` (the default
in `inventory/eks/group_vars/all.yml`). Migrate to `cert_manager` only after the role adds
the detect-and-skip-generation path.

## IRSA for image pulls from ECR

Attach this policy to the IAM role bound to the security-server service account:

```json
{
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    }
  ]
}
```

Annotate the SA via Helm values (add to `security-server-values.yaml.j2`):

```yaml
serviceAccount:
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::<account>:role/xroad-ecr-pull
```

## Verification

```bash
kubectl get pods -n ss                            # all Running / Ready
kubectl get svc -n ss                             # LoadBalancer ingress populated
kubectl get certificate -n ss                     # Ready=True (if cert-manager)
```

Hit the proxy-ui-api over the NLB hostname shown in `kubectl get svc`.

## Teardown

```bash
./scripts/delete-env.sh --env=eks --keep-cluster
```

`--keep-cluster` is default-recommended on EKS so the managed cluster, node groups, and addons stay put.
