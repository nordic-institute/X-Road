#!/bin/bash

DEFAULT_DB_DB_PROPERTIES_FILE=$(crudini --get proxy database-properties	/etc/xroad/conf.d/local.ini || echo "/etc/xroad/db.properties")

DB_PROPERTIES_FILE="${1:-$DEFAULT_DB_DB_PROPERTIES_FILE}"
MIGRATION_CLI_JAR_PATH=$XROAD_HOME/src/tool/migration-cli/build/libs/migration-cli-1.0.jar

SSL_PROPERTIES_FILE=$(crudini --get /etc/xroad/conf.d/local.ini proxy-ui-api ssl-properties 2>/dev/null || echo "/etc/xroad/ssl.properties")
CONF_ANCHOR_FILE=$(crudini --get /etc/xroad/conf.d/local.ini proxy configuration-anchor-file 2>/dev/null || echo "/etc/xroad/configuration-anchor.xml")

CONFIG_FILES=(
 /etc/xroad/conf.d/override-*.ini
 /etc/xroad/conf.d/local.ini
)

echo "Using database properties: $DB_PROPERTIES_FILE"
echo "Starting configuration files migration..."

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

#todo: ssl properties

if [ -f "$CONF_ANCHOR_FILE" ]; then
  echo "Configuration anchor file (${CONF_ANCHOR_FILE}) exists. Will insert to db."

  java -cp "$MIGRATION_CLI_JAR_PATH" org.niis.xroad.configuration.migration.ConfigurationAnchorMigrator "$CONF_ANCHOR_FILE" "$DB_PROPERTIES_FILE"
else
  echo "${CONF_ANCHOR_FILE} file not found, skipping migration to db."
fi
