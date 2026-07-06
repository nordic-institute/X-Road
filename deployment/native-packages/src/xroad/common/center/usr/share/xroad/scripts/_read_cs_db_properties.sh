get_cs_db_prop() {
  crudini --get '/etc/xroad/db.properties' '' "$1" 2>/dev/null || echo -n "$2"
}

prepare_db_props() {
  db_url=$(get_cs_db_prop 'spring.datasource.url' 'jdbc:postgresql://127.0.0.1:5432/centerui_production')
  db_user=$(get_cs_db_prop 'spring.datasource.username' 'centerui')
  db_password=$(get_cs_db_prop 'spring.datasource.password')
  db_schema=$(get_cs_db_prop 'spring.datasource.hikari.data-source-properties.currentSchema', "${db_user},public")

  db_schema=${db_schema%%,*}

  db_host='127.0.0.1:5432'
  local -r pat='^jdbc:postgresql://([^/]*)($|/([^\?]*)(.*)$)'
  if [[ "$db_url" =~ $pat ]]; then
    db_host=${BASH_REMATCH[1]}
    db_database=${BASH_REMATCH[3]}
  fi

  db_host=${db_host%%,*}

  local parts
  IFS=':' read -ra parts <<<"$db_host"
  db_host=${parts[0]}
  db_port=${parts[1]}

  db_port=${db_port:-5432}
  db_database=${db_database:-centerui_production}
}
