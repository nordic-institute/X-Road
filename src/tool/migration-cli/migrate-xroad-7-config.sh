#!/bin/bash

DB_PROPERTIES_FILE="${1:-/etc/xroad/db.properties}"
MIGRATION_CLI_JAR_PATH=$XROAD_HOME/src/tool/migration-cli/build/libs/migration-cli-1.0.jar

ssl_properties_file=$(crudini --get /etc/xroad/conf.d/local.ini proxy-ui-api ssl-properties 2>/dev/null || echo "/etc/xroad/ssl.properties")

CONFIG_FILES=(
 /etc/xroad/conf.d/override-*.ini
 /etc/xroad/conf.d/local.ini
 "$ssl_properties_file"
)

echo "Using database properties: $DB_PROPERTIES_FILE"
echo "Starting configuration migration..."

for cfg in "${CONFIG_FILES[@]}"; do
  # Expand globs
  for file in $cfg; do
    if [[ -f "$file" ]]; then
      echo "Migrating: $file"
      java -cp "$MIGRATION_CLI_JAR_PATH" org.niis.xroad.configuration.migration.IniToDbMigrator "$file" "$DB_PROPERTIES_FILE"
    else
      echo "Skipping (not found): $file"
    fi
  done
done
