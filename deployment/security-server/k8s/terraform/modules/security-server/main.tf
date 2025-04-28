resource "helm_release" "postgresql_serverconf" {
  name      = "security-server-serverconf-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "15.5.38"

  values = [
    yamlencode({
      fullnameOverride = "db-serverconf"
      auth = {
        database           = "serverconf"
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
  version    = "15.5.38"

  values = [
    yamlencode({
      fullnameOverride = "db-messagelog"
      auth = {
        database           = "messagelog"
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

resource "helm_release" "postgresql_opmonitor" {
  count = var.op_monitor_enabled ? 1 : 0

  name      = "security-server-opmonitor-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "15.5.38"

  values = [
    yamlencode({
      fullnameOverride = "db-opmonitor"
      auth = {
        database           = "op-monitor"
        username           = var.opmonitor_db_user
        password           = var.opmonitor_db_user_password
        //admin user for setup
        enablePostgresUser = true
        postgresPassword   = var.opmonitor_db_postgres_password
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

  chart = "${path.module}/../../../charts/security-server"
  timeout = 90 # TODO make it configurable

  wait = true

  values = [
    yamlencode({
      init = {
        serverconf = {
          dbUsername       = var.serverconf_db_user
        }
        messagelog = {
          dbUsername       = var.messagelog_db_user
        }
        opmonitor = {
          dbUsername       = var.opmonitor_db_user
        }
      }
      services = {
        configuration-client = {
          env = {
            XROAD_CONFIGURATION_CLIENT_UPDATE_INTERVAL = tostring(var.configuration_client_update_interval)
          }
        }
        proxy = {
          env = {
            XROAD_PROXY_ADDON_OP_MONITOR_ENABLED = tostring(var.op_monitor_enabled)
          }
        }
        op-monitor = {
          enabled = tostring(var.op_monitor_enabled)
        }
      }
    })
  ]

}