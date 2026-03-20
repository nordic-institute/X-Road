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

# Capture DB_SCHEMA for changelog selection, then unset to prevent
# Liquibase 5.x from resolving ${db_schema} changelog property from env var
CHANGELOG="$DB_SCHEMA"
unset DB_SCHEMA

# Build CLI arguments
ARGS=(
  "--changelog=$CHANGELOG"
  "--url=$JDBC_URL"
  "--username=$DB_USER"
  "--password=$DB_PASSWORD"
  "--defaultSchemaName=${DEFAULT_SCHEMA_NAME:-public}"
)

# Optional changelog properties (only pass if set)
for prop in db_user proxy_ui_superuser proxy_ui_superuser_password; do
  if [[ -n "${!prop:-}" ]]; then
    ARGS+=("--prop-${prop//_/-}=${!prop}")
  fi
done

# Optional Liquibase contexts
if [[ -n "${LIQUIBASE_CONTEXTS:-}" ]]; then
  ARGS+=("--contexts=$LIQUIBASE_CONTEXTS")
fi

exec java -jar /app/liquibase-executor.jar "${ARGS[@]}" update
