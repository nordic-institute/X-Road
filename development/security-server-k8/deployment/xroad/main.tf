resource "helm_release" "postgresql_serverconf" {
  name       = "serverconf-db-${var.environment}"
  namespace  = var.namespace

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
  namespace  = var.namespace

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
  namespace  = var.namespace

  chart = "${path.module}/../charts/security_server"
  timeout = 90 # TODO make it configurable

  depends_on = [
    var.images_loaded,
    helm_release.postgresql_serverconf,
    helm_release.postgresql_messagelog,
  ]

  #TODO might differ between environments
  # cleanup_on_fail = true   # Clean up on failed install/upgrade
  # atomic = true        # Roll back on failure
  recreate_pods = true        # Force pod recreation
  wait = true        # Wait for resources to be ready

  set {
    name  = "environment"
    value = var.environment
  }
}
