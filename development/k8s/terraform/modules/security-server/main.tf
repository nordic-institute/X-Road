resource "helm_release" "postgresql_serverconf" {
  name      = "security-server-serverconf-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "18.0.12"

  values = [yamlencode(var.serverconf_db_override_values)]
}

resource "helm_release" "postgresql_messagelog" {
  name      = "security-server-messagelog-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "18.0.12"

  values = [yamlencode(var.messagelog_db_override_values)]
}

resource "helm_release" "postgresql_opmonitor" {
  name      = "security-server-opmonitor-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "18.0.12"

  values = [yamlencode(var.opmonitor_db_override_values)]
}

resource "helm_release" "security_server" {
  name      = "security-server"
  namespace = var.namespace
  timeout = 90 # TODO make it configurable
  # wait=false because message-log-archives-pvc is consumed only by a scheduled
  # Job (not a Deployment), so with WaitForFirstConsumer storage it remains
  # Pending at install time — which would block `helm --wait` indefinitely.
  wait = false

  repository = var.security_server_chart_repo
  chart = var.security_server_chart
  version = var.security_server_chart_version

  values = [yamlencode(var.security_server_override_values)]
}
