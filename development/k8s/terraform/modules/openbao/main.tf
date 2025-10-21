resource "helm_release" "postgresql_openbao" {
  name      = "openbao-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "18.0.12"

  values = [yamlencode(var.openbao_db_override_values)]
}

resource "tls_private_key" "openbao_server" {
  algorithm   = "ECDSA"
  ecdsa_curve = "P384"
}

resource "tls_self_signed_cert" "openbao_server" {
  private_key_pem = tls_private_key.openbao_server.private_key_pem

  subject {
    common_name  = "openbao"
  }

  dns_names = [
    "localhost",
    "openbao.ss.svc.cluster.local"
  ]

  validity_period_hours = 43800 # 5 years

  allowed_uses = [
    "key_encipherment",
    "digital_signature",
    "server_auth",
  ]
}

resource "null_resource" "openbao_server_tls_secret" {
  provisioner "local-exec" {
    command = <<EOT
      cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: openbao-server-tls
  namespace: ${var.namespace}
type: kubernetes.io/tls
data:
  tls.crt: ${base64encode(tls_self_signed_cert.openbao_server.cert_pem)}
  tls.key: ${base64encode(tls_private_key.openbao_server.private_key_pem)}
EOF
    EOT
  }
}

resource "helm_release" "openbao_secret_store" {
  name      = "openbao"
  namespace = var.namespace
  create_namespace = true

  repository = "https://openbao.github.io/openbao-helm"
  chart      = "openbao"
  version    = "0.19.0"

  depends_on = [helm_release.postgresql_openbao, null_resource.openbao_server_tls_secret]

  values = [yamlencode(var.openbao_override_values)]
}

resource "helm_release" "openbao_secret_store_init" {
  name      = "openbao-initializer"
  namespace = var.namespace

  repository = var.openbao_init_chart_repo
  chart = var.openbao_init_chart
  version = var.openbao_init_chart_version

  depends_on = [helm_release.openbao_secret_store]
}
