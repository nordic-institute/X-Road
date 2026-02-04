resource "helm_release" "external_service_bridge" {
  name      = "xroad-bridge-${var.name}"
  namespace = var.namespace
  create_namespace = true

  repository = var.external_service_bridge_chart_repo
  chart = var.external_service_bridge_chart
  version = var.external_service_bridge_chart_version

  timeout = 30
  depends_on = []
  wait = true        # Wait for resources to be ready

  values = [
    yamlencode({
      service = {
        name      = var.name,
        namespace = var.namespace
      }
      externalHost = var.external_host
      ports        = var.ports
    })
  ]
}
