#!/bin/bash
set -euo pipefail

# Bootstrap mode (DB_BOOTSTRAP=on) creates the roles and the schema as the database master, then runs Liquibase
# as the admin role. Settings:
#   DB_BOOTSTRAP         on or off (default off). Off runs Liquibase as JDBC_USER with JDBC_PASSWORD.
#   DB_MASTER_USER       master role that runs the bootstrap SQL (CREATEDB and CREATEROLE are enough).
#   DB_MASTER_PASSWORD   password of the master role.
#   DB_ADMIN_PASSWORD    password of the admin role (owns the schema and runs Liquibase).
#   DB_RUNTIME_PASSWORD  password of the runtime role (the application's login, no DDL rights).
#   DB_HSTORE            on or off (default off). On creates the hstore extension in the public schema.
#   PROP_DB_USER         name of the runtime role. The admin role is "<PROP_DB_USER>_admin".
# JDBC_URL names the database to create; DEFAULT_SCHEMA_NAME (default public) names the component schema in it.

DB_SQL_DIR=/usr/share/xroad/db

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

# Quotes a value as a psql single-quoted \set argument.
psql_quote() {
  local value="${1//\\/\\\\}"
  printf "'%s'" "${value//\'/\'\'}"
}

# Sets db_host, db_port and db_name from the first host of JDBC_URL.
parse_jdbc_url() {
  local pattern='^jdbc:postgresql://([^/?]*)(/([^?]*))?'
  [[ "$JDBC_URL" =~ $pattern ]] || fail "Unable to parse JDBC_URL"
  local host_port="${BASH_REMATCH[1]%%,*}"
  db_host="${host_port%%:*}"
  db_port=5432
  [[ "$host_port" == *:* ]] && db_port="${host_port##*:}"
  db_name="${BASH_REMATCH[3]}"
  [[ -n "$db_host" && -n "$db_name" ]] || fail "JDBC_URL must name a host and a database"
}

# Runs the shared bootstrap SQL pair as the master, then checks that the runtime role can log in.
bootstrap_database() {
  local runtime_role="$PROP_DB_USER"
  local pair
  parse_jdbc_url

  if ! {
    for pair in \
      "db_name=$db_name" \
      "admin_role=$admin_role" \
      "admin_password=$DB_ADMIN_PASSWORD" \
      "runtime_role=$runtime_role" \
      "runtime_password=$DB_RUNTIME_PASSWORD" \
      "master_role=$DB_MASTER_USER" \
      "schema_name=${DEFAULT_SCHEMA_NAME:-public}" \
      "hstore=${DB_HSTORE:-off}" \
      "single_role=off"; do
      printf '\\set %s %s\n' "${pair%%=*}" "$(psql_quote "${pair#*=}")"
    done
    cat "$DB_SQL_DIR/bootstrap-cluster.sql" "$DB_SQL_DIR/bootstrap-database.sql"
  } | PGPASSWORD="$DB_MASTER_PASSWORD" PGCONNECT_TIMEOUT=10 psql -X -qtA -h "$db_host" -p "$db_port" -U "$DB_MASTER_USER" -d postgres; then
    fail "Database bootstrap as master user '$DB_MASTER_USER' on $db_host:$db_port failed"
  fi

  if ! PGPASSWORD="$DB_RUNTIME_PASSWORD" PGCONNECT_TIMEOUT=5 psql -X -h "$db_host" -p "$db_port" -U "$runtime_role" -d "$db_name" -c '\q' >/dev/null 2>&1; then
    fail "Logging in as database role '$runtime_role' failed after database setup. If the role already existed, its password differs from DB_RUNTIME_PASSWORD."
  fi
}

DB_BOOTSTRAP="${DB_BOOTSTRAP:-off}"
[[ "$DB_BOOTSTRAP" == "on" || "$DB_BOOTSTRAP" == "off" ]] || fail "DB_BOOTSTRAP must be 'on' or 'off', got '$DB_BOOTSTRAP'"
if [[ "$DB_BOOTSTRAP" == "on" ]]; then
  REQUIRED_VARS=(JDBC_URL CHANGELOG PROP_DB_USER DB_MASTER_USER DB_MASTER_PASSWORD DB_ADMIN_PASSWORD DB_RUNTIME_PASSWORD)
else
  REQUIRED_VARS=(JDBC_URL JDBC_USER JDBC_PASSWORD CHANGELOG)
fi
for var in "${REQUIRED_VARS[@]}"; do
  [[ -n "${!var:-}" ]] || fail "Required environment variable $var is not set"
done

# Bootstrap mode creates the roles, then runs Liquibase as the admin role
if [[ "$DB_BOOTSTRAP" == "on" ]]; then
  admin_role="${PROP_DB_USER}_admin"
  bootstrap_database
  JDBC_USER="$admin_role"
  JDBC_PASSWORD="$DB_ADMIN_PASSWORD"
  LIQUIBASE_CONTEXTS="admin${LIQUIBASE_CONTEXTS:+,$LIQUIBASE_CONTEXTS}"
fi

# Build CLI arguments
ARGS=(
  "--changelog=$CHANGELOG"
  "--url=$JDBC_URL"
  "--username=$JDBC_USER"
  "--defaultSchemaName=${DEFAULT_SCHEMA_NAME:-public}"
)

# Optional changelog properties (PROP_* env vars → --prop-* executor args)
# Any env var prefixed with PROP_ is forwarded: PROP_DB_USER → --prop-db-user
while IFS='=' read -r name value; do
  prop_name="${name#PROP_}"
  prop_name="${prop_name//_/-}"
  prop_name=$(echo "$prop_name" | tr '[:upper:]' '[:lower:]')
  ARGS+=("--prop-${prop_name}=${value}")
done < <(env | grep '^PROP_' || true)

# Optional Liquibase contexts
if [[ -n "${LIQUIBASE_CONTEXTS:-}" ]]; then
  ARGS+=("--contexts=$LIQUIBASE_CONTEXTS")
fi

LIQUIBASE_COMMAND_PASSWORD="$JDBC_PASSWORD" exec java -jar /app/liquibase-executor.jar "${ARGS[@]}" update
