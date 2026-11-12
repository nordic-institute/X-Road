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

# Sets ha_node_name_args to the Liquibase argument that carries the node name from node.ini, or to nothing when
# node.ini, its [node] section or the name is missing or blank, so the changelog default applies.
# Prints the error to stderr and returns 1 when node.ini is unreadable or malformed or the name breaks the node name rule.
prepare_ha_node_name_args() {
  local -r node_ini='/etc/xroad/conf.d/node.ini'
  local -r node_name_pattern='^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$'
  local node_name=""

  ha_node_name_args=()
  if [[ -e "$node_ini" ]]; then
    if ! crudini --get "$node_ini" >/dev/null 2>&1; then
      echo >&2 "Cannot read ${node_ini}: the file is unreadable or malformed."
      return 1
    fi
    node_name=$(crudini --get "$node_ini" node name 2>/dev/null) || node_name=""
  fi
  [[ -n "${node_name//[[:space:]]/}" ]] || return 0

  local LC_ALL=C
  if [[ ! "$node_name" =~ $node_name_pattern ]]; then
    echo >&2 "The node name '${node_name}' in ${node_ini} is not valid. It must start with a letter or digit, followed by letters, digits, '_' or '-', at most 63 characters in total."
    return 1
  fi
  ha_node_name_args=("--prop-ha-node-name=${node_name}")
}
