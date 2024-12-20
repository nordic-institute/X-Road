resource "helm_release" "openbao_secret_store" {
  name      = "openbao"
  namespace = var.namespace

  repository = "https://openbao.github.io/openbao-helm"
  chart      = "openbao"

  values = [
    yamlencode({
      global = {
        namespace = var.namespace
      }
      server = {
        dev = {
          enabled = var.openbao_dev
        }
        ha = {
          enabled = true
          raft = {
            enabled   = true
            setNodeId = true
          }
        }
        image = {
          repository = "openbao/openbao"
          tag        = var.openbao_version
        }
      }
    })
  ]
}

resource "helm_release" "openbao_secret_store_init" {
  name      = "openbao-initializer"
  namespace = var.namespace

  chart = "${path.module}/../../charts/openbao_init"

  values = [
    yamlencode({
      databaseCredentials = {
        serverConf = {
          username = var.postgres_serverconf_username
          password = var.postgres_serverconf_password
        }
        messageLog = {
          username = var.postgres_messagelog_username
          password = var.postgres_messagelog_password
        }
      }
    })
  ]

  depends_on = [helm_release.openbao_secret_store]
}
