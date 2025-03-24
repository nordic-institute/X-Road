
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

/*
resource "null_resource" "load_images" {
  depends_on = [kind_cluster.xroad-cluster]

  triggers = {
    cluster_id = kind_cluster.xroad-cluster.id
    image_list = join(",", var.images)  # Triggers on image list changes
    always_run = timestamp()  # WARNING: This will run image refresh on every apply
  }

  # TODO: mirror localhost registry to the kind cluster instead of caching them locally & loading into cluster
  provisioner "local-exec" {
    command = <<EOF
      echo "Loading images into Kind cluster..."
      %{for image in var.images~}
      echo "Loading ${image}..."
      docker pull --platform linux/arm64 ${var.images_registry}/${image} || exit 1
      kind load docker-image ${var.images_registry}/${image} --name ${var.kind_cluster_name} --nodes ${var.kind_cluster_name}-worker || exit 1
      %{endfor~}
      echo "All images loaded successfully"
    EOF
  }
}
*/
