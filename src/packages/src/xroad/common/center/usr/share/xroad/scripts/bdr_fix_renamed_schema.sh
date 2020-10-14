#!/bin/bash
# Helper script to fix BRD schema rename

get_prop() { crudini --get "$1" '' "$2" 2>/dev/null || echo -n "$3"; }
get_db_prop() { get_prop "/etc/xroad/db.properties" "$@"; }
abort() { local rc=$?; echo -e "FATAL: $*" >&2; exit $rc; }

USER=$(get_db_prop 'username' 'centerui')
SCHEMA=$(get_db_prop 'schema')
PASSWORD=$(get_db_prop 'password' 'centerui')
DATABASE=$(get_db_prop 'database' 'centerui_production')
HOST=$(get_db_prop 'host' '127.0.0.1')
PORT=$(get_db_prop 'port' 5432)

remote_psql() {
    psql -h "$HOST" -p "$PORT" -qtA "$@"
}

psql_dbuser() {
    PGDATABASE="$DATABASE" PGUSER="$USER" PGPASSWORD="$PASSWORD" remote_psql "$@"
}

detect_bdr()  {
    [[ "$(psql_dbuser -c 'select bdr.bdr_version()' 2>/dev/null)" == "1.0."* ]];
}

detect_schema() {
    [[ "$(psql_dbuser -c "select schema_name from information_schema.schemata where schema_name = '$1';")" == "$1" ]]
}

check_db_version() {
  local version="$1"
  [[ "$(psql_dbuser -c "select version from schema_migrations where version='$version';")" == "$version" ]]
}

verify_db_version() {
  local version="$1"
  [[ "$(psql_dbuser -c "select max(version) from schema_migrations;")" == "$version" ]]
}

if [[ $(psql --version) != *" 9.4."* ]]; then
    abort "Expected psql version 9.4.x."
fi

if ! detect_bdr; then
  abort "BDR 1.0 not detected, exiting."
fi

if [[ -z "$SCHEMA" || "$SCHEMA" == "public" ]]; then
    abort "Schema is not renamed?"
fi

if ! detect_schema "$SCHEMA"; then
    abort "Schema '$SCHEMA' does not exist, exiting"
fi

if check_db_version 20200903000000; then
  abort "Fix alreadly applied"
fi

if ! verify_db_version '20200902142050'; then
  abort "Unexpected current DB version."
fi

if ! check_db_version '20200124082315'; then
  abort "No need to apply the fix."
fi

echo "Fixing schema '$SCHEMA' in database '$DATABASE' at $HOST:$PORT"
echo "Please create a backup before proceeding."
read -p "Continue (y/N)?" -n 1 -r
echo
if [[ $REPLY != y ]]; then
  exit 1
fi

psql_dbuser -v ON_ERROR_STOP=1 <<EOF || abort "Fixing schema failed, exiting"
SET SCHEMA '$SCHEMA';
BEGIN;
  CREATE OR REPLACE VIEW ha_cluster_status AS
  SELECT sp.ha_node_name, sp.value AS address, df.configuration_generated
  FROM (system_parameters sp
    LEFT JOIN (SELECT distributed_files.ha_node_name,
      max(distributed_files.file_updated_at) AS configuration_generated
      FROM distributed_files
      WHERE ((distributed_files.content_identifier)::text = 'PRIVATE-PARAMETERS'::text)
      GROUP BY distributed_files.ha_node_name) df ON (((sp.ha_node_name)::text = (df.ha_node_name)::text)))
  WHERE ((sp.key)::text = 'centralServerAddress'::text);

  ALTER TABLE ONLY anchor_url_certs ALTER COLUMN id SET DEFAULT nextval('anchor_url_certs_id_seq'::regclass);
  ALTER TABLE ONLY anchor_urls ALTER COLUMN id SET DEFAULT nextval('anchor_urls_id_seq'::regclass);
  ALTER TABLE ONLY approved_cas ALTER COLUMN id SET DEFAULT nextval('approved_cas_id_seq'::regclass);
  ALTER TABLE ONLY approved_tsas ALTER COLUMN id SET DEFAULT nextval('approved_tsas_id_seq'::regclass);
  ALTER TABLE ONLY auth_certs ALTER COLUMN id SET DEFAULT nextval('auth_certs_id_seq'::regclass);
  ALTER TABLE ONLY ca_infos ALTER COLUMN id SET DEFAULT nextval('ca_infos_id_seq'::regclass);
  ALTER TABLE ONLY central_services ALTER COLUMN id SET DEFAULT nextval('central_services_id_seq'::regclass);
  ALTER TABLE ONLY configuration_signing_keys ALTER COLUMN id SET DEFAULT nextval('configuration_signing_keys_id_seq'::regclass);
  ALTER TABLE ONLY configuration_sources ALTER COLUMN id SET DEFAULT nextval('configuration_sources_id_seq'::regclass);
  ALTER TABLE ONLY distributed_files ALTER COLUMN id SET DEFAULT nextval('distributed_files_id_seq'::regclass);
  ALTER TABLE ONLY global_group_members ALTER COLUMN id SET DEFAULT nextval('global_group_members_id_seq'::regclass);
  ALTER TABLE ONLY global_groups ALTER COLUMN id SET DEFAULT nextval('global_groups_id_seq'::regclass);
  ALTER TABLE ONLY history ALTER COLUMN id SET DEFAULT nextval('history_id_seq'::regclass);
  ALTER TABLE ONLY identifiers ALTER COLUMN id SET DEFAULT nextval('identifiers_id_seq'::regclass);
  ALTER TABLE ONLY member_classes ALTER COLUMN id SET DEFAULT nextval('member_classes_id_seq'::regclass);
  ALTER TABLE ONLY ocsp_infos ALTER COLUMN id SET DEFAULT nextval('ocsp_infos_id_seq'::regclass);
  ALTER TABLE ONLY request_processings ALTER COLUMN id SET DEFAULT nextval('request_processings_id_seq'::regclass);
  ALTER TABLE ONLY requests ALTER COLUMN id SET DEFAULT nextval('requests_id_seq'::regclass);
  ALTER TABLE ONLY security_categories ALTER COLUMN id SET DEFAULT nextval('security_categories_id_seq'::regclass);
  ALTER TABLE ONLY security_server_client_names ALTER COLUMN id SET DEFAULT nextval('security_server_client_names_id_seq'::regclass);
  ALTER TABLE ONLY security_server_clients ALTER COLUMN id SET DEFAULT nextval('security_server_clients_id_seq'::regclass);
  ALTER TABLE ONLY security_servers ALTER COLUMN id SET DEFAULT nextval('security_servers_id_seq'::regclass);
  ALTER TABLE ONLY security_servers_security_categories ALTER COLUMN id SET DEFAULT nextval('security_servers_security_categories_id_seq'::regclass);
  ALTER TABLE ONLY server_clients ALTER COLUMN id SET DEFAULT nextval('server_clients_id_seq'::regclass);
  ALTER TABLE ONLY system_parameters ALTER COLUMN id SET DEFAULT nextval('system_parameters_id_seq'::regclass);
  ALTER TABLE ONLY trusted_anchors ALTER COLUMN id SET DEFAULT nextval('trusted_anchors_id_seq'::regclass);
  ALTER TABLE ONLY ui_users ALTER COLUMN id SET DEFAULT nextval('ui_users_id_seq'::regclass);

  INSERT INTO SCHEMA_MIGRATIONS(version) VALUES ('20200903000000');
COMMIT;
SELECT fix_sequence('$SCHEMA');
EOF

echo "Done";
