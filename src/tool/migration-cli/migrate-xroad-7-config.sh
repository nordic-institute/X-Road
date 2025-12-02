#!/bin/bash

DEFAULT_DB_DB_PROPERTIES_FILE=$(crudini --get proxy database-properties	/etc/xroad/conf.d/local.ini || echo "/etc/xroad/db.properties")
DB_PROPERTIES_FILE="${1:-$DEFAULT_DB_DB_PROPERTIES_FILE}"

MIGRATION_CLI_JAR_PATH=$XROAD_HOME/src/tool/migration-cli/build/libs/migration-cli-1.0.jar

SSL_PROPERTIES_FILE=$(crudini --get /etc/xroad/conf.d/local.ini proxy-ui-api ssl-properties 2>/dev/null || echo "/etc/xroad/ssl.properties")
CONF_ANCHOR_FILE=$(crudini --get /etc/xroad/conf.d/local.ini proxy configuration-anchor-file 2>/dev/null || echo "/etc/xroad/configuration-anchor.xml")
DEVICES_INI_FILE=$(crudini --get /etc/xroad/conf.d/local.ini signer device-configuration-file 2>/dev/null || echo "/etc/xroad/devices.ini")
ACME_CONFIG_FILE="/etc/xroad/conf.d/acme.yml"
MAIL_CONFIG_FILE="/etc/xroad/conf.d/mail.yml"

CONFIG_FILES=(
 /etc/xroad/conf.d/override-*.ini
 /etc/xroad/conf.d/local.ini
)

migrate_file() {
  local file="$1"
  local class="$2"
  local description="$3"
  local scope="$4"
  local property_key="$5"

  if [[ -f "$file" ]]; then
    read -p "$description ($file) exists. Migrate? [y/N]" confirm
    case "$confirm" in
      [yY][eE][sS]|[yY])
        java -cp "$MIGRATION_CLI_JAR_PATH" org.niis.xroad.configuration.migration."$class" "$file" \
          "$DB_PROPERTIES_FILE" ${property_key:+$property_key} ${scope:+$scope}
        ;;
      *)
        echo "Skipping: $file"
        ;;
    esac
    echo "----------------------------------"
  else
    echo "$file file not found."
  fi
}

save_value_to_db() {
  echo "Saving $1 = $2 (scope:'${3:-}') to database."
  java -cp "$MIGRATION_CLI_JAR_PATH" org.niis.xroad.configuration.migration.SinglePropertySetter  \
                  "$DB_PROPERTIES_FILE" "$1" "$2" ${3:+$3}
}


keep_batch_signatures() {
  read -p "Keep batch signing enabled (disabled by default)? [y/N]" confirm
  case "$confirm" in
    [yY][eE][sS]|[yY])
      save_value_to_db "xroad.proxy.batch-signing-enabled" "true"
      ;;
    *)
      echo "Batch signing disabled."
      ;;
  esac
}

echo "Using database properties: $DB_PROPERTIES_FILE"
echo "Starting configuration files migration..."

for cfg in "${CONFIG_FILES[@]}"; do
  # Expand globs
  for file in $cfg; do
    migrate_file "$file" IniToDbMigrator "Configuration file"
  done
done

migrate_file "$SSL_PROPERTIES_FILE" PropertiesToDbMigrator "SSL properties file" "proxy-ui-api"
migrate_file "$CONF_ANCHOR_FILE" ConfigurationAnchorMigrator "Configuration anchor file"
migrate_file "$DEVICES_INI_FILE" DevicesIniToDbMigrator "Signer devices configuration file"
migrate_file "$ACME_CONFIG_FILE" FileToDbPropertyMigrator "ACME configuration file" "proxy-ui-api" "xroad.acme"
migrate_file "$MAIL_CONFIG_FILE" FileToDbPropertyMigrator "Mail notification configuration file" "proxy-ui-api" "xroad.mail-notification"

keep_batch_signatures

