resource "helm_release" "openbao_secret_store" {
  name       = "openbao"
  repository = "https://openbao.github.io/openbao-helm"
  chart      = "openbao"

  set {
    name  = "server.dev.enabled"
    value = var.openbao_dev
  }

  set {
    name  = "server.ha.enabled"
    value = "true"
  }
  set {
    name  = "server.ha.raft.enabled"
    value = "true"
  }
  set {
    name  = "server.ha.raft.setNodeId"
    value = "true"
  }
  set {
    name  = "server.ha.raft.config"
    value = <<EOT
  ui = false

  listener "tcp" {
    tls_disable = 1
    address = "[::]:8200"
    cluster_address = "[::]:8201"
  }

  storage "raft" {
    path    = "/openbao/data"
  }

  service_registration "kubernetes" {}

  EOT
  }
}

resource "helm_release" "postgresql_serverconf" {
  name       = "serverconf-db-${var.environment}"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  set {
    name  = "image.tag"
    value = local.versions.postgres.engine
  }

  set {
    name  = "auth.username"
    value = "serverconf"
  }
  set {
    name  = "auth.password"
    value = var.postgres_serverconf_password
  }
  set {
    name  = "primary.resources.requests.memory"
    value = "64Mi"
  }
  set {
    name  = "primary.resources.limits.memory"
    value = "128Mi"
  }
}

resource "helm_release" "postgresql_messagelog" {
  name       = "messagelog-db-${var.environment}"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  set {
    name  = "image.tag"
    value = local.versions.postgres.engine
  }

  set {
    name  = "auth.username"
    value = "messagelog"
  }
  set {
    name  = "auth.password"
    value = var.postgres_messagelog_password
  }
  set {
    name  = "primary.resources.requests.memory"
    value = "64Mi"
  }
  set {
    name  = "primary.resources.limits.memory"
    value = "256Mi"
  }
}

resource "helm_release" "security_server" {
  name  = "xroad-${var.environment}"
  chart = "${path.module}/../charts/security_server"
  timeout = 90 # TODO make it configurable

  depends_on = [
    var.images_loaded,
    helm_release.openbao_secret_store,
    helm_release.postgresql_serverconf,
    helm_release.postgresql_messagelog,
  ]

  set {
    name  = "environment"
    value = var.environment
  }
}
