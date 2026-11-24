#!/bin/bash

# Starts the OpenBao server as the OpenBao user. The environment file is read as data, not run as shell
# code, so a database password keeps every character it has.

. /usr/share/xroad/scripts/_openbao_env.sh

env_file=/etc/openbao/xroad-openbao.env
base_config=/usr/share/xroad/openbao/xroad-openbao.hcl
node_config=/etc/xroad-secret-store/node.hcl

if [[ ! -f $env_file ]]; then
  echo >&2 "Unable to find OpenBao environment file at $env_file"
  exit 1
fi

BAO_PG_CONNECTION_URL=$(read_env_value "$env_file" BAO_PG_CONNECTION_URL)

if [[ -z $BAO_PG_CONNECTION_URL ]]; then
  echo >&2 "BAO_PG_CONNECTION_URL is missing or empty in $env_file"
  exit 1
fi

export BAO_PG_CONNECTION_URL

config_args=(-config="$base_config")
if [[ -f $node_config ]]; then
  config_args+=(-config="$node_config")
fi

exec /usr/bin/bao server "${config_args[@]}"
