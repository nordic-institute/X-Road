#!/bin/bash

db_addr="${XROAD_SERVERCONF_DB_HOST:-db-serverconf}"
db_port="${XROAD_SERVERCONF_DB_PORT:-5432}"
db_database="${XROAD_SERVERCONF_DB_DATABASE:-serverconf}"
db_schema="${XROAD_SERVERCONF_DB_SCHEMA:-public}"
db_user="${XROAD_SERVERCONF_DB_USER:-serverconf}"
db_password="${XROAD_SERVERCONF_DB_PASSWORD}"
db_admin_user="${XROAD_SERVERCONF_DB_ADMIN_USER:-postgres}"
db_admin_password="${XROAD_SERVERCONF_DB_ADMIN_PASSWORD}"

pg_options="-c client-min-messages=warning -c search_path=$db_schema,public"

abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

while getopts "F" opt ; do
  case ${opt} in
    F)
      FORCE_RESTORE=true
      ;;
    \?)
      echo "Invalid option $OPTARG -- did you use the correct wrapper script?"
      exit 2
      ;;
  esac
done

shift $(($OPTIND - 1))

dump_file="$1"

if [[ ! -z $PGOPTIONS_EXTRA ]]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

remote_psql() {
  psql -v ON_ERROR_STOP=1 -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -qtA
}

psql_adminuser() {
  PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGDATABASE="$db_database" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" remote_psql
}

psql_dbuser() {
  PGOPTIONS="$pg_options${PGOPTIONS_EXTRA-}" PGDATABASE="$db_database" PGUSER="$db_user" PGPASSWORD="$db_password" remote_psql
}

pgrestore() {
  # no --clean for force restore
  if [[ $FORCE_RESTORE == true ]] ; then
    PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" \
      pg_restore --no-owner --single-transaction -d "$db_database" --schema="$db_schema" "$dump_file"
  else
    PGHOST="${PGHOST:-$db_addr}" PGPORT="${PGPORT:-$db_port}" PGUSER="$db_admin_user" PGPASSWORD="$db_admin_password" \
      pg_restore --no-owner --single-transaction --clean -d "$db_database" --schema="$db_schema" "$dump_file"
  fi
}

if [[ $FORCE_RESTORE == true ]] ; then
  { cat <<EOF
     DROP SCHEMA IF EXISTS "$db_schema" CASCADE;
EOF
  } | psql_adminuser || abort "Restoring database failed. Could not drop schema."
fi

{ cat <<EOF
CREATE SCHEMA IF NOT EXISTS "$db_schema";
CREATE EXTENSION IF NOT EXISTS hstore;
EOF
} | psql_adminuser || abort "Restoring database failed. Could not create schema."

pgrestore || abort "Restoring database failed."

# PostgreSQL does not in all cases detect that prepared statements in open sessions
# need to be re-parsed. Therefore, try to forcibly close any serverconf connections.
{ cat <<EOF
DO \$\$
BEGIN
  PERFORM pg_terminate_backend(pid)
  FROM pg_stat_activity
  WHERE usename = '$db_user'
    AND datname = '$db_database'
    AND pid <> pg_backend_pid();
END
\$\$;
EOF
} | psql_dbuser || true


echo "Running Liquibase migration for serverconf database..."

if [ -n "$KUBERNETES_SERVICE_HOST" ] || [ -f /var/run/secrets/kubernetes.io/serviceaccount/token ]; then
  echo "Kubernetes deployment detected. Creating job for Liquibase migration..."
  jobname="serverconf-liquibase-migrate-$(date +%Y%m%d%H%M%S)"

  (cat <<EOF
  apiVersion: batch/v1
  kind: Job
  metadata:
    name: "${jobname}"
  spec:
    ttlSecondsAfterFinished: 86400  # 24 hours
    backoffLimit: 1
    template:
      spec:
        restartPolicy: Never
        containers:
          - name: serverconf-liquibase-runner
            image: localhost:5555/ss-db-serverconf-init:latest
            env:
              - name: LIQUIBASE_COMMAND_URL
                value: "jdbc:postgresql://${db_addr}:${db_port}/${db_database}"
              - name: LIQUIBASE_COMMAND_USERNAME
                value: "${db_admin_user}"
              - name: LIQUIBASE_COMMAND_PASSWORD
                valueFrom:
                  secretKeyRef:
                    name: db-serverconf
                    key: postgres-password
              - name: db_schema
                value: "${db_schema}"
              - name: db_user
                value: "${db_user}"
EOF

  if [[ "$SERVERCONF_INITIALIZED_WITH_PROXY_UI_SUPERUSER" == "true" ]]; then
    cat <<EOF
              - name: PROXY_UI_SUPERUSER
                value: "${PROXY_UI_SUPERUSER}"
              - name: PROXY_UI_SUPERUSER_PASSWORD
                valueFrom:
                  secretKeyRef:
                    name: serverconf-db-init-secret
                    key: proxy_ui_superuser_password
EOF
  fi) | kubectl apply -f -

  if [ $? -ne 0 ]; then
    abort "Failed to trigger Liquibase migration job."
  fi
  echo "Job created: ${jobname}. Waiting for it to complete..."
  if ! kubectl wait --for=condition=complete job/"${jobname}" --timeout=300s
  then
    abort "Job ${jobname} did not complete successfully within timeout"
  fi

  echo "Liquibase migration completed successfully."
else
  echo "Unsupported environment for Liquibase migration. Liquibase migration will not be run."
fi

