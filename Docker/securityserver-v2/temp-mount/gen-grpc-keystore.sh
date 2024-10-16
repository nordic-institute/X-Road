#!/bin/bash
set -e
shopt -s nullglob

gen_pw() {
  head -c 24 /dev/urandom | base64 | tr "/+" "_-"
}

# generate EC keypair and self-signed certificate for internal transport
gen_grpc_internal_keypair() {
  umask 077
  local keystore_pw="$(gen_pw)"
  local keystore=xroad-grpc-internal-keystore.p12
  local env_file=xroad-grpc-internal-env.properties

  if [[ ! -f "$keystore" && ! -f "$env_file" ]]; then
      PW="$keystore_pw" \
      keytool -genkeypair -alias grpc-internal \
       -storetype PKCS12 \
       -keyalg EC -groupname secp256r1 \
       -sigalg SHA256withECDSA \
       -keystore "$keystore" \
       -dname "CN=127.0.0.1" \
       -ext "SAN:c=DNS:localhost,IP:127.0.0.1,DNS:confclient,DNS:proxy,DNS:signer" \
       -validity 3650 \
       -storepass:env PW \
       -keypass:env PW


  echo "Password: $keystore_pw"
      cat <<EOF >"$env_file"
XROAD_COMMON_GRPC_INTERNAL_KEYSTORE_PASSWORD="$keystore_pw"
XROAD_COMMON_GRPC_INTERNAL_TRUSTSTORE_PASSWORD="$keystore_pw"
EOF
  fi
}

gen_grpc_internal_keypair
