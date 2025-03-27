
resource "kind_cluster" "xroad-cluster" {
  name = var.kind_cluster_name
  wait_for_ready = true
  kubeconfig_path = pathexpand(var.kube_config_path)

  kind_config {
    kind = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"
    node {
      role = "control-plane"
    }
    node {
      role = "worker"

    }

    containerd_config_patches = [
      <<-EOF
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:5555"]
        endpoint = ["http://host.docker.internal:5555"]
      EOF
    ]
  }
}
