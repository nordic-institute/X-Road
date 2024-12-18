resource "helm_release" "external_service_bridge" {
  name      = "xroad-bridge-${var.name}"
  namespace = var.namespace

  chart   = "${path.module}/../../charts/external_service_bridge"
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
