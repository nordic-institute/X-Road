#!/bin/bash
set -euo pipefail

# Validate required environment variables
REQUIRED_VARS=(JDBC_URL DB_USER DB_PASSWORD CHANGELOG)
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: Required environment variable $var is not set" >&2
    exit 1
  fi
done

# Build CLI arguments
ARGS=(
  "--changelog=$CHANGELOG"
  "--url=$JDBC_URL"
  "--username=$DB_USER"
  "--password=$DB_PASSWORD"
  "--defaultSchemaName=${DEFAULT_SCHEMA_NAME:-public}"
)

# Optional changelog properties (PROP_* env vars → --prop-* executor args)
declare -A PROP_MAP=(
  [PROP_DB_USER]=db-user
  [PROP_PROXY_UI_SUPERUSER]=proxy-ui-superuser
  [PROP_PROXY_UI_SUPERUSER_PASSWORD]=proxy-ui-superuser-password
)
for env_var in "${!PROP_MAP[@]}"; do
  if [[ -n "${!env_var:-}" ]]; then
    ARGS+=("--prop-${PROP_MAP[$env_var]}=${!env_var}")
  fi
done

# Optional Liquibase contexts
if [[ -n "${LIQUIBASE_CONTEXTS:-}" ]]; then
  ARGS+=("--contexts=$LIQUIBASE_CONTEXTS")
fi

exec java -jar /app/liquibase-executor.jar "${ARGS[@]}" update
