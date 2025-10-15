resource "helm_release" "postgresql_openbao" {
  name      = "openbao-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "18.0.12"

  values = [
    yamlencode({
      fullnameOverride = "db-openbao"
      image = {
        repository = "bitnamilegacy/postgresql"
        tag = "16.6.0"
      }
      auth = {
        database           = "openbao"
        username           = var.openbao_db_user
        password           = var.openbao_db_user_password
      }
      primary = {
        resources = {
          requests = {
            memory = "64Mi"
          }
          limits = {
            memory = "256Mi"
          }
        }
      }
    })
  ]
}

resource "helm_release" "openbao_secret_store" {
  name      = "openbao"
  namespace = var.namespace
  create_namespace = true

  repository = "https://openbao.github.io/openbao-helm"
  chart      = "openbao"
  version    = "0.10.1"

  depends_on = [helm_release.postgresql_openbao]

  values = [
    yamlencode({
      global = {
        namespace = var.namespace
      }
      server = {
        ha = {
          enabled = true
          config = <<-EOF
            ui = true
            listener "tcp" {
              tls_disable = 1
              address = "[::]:8200"
              cluster_address = "[::]:8201"
            }
            storage "postgresql" {
              ha_enabled = "true"
            }
            service_registration "kubernetes" {}
          EOF
        }
        extraSecretEnvironmentVars = [
          { envName: "BAO_PG_PASSWORD", secretName: "db-openbao", secretKey: "password" }
        ]
        extraEnvironmentVars = {
          # https://github.com/openbao/openbao-helm/issues/37 - Once this gets resolved, use $(BAO_PG_PASSWORD) instead of ${var.openbao_db_user_password}
          BAO_PG_CONNECTION_URL = "postgres://${var.openbao_db_user}:${var.openbao_db_user_password}@db-openbao.ss.svc.cluster.local:5432/openbao"
        }
      }
    })
  ]
}

resource "helm_release" "openbao_secret_store_init" {
  name      = "openbao-initializer"
  namespace = var.namespace
  chart = "${path.module}/../../../../../deployment/security-server/k8s/charts/openbao-init"

  depends_on = [helm_release.openbao_secret_store]
}
