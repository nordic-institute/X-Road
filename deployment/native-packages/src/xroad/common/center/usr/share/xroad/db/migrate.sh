#!/bin/bash

source /usr/share/xroad/scripts/_read_cs_db_properties.sh

log() { echo >&2 "$@"; }
die() {
  log "$@"
  exit 1
}

get_prop() {
  crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"
}

get_required_db_prop() {
  local -r prop_value=$(get_prop "$db_properties" "$1" "")
  if [ -z "$prop_value" ]; then
    echo "Unable to read required parameter $1 from ${db_properties}"
  fi
  echo -n "$prop_value"
}

get_required_root_prop() {
  local -r prop_value=$(get_prop "$root_properties" "$1" "")
  if [ -z "$prop_value" ]; then
    echo "$2"
  fi
  echo -n "$prop_value"
}

migrate() {
  local db_properties=/etc/xroad/db.properties
  local root_properties=/etc/xroad.properties

  # read parameters from db.properties
  prepare_db_props

  local -r db_admin_user=$(get_required_root_prop 'centerui.database.admin_user' "$db_user")
  local -r db_admin_password=$(get_required_root_prop 'centerui.database.admin_password' "$db_password")

  if [[ -z "$db_user" || -z "$db_schema" || -z "$db_host" || -z "$db_port" || -z "$db_database" || -z "$db_password" ]]; then
    die "Running database migrations failed, missing some required parameters from ${db_properties}"
  fi

  context="--contexts=user"
  if [[ "$db_user" != "$db_admin_user" ]]; then
    context="--contexts=admin"
  fi

  echo "Context was: ${context}"

  url_concat_string="$([[ "$db_url" == *"?"* ]] && echo "&" || echo "?")"

  /usr/share/xroad/db/liquibase.sh \
    --changelog=centerui \
    --url="${db_url}${url_concat_string}currentSchema=${db_schema},public" \
    --password="${db_admin_password}" \
    --username="${db_admin_user}" \
    --defaultSchemaName="${db_schema}" \
    --prop-db-user="${db_user%%@*}" \
    $context \
    update ||
    die "Running database migrations failed, please check database availability and configuration in ${db_properties}"

}

migrate
