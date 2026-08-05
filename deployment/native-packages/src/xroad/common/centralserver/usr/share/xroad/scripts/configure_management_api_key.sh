#!/bin/bash

source /usr/share/xroad/scripts/_read_cs_db_properties.sh

encode_token() {
  echo -n "$1" | sha256sum -b | cut -d' ' -f1
}

run_psql() {
  PGOPTIONS="${PGOPTIONS_EXTRA-}" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" \
    psql -h "${PGHOST:-$db_host}" -p "${PGPORT:-$db_port}" -qtA -c "$1"
}

token_hash_in_db() {
  local encoded_token="$1"
  local apikeys
  apikeys=$(run_psql "SELECT encodedkey FROM apikey a INNER JOIN apikey_roles r ON a.id = r.apikey_id WHERE r.role = 'XROAD_MANAGEMENT_SERVICE';")
  while read -r line; do
    if [[ "$encoded_token" == "$line" ]]; then
      return 0
    fi
  done <<< "$apikeys"
  return 1
}

store_token() {
  run_psql "INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at)
    VALUES ('xroad.${1}.api-token', '$2', now(), now()) ON CONFLICT DO NOTHING;"
}

api_token_configured() {
  local token
  token=$(run_psql "SELECT property_value FROM configuration_properties WHERE property_key = 'xroad.${1}.api-token';")
  if [[ -n "$token" ]]; then
    if token_hash_in_db "$(encode_token "$token")"; then
      return 0
    fi
    echo "Configured api-token for $1 not found in database"
    return 1
  fi

  # Older releases stored the token in local-tls.yaml; migrate a still-valid one into the database.
  local legacy_token
  legacy_token=$(/usr/share/xroad/scripts/yaml_helper.sh get /etc/xroad/conf.d/local-tls.yaml "xroad.${1}.api-token" 2>/dev/null)
  if [[ -n "$legacy_token" ]] && token_hash_in_db "$(encode_token "$legacy_token")"; then
    store_token "$1" "$legacy_token"
    echo "Migrated api-token for $1 from local-tls.yaml to the database"
    return 0
  fi

  echo "api-token property not configured for $1"
  return 1
}

if [[ "$1" != "management-service" && "$1" != "registration-service" ]]; then
    echo "Must supply either \"management-service\" or \"registration-service\" as input argument"
    exit 1
fi

prepare_db_props

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

echo "Checking whether a valid API KEY with Management Service role is configured for $1..."
if api_token_configured $1; then
  echo "A valid API KEY with Management Service role already configured"
else
  echo "Generating & configuring a new API KEY with Management Service role for $1..."
  token=$(tr -C -d "[:alnum:]" </dev/urandom | head -c32)
  encoded_token=$(encode_token $token)

  run_psql "INSERT INTO apikey(id, encodedkey) VALUES ((SELECT NEXTVAL('hibernate_sequence')), '$encoded_token');
  INSERT INTO apikey_roles(apikey_id,role) VALUES ((SELECT id FROM apikey WHERE encodedkey = '$encoded_token'), 'XROAD_MANAGEMENT_SERVICE');"
  if [ $? -ne 0 ] ; then
        echo "Failed to finish configuring new API KEY"
        exit 1
  fi
  store_token "$1" "$token"
  if [ $? -ne 0 ] ; then
        echo "Failed to store the API token in the database"
        exit 1
  fi
  echo "New API KEY successfully configured"
fi
