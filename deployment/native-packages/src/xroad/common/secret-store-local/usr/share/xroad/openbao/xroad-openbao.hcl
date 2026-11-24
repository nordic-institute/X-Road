ui = true

listener "tcp" {
  address       = "127.0.0.1:8200"
  tls_cert_file = "/opt/openbao/tls/tls.crt"
  tls_key_file  = "/opt/openbao/tls/tls.key"
}

storage "postgresql" {
  ha_enabled = "true"
}

api_addr = "https://127.0.0.1:8200"
