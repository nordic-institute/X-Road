#!/bin/bash

get_db_prop() {
  crudini --get '/etc/xroad/db.properties' '' "$1" 2>/dev/null || echo -n "$2"
}

prepare_db_props() {
  db_host=$(get_db_prop 'host' '127.0.0.1')
  db_port=$(get_db_prop 'port' '5432')
  db_database=$(get_db_prop 'database' 'centerui_production')
  db_username=$(get_db_prop 'username' 'centerui')
  db_password=$(get_db_prop 'password')
}

encode_token() {
  echo -n "$1" | sha256sum -b | cut -d' ' -f1
}

api_token_configured() {
  local token=$(crudini --get /etc/xroad/conf.d/local.ini "$1" api-token 2>/dev/null)
  if [[ -z "$token" ]]; then
    echo "api-token property not configured for $1"
    return 1
  else
    prepare_db_props
    local apikeys=$(
      PGDATABASE="$db_database" PGUSER="$db_username" PGPASSWORD="$db_password" psql -h "$db_host" -p "$db_port" -qtA -c \
      "SELECT encodedkey FROM apikey a INNER JOIN apikey_roles r ON a.id = r.apikey_id WHERE r.role = 'XROAD_MANAGEMENT_SERVICE';"
    )
    local encoded_token=$(encode_token $token)
    while read line ; do
      if [[ "$encoded_token" == "$line" ]]; then
        return 0
      fi
    done <<< $apikeys
    echo "Configured api-token for $1 not found in database"
    return 1
  fi
}

if [[ "$1" != "management-service" && "$1" != "registration-service" ]]; then
    echo "Must supply either \"management-service\" or \"registration-service\" as input argument"
    exit 1
fi

echo "Checking whether a valid API KEY with Management Service role is configured for $1..."
if api_token_configured $1; then
  echo "A valid API KEY with Management Service role already configured"
else
  echo "Generating & configuring a new API KEY with Management Service role for $1..."
  token=$(tr -C -d "[:alnum:]" </dev/urandom | head -c32)
  encoded_token=$(encode_token $token)
  prepare_db_props
  PGDATABASE="$db_database" PGUSER="$db_username" PGPASSWORD="$db_password" psql -h "$db_host" -p "$db_port" -qtA -c \
  "INSERT INTO apikey(id, encodedkey) VALUES ((SELECT NEXTVAL('hibernate_sequence')), '$encoded_token');
  INSERT INTO apikey_roles(apikey_id,role) VALUES ((SELECT id FROM apikey WHERE encodedkey = '$encoded_token'), 'XROAD_MANAGEMENT_SERVICE');"
  crudini --set /etc/xroad/conf.d/local.ini "$1" api-token "$token"
  echo "New API KEY successfully configured"
fi
