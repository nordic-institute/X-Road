
resource "kind_cluster" "xroad_cluster" {
  name = var.kind_cluster_name
  wait_for_ready = true
  kubeconfig_path = pathexpand(var.kubeconfig_path)

  kind_config {
    kind = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"
    }

    node {
      role = "worker"
    }

    node {
      role = "worker"
    }

    containerd_config_patches = var.containerd_config_patches
  }
}
