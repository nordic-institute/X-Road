resource "helm_release" "postgresql_serverconf" {
  name      = "serverconf-db-${var.environment}"
  namespace = var.namespace

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  values = [
    yamlencode({
      fullnameOverride = "db-serverconf"
      image = {
        tag = local.versions.postgres.engine
      }
      auth = {
        database = "serverconf"
        username = var.postgres_serverconf_username
        password = var.postgres_serverconf_password
      }
      primary = {
        resources = {
          requests = {
            memory = "64Mi"
          }
          limits = {
            memory = "128Mi"
          }
        }
      }
    })
  ]
}

resource "helm_release" "postgresql_messagelog" {
  name      = "messagelog-db-${var.environment}"
  namespace = var.namespace

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  values = [
    yamlencode({
      fullnameOverride = "db-messagelog"
      image = {
        tag = local.versions.postgres.engine
      }
      auth = {
        database = "messagelog"
        username = var.postgres_messagelog_username
        password = var.postgres_messagelog_password
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


resource "helm_release" "security_server" {
  name      = "xroad-${var.environment}"
  namespace = var.namespace

  chart = "${path.module}/../charts/security_server"
  timeout = 60 # TODO make it configurable

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
