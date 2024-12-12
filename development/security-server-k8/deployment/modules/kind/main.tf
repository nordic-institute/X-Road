
# modules/kind/main.tf
resource "kind_cluster" "xroad" {
  name = var.cluster_name
  wait_for_ready = true

  kind_config {
    kind = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"
      kubeadm_config_patches = [
        <<-PATCH
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
        PATCH
      ]

      extra_port_mappings {
        container_port = 4000
        host_port = 4000
        protocol = "TCP"
      }

      # Commented ports as in original YAML
      # extra_port_mappings {
      #   container_port = 443
      #   host_port = 443
      #   protocol = "TCP"
      # }
    }
  }
}

resource "null_resource" "load_images" {
  depends_on = [kind_cluster.xroad]

  triggers = {
    cluster_id = kind_cluster.xroad.id
    image_list = join(",", var.images)  # Triggers on image list changes
    always_run = timestamp()  # WARNING: This will run image refresh on every apply
  }

  provisioner "local-exec" {
    command = <<-EOT
      echo "Loading images into Kind cluster..."
      %{for image in var.images~}
      echo "Loading ${image}..."
      kind load docker-image ${image} --name ${var.cluster_name} || exit 1
      %{endfor~}
      echo "All images loaded successfully"
    EOT
  }
}