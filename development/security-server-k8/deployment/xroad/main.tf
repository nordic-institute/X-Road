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
        database           = "serverconf"
        username           = var.postgres_serverconf_username
        password = var.postgres_serverconf_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.postgres_serverconf_password
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
        database           = "messagelog"
        username           = var.postgres_messagelog_username
        password = var.postgres_messagelog_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.postgres_messagelog_password
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

resource "helm_release" "postgresql_ds_data_plane" {
  name       = "ds-data-plane-db-${var.environment}"
  namespace  = var.namespace
  count      = var.data_spaces_enabled ? 1 : 0
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  values = [
    yamlencode({
      fullnameOverride = "db-ds-data-plane"
      image = {
        tag = local.versions.postgres.engine
      }
      auth = {
        database           = "ds-data-plane"
        username           = var.postgres_ds_username
        password = var.postgres_ds_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.postgres_ds_password
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

resource "helm_release" "postgresql_ds_control_plane" {
  name      = "ds-control-plane-db-${var.environment}"
  namespace = var.namespace
  count     = var.data_spaces_enabled ? 1 : 0

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  values = [
    yamlencode({
      fullnameOverride = "db-ds-control-plane"
      image = {
        tag = local.versions.postgres.engine
      }
      auth = {
        database           = "ds-control-plane"
        username           = var.postgres_ds_username
        password = var.postgres_ds_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.postgres_ds_password
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

resource "helm_release" "postgresql_ds_identity_hub" {
  name      = "ds-identity-hub-db-${var.environment}"
  namespace = var.namespace
  count     = var.data_spaces_enabled ? 1 : 0

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = local.versions.postgres.chart

  values = [
    yamlencode({
      fullnameOverride = "db-ds-identity-hub"
      image = {
        tag = local.versions.postgres.engine
      }
      auth = {
        database           = "ds-identity-hub"
        username           = var.postgres_ds_username
        password = var.postgres_ds_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.postgres_ds_password
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

  values = [
    yamlencode({
      global = {
        environment       = var.environment,
        dataSpacesEnabled = var.data_spaces_enabled,
      }
      init = {
        serverconf = {
          username = var.postgres_serverconf_username
          password = var.postgres_serverconf_password
        }
        messagelog = {
          username = var.postgres_messagelog_username
          password = var.postgres_messagelog_password
        }
        dsControlPlane = {
          username = var.postgres_ds_username
          password = var.postgres_ds_password
        }
        dsDataPlane = {
          username = var.postgres_ds_username
          password = var.postgres_ds_password
        }
        dsIdentityHub = {
          username = var.postgres_ds_username
          password = var.postgres_ds_password
        }
      }
    })
  ]
}
