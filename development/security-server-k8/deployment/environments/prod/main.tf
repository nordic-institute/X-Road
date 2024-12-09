module "xroad" {
  source = "../xroad"

  environment = "prod"
  postgres_serverconf_password = var.postgres_serverconf_password
  postgres_messagelog_password = var.postgres_messagelog_password
}