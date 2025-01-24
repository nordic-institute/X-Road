ui            = false
cluster_addr  = "https://127.0.0.1:8201"
api_addr      = "https://127.0.0.1:8200"

storage "raft" {
  path = "/opt/openbao/data"
  node_id = "node1"
}

listener "tcp" {
  address       = "127.0.0.1:8200"
  tls_cert_file = "/opt/openbao/tls/tls.crt"
  tls_key_file  = "/opt/openbao/tls/tls.key"
}

