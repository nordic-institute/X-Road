#!/usr/bin/env bash

export PGPASSWORD=${PGPASSWORD:-${POSTGRES_PASSWORD}}

# Setup database, schema and user
(psql -U ${POSTGRES_USER} -p 5432 -h ${POSTGRES_HOST} -lqt | cut -d \| -f 1 | grep -qw ${POSTGRES_XRD_DATABASE}) || {
  psql -v ON_ERROR_STOP=1 -U ${POSTGRES_USER} -p 5432 -h ${POSTGRES_HOST} -f <(${PREPARE_DB_INIT_SQL_SCRIPT})
}

context=$([[ "${POSTGRES_XRD_USER}" != "${POSTGRES_XRD_ADMIN_USER}" ]] && echo 'admin' || echo 'user')

set -x
# Perform migrations by using the Liquibase
java \
  -cp "${LIQUIBASE_CLASSPATH}" \
  -Ddb_user="${POSTGRES_XRD_USER}" -Ddb_schema="${POSTGRES_XRD_SCHEMA}" `# Changelog interpolation parameters` \
  -Dliquibase.hub.mode=off `# Do not send any data to Liquibase Hub` \
  liquibase.integration.commandline.Main \
  --url="jdbc:postgresql://${POSTGRES_HOST}:5432/${POSTGRES_XRD_DATABASE}?currentSchema=${POSTGRES_XRD_SCHEMA},public" \
  --changeLogFile="${LIQUIBASE_CHANGE_LOG_FILE}" \
  --password="${POSTGRES_XRD_ADMIN_PASSWORD}" --username="${POSTGRES_XRD_ADMIN_USER}" \
  --defaultSchemaName="${POSTGRES_XRD_SCHEMA}" \
  --contexts="$context" \
  update
