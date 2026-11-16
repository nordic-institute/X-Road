#!/usr/bin/env bash
# Container test for the op-monitor native environment file.
#
# Sources the real opmonitor.conf and global.conf (mounted read-only from this checkout) in a Linux
# container with real crudini, once per case, and checks which DB_CONFIG_SOURCE_* variables are exported.
# A case is the presence of the proxy launcher plus the content of a stub /etc/xroad/db.properties.
# Only variable names and a blank / query-only / value classification of the URL are printed, never values.
#
# Usage: ./test-opmonitor-env.sh [image]
#   image defaults to ${OPMONITOR_ENV_TEST_IMAGE:-ubuntu:24.04}

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

IMAGE="${1:-${OPMONITOR_ENV_TEST_IMAGE:-ubuntu:24.04}}"
SERVICES_DIR="${ROOT_DIR}/deployment/native-packages/src/xroad/common"
GLOBAL_CONF="${SERVICES_DIR}/base/etc/xroad/services/global.conf"
OPMONITOR_CONF="${SERVICES_DIR}/op-monitor/etc/xroad/services/opmonitor.conf"
CONTAINER="opmonitor-env-test-$$"
WORK_DIR="$(mktemp -d)"
ERR_FILE="${WORK_DIR}/source.err"

LAUNCHER="/usr/share/xroad/bin/xroad-proxy"
DB_PROPERTIES="/etc/xroad/db.properties"
EXPECTED_ALL="DB_CONFIG_SOURCE_PASSWORD DB_CONFIG_SOURCE_URL DB_CONFIG_SOURCE_USERNAME"

URL_KEY="xroad.db.serverconf.hibernate.connection.url"
SCHEMA_KEY="xroad.db.serverconf.hibernate.hikari.dataSource.currentSchema"
USER_KEY="xroad.db.serverconf.hibernate.connection.username"
PASSWORD_KEY="xroad.db.serverconf.hibernate.connection.password"

FAILURES=0

cleanup() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

pass() { log_success "  ok: $1"; }
fail() {
  log_error "  FAILED: $1"
  FAILURES=$((FAILURES + 1))
  if [[ -s "${ERR_FILE}" ]]; then sed 's/^/    /' "${ERR_FILE}" >&2; fi
}

in_container() { docker exec "${CONTAINER}" "$@"; }

start_container() {
  docker run -d --name "${CONTAINER}" \
    -v "${GLOBAL_CONF}:/etc/xroad/services/global.conf:ro" \
    -v "${OPMONITOR_CONF}:/etc/xroad/services/opmonitor.conf:ro" \
    --entrypoint sleep "${IMAGE}" infinity >/dev/null || exit 1
  in_container bash -ec '
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq crudini >/dev/null
    useradd --system xroad
    mkdir -p /usr/share/xroad/bin /etc/xroad/conf.d' >/dev/null || { log_error "Container set-up failed"; exit 1; }
}

# set_launcher present|absent: the proxy launcher is root-owned and mode 550, as the package installs it.
set_launcher() {
  if [[ "$1" == present ]]; then
    in_container bash -c "install -m 550 -o root -g xroad /dev/null ${LAUNCHER}"
  else
    in_container rm -f "${LAUNCHER}"
  fi
}

# set_db_properties readable|unreadable LINE...: the file always holds the op-monitor keys, then the given lines.
set_db_properties() {
  local access="$1"
  shift
  {
    echo "xroad.db.op-monitor.hibernate.connection.url=jdbc:postgresql://127.0.0.1:5432/op-monitor"
    echo "xroad.db.op-monitor.hibernate.connection.username=opmonitor"
    echo "xroad.db.op-monitor.hibernate.connection.password=stub-op-monitor"
    local line
    for line in "$@"; do echo "${line}"; done
  } | docker exec -i "${CONTAINER}" sh -c "cat > ${DB_PROPERTIES}"
  if [[ "${access}" == readable ]]; then
    in_container chown root:xroad "${DB_PROPERTIES}"
    in_container chmod 640 "${DB_PROPERTIES}"
  else
    in_container chown root:root "${DB_PROPERTIES}"
    in_container chmod 600 "${DB_PROPERTIES}"
  fi
}

# source_opmonitor_conf: as the service user, prints the sorted DB_CONFIG_SOURCE_* variable names on the
# first line and the URL state (absent, blank, query-only or value) on the second. Never prints a value.
source_opmonitor_conf() {
  docker exec -u xroad "${CONTAINER}" bash -c '
    . /etc/xroad/services/opmonitor.conf || exit 1
    names=$(compgen -e | grep "^DB_CONFIG_SOURCE_" | sort | tr "\n" " ")
    echo "${names% }"
    if [[ " ${names} " != *" DB_CONFIG_SOURCE_URL "* ]]; then
      echo absent
    elif [[ -z "${DB_CONFIG_SOURCE_URL}" ]]; then
      echo blank
    elif [[ "${DB_CONFIG_SOURCE_URL}" == \?* ]]; then
      echo query-only
    else
      echo value
    fi' 2>"${ERR_FILE}"
}

# check_case LABEL LAUNCHER ACCESS EXPECTED_NAMES EXPECTED_URL_STATE LINE...
check_case() {
  local label="$1" launcher="$2" access="$3" expected_names="$4" expected_url="$5"
  shift 5
  set_launcher "${launcher}"
  set_db_properties "${access}" "$@"
  local output names url_state
  output="$(source_opmonitor_conf)"
  names="$(sed -n 1p <<<"${output}")"
  url_state="$(sed -n 2p <<<"${output}")"
  if [[ "${names}" == "${expected_names}" && "${url_state}" == "${expected_url}" ]]; then
    pass "${label}: exports [${names:-none}], URL ${url_state}"
  else
    fail "${label}: expected [${expected_names:-none}] with URL ${expected_url}, got [${names:-none}] with URL ${url_state}"
  fi
}

if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
  docker pull "${IMAGE}" >/dev/null || { log_error "Image ${IMAGE} not available"; exit 1; }
fi
log_info "Image: ${IMAGE}"
start_container

SERVERCONF_URL="${URL_KEY}=jdbc:postgresql://127.0.0.1:5432/serverconf"
SERVERCONF_USER="${USER_KEY}=serverconf"
SERVERCONF_PASSWORD="${PASSWORD_KEY}=stub-serverconf"
SCHEMA_SET="${SCHEMA_KEY}=serverconf"

log_info "Cases without the proxy launcher (bare host)"
check_case "key absent" absent readable "" absent
check_case "key present, blank" absent readable "" absent "${URL_KEY}="
check_case "blank base URL, schema set" absent readable "" absent "${URL_KEY}=" "${SCHEMA_SET}"
check_case "serverconf URL present" absent readable "${EXPECTED_ALL}" value \
  "${SERVERCONF_URL}" "${SERVERCONF_USER}" "${SERVERCONF_PASSWORD}"

log_info "Cases with the proxy launcher (Security Server)"
check_case "serverconf URL present" present readable "${EXPECTED_ALL}" value \
  "${SERVERCONF_URL}" "${SERVERCONF_USER}" "${SERVERCONF_PASSWORD}"
check_case "key absent" present readable "${EXPECTED_ALL}" blank
check_case "key present, blank" present readable "${EXPECTED_ALL}" blank "${URL_KEY}="
check_case "blank base URL, schema set" present readable "${EXPECTED_ALL}" query-only "${URL_KEY}=" "${SCHEMA_SET}"
check_case "db.properties unreadable for the service user" present unreadable "" absent \
  "${SERVERCONF_URL}" "${SERVERCONF_USER}" "${SERVERCONF_PASSWORD}"

if [[ "${FAILURES}" -ne 0 ]]; then
  log_error "${FAILURES} check(s) failed"
  exit 1
fi
log_success "All checks passed"
