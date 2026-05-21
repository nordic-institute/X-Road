#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"
log_message "========================================"
log_message "Step: Serverconf Identifier Deduplication"
log_message "========================================"
log_message ""

require_root

get_db_prop() {
  local pattern="$1"
  grep -m1 -E "$pattern" "$DB_PROPS" 2>/dev/null \
    | sed -E 's/^[^=]*=[[:space:]]*//' \
    | sed -E 's/[[:space:]]+$//'
}

prepare_serverconf_db() {
  if [[ ! -f "$DB_PROPS" ]]; then
    log_die "$DB_PROPS not found. Cannot run serverconf.identifier deduplication."
    return 1
  fi

  local jdbc_url
  jdbc_url=$(get_db_prop 'serverconf.*hibernate\.connection\.url' | tr -d ' ')

  if [[ -z "$jdbc_url" ]]; then
    log_die "serverconf JDBC URL not found in $DB_PROPS. Cannot run serverconf.identifier deduplication."
    return 1
  fi

  local hostport
  hostport=$(echo "$jdbc_url" | sed 's|jdbc:postgresql://\([^/]*\)/.*|\1|')

  db_host="${hostport%%:*}"
  if [[ "$hostport" == *:* ]]; then
    db_port="${hostport##*:}"
  else
    db_port="5432"
  fi

  db_user=$(get_db_prop 'serverconf\.hibernate\.connection\.username')
  db_password=$(get_db_prop 'serverconf\.hibernate\.connection\.password')
  db_user="${db_user%%@*}"

  local db_path
  db_path=$(echo "$jdbc_url" | sed 's|jdbc:postgresql://[^/]*/\([^?]*\).*|\1|')
  db_database="${db_path:-serverconf}"

  log_info "PostgreSQL connection: host=${db_host} port=${db_port} user=${db_user} db=${db_database}"

  return 0
}

psql_run() {
  PGPASSWORD="${db_password}" psql \
    -v ON_ERROR_STOP=1 \
    -h "${db_host}" \
    -p "${db_port}" \
    -U "${db_user}" \
    -d "${db_database}" \
    "$@"
}

db_exists() {
  PGPASSWORD="${db_password}" psql \
    -qtAX \
    -h "${db_host}" \
    -p "${db_port}" \
    -U "${db_user}" \
    -d postgres \
    -c "SELECT 1 FROM pg_database WHERE datname = '${db_database}'" \
    | grep -qx '1'
}

run_migration() {
  log_info "Using DB=${db_database}, HOST=${db_host}, PORT=${db_port}"

  if ! db_exists; then
    log_die "Database ${db_database} not found on this host. Cannot run serverconf.identifier deduplication."
  fi

  psql_run <<SQL
BEGIN;

LOCK TABLE serverconf.identifier IN EXCLUSIVE MODE;
LOCK TABLE serverconf.accessright IN EXCLUSIVE MODE;
LOCK TABLE serverconf.client IN EXCLUSIVE MODE;
LOCK TABLE serverconf.groupmember IN EXCLUSIVE MODE;

DO \$\$
DECLARE
    bad_count bigint;
BEGIN
    SELECT COUNT(*)
      INTO bad_count
    FROM serverconf.identifier
    WHERE "type" IS NULL
       OR "type" NOT IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP', 'LOCALGROUP');

    IF bad_count <> 0 THEN
        RAISE EXCEPTION
            'serverconf.identifier contains % rows with invalid type values (allowed values: MEMBER, SUBSYSTEM, GLOBALGROUP, LOCALGROUP)',
            bad_count;
    END IF;

    SELECT COUNT(*)
      INTO bad_count
    FROM serverconf.identifier
    WHERE xroadinstance IS NULL
       OR memberclass IS NULL
       OR membercode IS NULL;

    IF bad_count <> 0 THEN
        RAISE EXCEPTION
            'serverconf.identifier contains % rows with NULL values in required fields (xroadinstance/memberclass/membercode)',
            bad_count;
    END IF;

    SELECT COUNT(*)
      INTO bad_count
    FROM serverconf.identifier
    WHERE servicecode IS NOT NULL
       OR serviceversion IS NOT NULL;

    IF bad_count <> 0 THEN
        RAISE EXCEPTION
            'serverconf.identifier contains % rows where servicecode or serviceversion is NOT NULL (these columns should be NULL)',
            bad_count;
    END IF;

    SELECT COUNT(*)
      INTO bad_count
    FROM serverconf.identifier
    WHERE servercode IS NOT NULL
      AND "type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP');

    IF bad_count <> 0 THEN
        RAISE EXCEPTION
            'serverconf.identifier contains % rows where servercode is NOT NULL (servercode should be NOT NULL for MEMBER, SUBSYSTEM, and GLOBALGROUP)',
            bad_count;
    END IF;
END \$\$;

DROP TABLE IF EXISTS tmp_identifier_groups;
CREATE TEMP TABLE tmp_identifier_groups AS
SELECT
    MIN(id) AS main_id,
    "type",
    xroadinstance,
    memberclass,
    membercode,
    subsystemcode,
    groupcode,
    servercode
FROM serverconf.identifier
WHERE "type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP')
GROUP BY
    "type",
    xroadinstance,
    memberclass,
    membercode,
    subsystemcode,
    groupcode,
    servercode
HAVING COUNT(*) > 1;

DROP TABLE IF EXISTS tmp_identifier_dedup_map;
CREATE TEMP TABLE tmp_identifier_dedup_map AS
SELECT
    i.id AS dup_id,
    g.main_id
FROM serverconf.identifier i
JOIN tmp_identifier_groups g
    ON i."type" = g."type"
   AND i.xroadinstance = g.xroadinstance
   AND i.memberclass = g.memberclass
   AND i.membercode = g.membercode
   AND i.subsystemcode IS NOT DISTINCT FROM g.subsystemcode
   AND i.groupcode IS NOT DISTINCT FROM g.groupcode
   AND i.servercode IS NOT DISTINCT FROM g.servercode
WHERE i.id <> g.main_id
  AND i."type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP');

UPDATE serverconf.accessright ar
SET subjectid = m.main_id
FROM tmp_identifier_dedup_map m
WHERE ar.subjectid = m.dup_id;

UPDATE serverconf.client c
SET identifier = m.main_id
FROM tmp_identifier_dedup_map m
WHERE c.identifier = m.dup_id;

UPDATE serverconf.groupmember gm
SET groupmemberid = m.main_id
FROM tmp_identifier_dedup_map m
WHERE gm.groupmemberid = m.dup_id;

DELETE FROM serverconf.identifier i
USING tmp_identifier_dedup_map m
WHERE i.id = m.dup_id;

DO \$\$
DECLARE
    remaining_count integer;
BEGIN
    SELECT COUNT(*)
      INTO remaining_count
    FROM (
        SELECT 1
        FROM serverconf.identifier
        WHERE "type" IN ('MEMBER', 'SUBSYSTEM', 'GLOBALGROUP')
        GROUP BY
            "type",
            xroadinstance,
            memberclass,
            membercode,
            subsystemcode,
            groupcode,
            servercode
        HAVING COUNT(*) > 1
    ) d;

    IF remaining_count <> 0 THEN
        RAISE EXCEPTION
            'Duplicate rows still remain in serverconf.identifier for MEMBER, SUBSYSTEM, and GLOBALGROUP: %',
            remaining_count;
    END IF;
END \$\$;

COMMIT;
SQL

  return 0
}

main() {
  if ! prepare_serverconf_db; then
    exit 1
  fi

  run_migration

  log_message ""
  log_info "Serverconf.identifier deduplication completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
