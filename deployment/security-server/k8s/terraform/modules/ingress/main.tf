resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "4.12.0"
  namespace        = "ingress-nginx"
  create_namespace = true

  values = [
    <<-EOF
      controller:
        hostPort:
          enabled: true
        terminationGracePeriodSeconds: 0
        service:
          type: "NodePort"
        watchIngressWithoutClass: true
        nodeSelector:
          ingress-ready: "true"
        tolerations:
          - effect: "NoSchedule"
            key: "node-role.kubernetes.io/master"
            operator: "Equal"
          - effect: "NoSchedule"
            key: "node-role.kubernetes.io/control-plane"
            operator: "Equal"
        publishService:
          enabled: false
        extraArgs:
          publish-status-address: "localhost"
    EOF
  ]
}

resource "null_resource" "wait_for_ingress_nginx" {
  depends_on = [helm_release.ingress_nginx]

  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command = <<EOF
      echo "Waiting for the nginx ingress controller..."
      kubectl wait --namespace ${helm_release.ingress_nginx.namespace} \
        --for=condition=ready pod \
        --selector=app.kubernetes.io/component=controller \
        --timeout=90s
    EOF
  }
}

resource "kubernetes_ingress_v1" "ingress" {
  depends_on = [
    null_resource.wait_for_ingress_nginx
  ]

  metadata {
    name = "ingress"
    namespace = var.namespace
    annotations = {
      "nginx.ingress.kubernetes.io/backend-protocol": "HTTPS"
    }
  }
  spec {
    rule {
      http {
        path {
          path = "/"
          path_type = "Prefix"
          backend {
            service {
                name = var.target_service
                port {
                    number = var.target_port
                }
            }
          }
        }
      }
    }
  }
}