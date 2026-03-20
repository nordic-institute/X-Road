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
[[ -n "${PROP_DB_USER:-}" ]] && ARGS+=("--prop-db-user=$PROP_DB_USER")
[[ -n "${PROP_PROXY_UI_SUPERUSER:-}" ]] && ARGS+=("--prop-proxy-ui-superuser=$PROP_PROXY_UI_SUPERUSER")
[[ -n "${PROP_PROXY_UI_SUPERUSER_PASSWORD:-}" ]] && ARGS+=("--prop-proxy-ui-superuser-password=$PROP_PROXY_UI_SUPERUSER_PASSWORD")

# Optional Liquibase contexts
if [[ -n "${LIQUIBASE_CONTEXTS:-}" ]]; then
  ARGS+=("--contexts=$LIQUIBASE_CONTEXTS")
fi

exec java -jar /app/liquibase-executor.jar "${ARGS[@]}" update
