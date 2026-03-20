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

exec java -jar /app/liquibase-executor.jar "${ARGS[@]}" update
