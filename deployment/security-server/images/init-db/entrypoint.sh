#!/bin/bash
set -euo pipefail

# Validate required environment variables
REQUIRED_VARS=(JDBC_URL DB_USER DB_PASSWORD DB_SCHEMA)
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: Required environment variable $var is not set" >&2
    exit 1
  fi
done

# Build CLI arguments
ARGS=(
  "--schema=$DB_SCHEMA"
  "--url=$JDBC_URL"
  "--username=$DB_USER"
  "--password=$DB_PASSWORD"
  "--defaultSchemaName=${DEFAULT_SCHEMA_NAME:-public}"
  "--log-level=debug"
)

# Optional changelog properties (only pass if set)
for prop in db_user db_schema proxy_ui_superuser proxy_ui_superuser_password; do
  if [[ -n "${!prop:-}" ]]; then
    ARGS+=("-D${prop}=${!prop}")
  fi
done

# Optional Liquibase contexts
if [[ -n "${LIQUIBASE_CONTEXTS:-}" ]]; then
  ARGS+=("--contexts=$LIQUIBASE_CONTEXTS")
fi

exec java -jar /app/liquibase-executor.jar "${ARGS[@]}" update
