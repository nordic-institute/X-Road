output "kubeconfig_path" {
  value = kind_cluster.xroad.kubeconfig_path
  description = "Path to the kubeconfig file for the Kind cluster"
}

output "images_loaded" {
  value = null_resource.load_images.id
  description = "ID indicating images have been loaded"
}