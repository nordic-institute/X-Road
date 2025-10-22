resource "helm_release" "postgresql_openbao" {
  name      = "openbao-db"
  namespace = var.namespace
  create_namespace = true

  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "18.0.12"

  values = [yamlencode(var.openbao_db_override_values)]
}

resource "helm_release" "openbao_secret_store" {
  name      = "openbao"
  namespace = var.namespace
  create_namespace = true

  repository = "https://openbao.github.io/openbao-helm"
  chart      = "openbao"
  version    = "0.19.0"

  depends_on = [helm_release.postgresql_openbao]

  values = [yamlencode(var.openbao_override_values)]
}

resource "helm_release" "openbao_secret_store_init" {
  name      = "openbao-initializer"
  namespace = var.namespace

  repository = var.openbao_init_chart_repo
  chart = var.openbao_init_chart
  version = var.openbao_init_chart_version

  depends_on = [helm_release.openbao_secret_store]
}
