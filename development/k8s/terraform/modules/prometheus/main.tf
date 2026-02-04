resource "helm_release" "prometheus" {
  name             = "prometheus"
  namespace        = var.namespace
  create_namespace = true

  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = "80.4.1"

  values = [yamlencode(var.prometheus_override_values)]
}
