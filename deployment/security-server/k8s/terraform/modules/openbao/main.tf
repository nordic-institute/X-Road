resource "helm_release" "openbao_secret_store" {
  name      = "openbao"
  namespace = var.namespace
  create_namespace = true

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

  chart = "${path.module}/../../../charts/openbao-init"

  depends_on = [helm_release.openbao_secret_store]
}
