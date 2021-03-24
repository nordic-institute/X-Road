#!/bin/bash

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

migrate() {
  local db_properties=/etc/xroad/db.properties

  # read parameters from db.properties
  local -r db_user=$(get_required_db_prop 'username')
  local -r db_schema=$(get_prop "$db_properties" 'schema' "$db_user")
  local -r db_host=$(get_required_db_prop 'host')
  local -r db_database=$(get_required_db_prop 'database')
  local -r db_password=$(get_required_db_prop 'password')

  if [[ -z "$db_user" || -z "$db_schema" || -z "$db_host" || -z "$db_database" || -z "$db_password" ]]; then
    die "Running database migrations failed, missing some required parameters from ${db_properties}"
  fi

  cd /usr/share/xroad/db/ || die "Running database migrations failed, please check that directory /usr/share/xroad/db exists"

  LIQUIBASE_HOME="$(pwd)" JAVA_OPTS="-Ddb_user=$db_user -Ddb_schema=$db_schema" /usr/share/xroad/db/liquibase.sh \
    --classpath=/usr/share/xroad/jlib/postgresql.jar \
    --url="jdbc:postgresql://$db_host/$db_database?currentSchema=${db_schema},public" \
    --changeLogFile=/usr/share/xroad/db/centerui-changelog.xml \
    --password="${db_password}" \
    --username="${db_user}" \
    --defaultSchemaName="${db_schema}" \
    update ||
    die "Running database migrations failed, please check database availability and configuration in ${db_properties}"

}

migrate
