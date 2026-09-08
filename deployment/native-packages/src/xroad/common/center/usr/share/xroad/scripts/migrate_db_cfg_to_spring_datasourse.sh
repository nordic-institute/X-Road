#!/bin/bash

build_secondary_hosts() {
  local -r secondary_hosts=$(crudini --get "$1" '' secondary_hosts)
  local joined=""
  IFS=';' read -ra hosts <<< "$secondary_hosts"
  for h in "${hosts[@]}"; do
    if [ -n "$joined" ]; then
      joined="${joined},"
    fi
    if [[ "$h" == *":"* ]]; then
      joined="${joined}${h}"
    else
      joined="${joined}${h}:${port}"
    fi
  done
  echo "$joined"
}

migrate_db_props() {
  local -r original_file=/etc/xroad/db.properties
  local -r temp_file=/etc/xroad/db.properties.tmp

  if [ ! -f ${original_file} ]; then
    echo "/etc/xroad/db.properties file isn't present. Skipping migration..."
    exit
  fi


  local -r spring_ds_username=$(crudini --get $original_file '' spring.datasource.username)
  local host=$(crudini --get $original_file '' host)

  if [[ -n "$spring_ds_username" || -z "$host" ]]; then
    echo "Spring Datasource compatible properties already present in file or it isn't yet fully created. Skipping migration..."
    exit
  fi
  echo "Migrating to Spring Datasource properties..."

  local port=$(crudini --get $original_file '' port)
  local -r user=$(crudini --get $original_file '' username)
  local schema=$(crudini --get $original_file '' schema)
  local -r password=$(crudini --get $original_file '' password)
  local -r database=$(crudini --get $original_file '' database)
  local -r skip_migrations=$(crudini --get $original_file '' skip_migrations)

  if [ -z "$schema" ]; then
    schema=$user
  fi

  if [ -n "$port" ]; then
      host="$host:$port"
  fi

  local -r sec_hosts=$(build_secondary_hosts "$original_file")
  if [ -n "$sec_hosts" ]; then
      host="${host},${sec_hosts}"
  fi

  crudini --set ${temp_file} '' "spring.datasource.username" "$user"
  crudini --set ${temp_file} '' "spring.datasource.password" "$password"
  crudini --set ${temp_file} '' "spring.datasource.url" "jdbc:postgresql://${host}/${database}"
  crudini --set ${temp_file} '' "spring.datasource.hikari.data-source-properties.currentSchema" "${schema},public"
  if [ -n "$skip_migrations" ]; then
   crudini --set ${temp_file} '' "skip_migrations" "$skip_migrations"
  fi
  mv -f "$original_file" "$original_file.old"
  mv -f "$temp_file" "$original_file"
  echo "Migration completed."
}

migrate_db_props
