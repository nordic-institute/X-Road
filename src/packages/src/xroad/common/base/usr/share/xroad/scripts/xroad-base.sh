#!/bin/bash
set -e
shopt -s nullglob
XROAD_CONF_PATH="${XROAD_CONF_PATH:-/etc/xroad}"

gen_pw() {
  head -c 24 /dev/urandom | base64 | tr "/+" "_-"
}

get_prop() {
  crudini --get "$1" "$2" "$3" 2>/dev/null || echo "$4"
}

use_secure_akka_transport() {
  local value="true"
  for f in "$XROAD_CONF_PATH/conf.d/common.ini" "$XROAD_CONF_PATH"/conf.d/override-*.ini "$XROAD_CONF_PATH/conf.d/local.ini"; do
    value=$(get_prop "$f" common akka-use-secure-remote-transport "$value")
  done
  [[ $value == "true" ]]
}

# generate EC keypair and self-signed certificate for akka remoting
gen_akka_keypair() {
  umask 077
  local keystore_pw="$(gen_pw)"
  local keystore=/var/run/xroad/xroad-akka-keystore.p12
  local env_file=/var/run/xroad/xroad-akka-env.properties

  if [[ ! -f "$keystore" && ! -f "$env_file" ]]; then
    if use_secure_akka_transport; then
      PW="$keystore_pw" keytool -genkeypair -alias akka -keyalg EC -keysize 256 -sigalg SHA256withECDSA -validity 3650 \
        -dname "cn=xroad-akka" -keystore "$keystore" -deststoretype pkcs12 -storepass:env PW -keypass:env PW
      chown xroad:xroad "$keystore"

      cat <<EOF >"$env_file"
XROAD_COMMON_AKKA_REMOTE_TRANSPORT=tls-tcp
XROAD_COMMON_AKKA_KEYSTORE="$keystore"
XROAD_COMMON_AKKA_KEYSTORE_PASSWORD="$keystore_pw"
XROAD_COMMON_AKKA_TRUSTSTORE="$keystore"
XROAD_COMMON_AKKA_TRUSTSTORE_PASSWORD="$keystore_pw"
EOF
    else
      echo "XROAD_COMMON_AKKA_REMOTE_TRANSPORT=tcp" >"$env_file"
    fi

    chown xroad:xroad "$env_file"
  fi
}

gen_akka_keypair
