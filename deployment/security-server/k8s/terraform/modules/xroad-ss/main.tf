resource "helm_release" "postgresql_serverconf" {
  name      = "security-server-serverconf-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "15.1.0"

  values = [
    yamlencode({
      fullnameOverride = "db-serverconf"
      auth = {
        database           = var.serverconf_db_name
        username           = var.serverconf_db_user
        password           = var.serverconf_db_user_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.serverconf_db_postgres_password
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

resource "helm_release" "postgresql_messagelog" {
  name      = "security-server-messagelog-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "15.1.0"

  values = [
    yamlencode({
      fullnameOverride = "db-messagelog"
      auth = {
        database           = var.messagelog_db_name
        username           = var.messagelog_db_user
        password           = var.messagelog_db_user_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.messagelog_db_postgres_password
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
  name      = "security-server"
  namespace = var.namespace
  create_namespace = true

  chart = "${path.module}/../../../charts/security_server"
  timeout = 90 # TODO make it configurable

  #TODO might differ between environments
  # cleanup_on_fail = true   # Clean up on failed install/upgrade
  # atomic = true        # Roll back on failure
  recreate_pods = true # Force pod recreation
  wait = true          # Wait for resources to be ready

  values = [
    yamlencode({
      init = {
        serverconf = {
          database = var.serverconf_db_name
          postgres_password = var.serverconf_db_postgres_password
          db_username = var.serverconf_db_user

        }
        messagelog = {
          database = var.messagelog_db_name
          postgres_password = var.messagelog_db_postgres_password
          db_username = var.messagelog_db_user
        }
      }
      services = {
        configuration-client = {
          env = {
            XROAD_CONFIGURATION_CLIENT_UPDATE_INTERVAL = var.configuration_client_update_interval
          }
        }
      }
    })
  ]

}