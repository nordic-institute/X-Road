#!/usr/bin/env bash
# Container test for the secret store scripts: init, config writer, unseal and cluster bundle.
#
# Runs the real scripts (mounted read-only from this checkout) against a real OpenBao started from the
# dev OpenBao image, once per scenario, and checks the resulting OpenBao state. It also runs the supervisor
# unseal listener of the Central Server container and compares the policy in the HA guide with the one
# the init script writes.
#
# Usage: ./test-secret-store-init.sh [image]
#   image defaults to ${IMAGE_REGISTRY:-localhost:5555}/openbao-dev:${IMAGE_TAG:-latest}

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

IMAGE="${1:-${IMAGE_REGISTRY:-localhost:5555}/openbao-dev:${IMAGE_TAG:-latest}}"
PACKAGE_SOURCES="${ROOT_DIR}/deployment/native-packages/src/xroad/common"
POSTINST_FILE="${ROOT_DIR}/deployment/native-packages/src/xroad/ubuntu/generic/xroad-secret-store-local.postinst"
POSTRM_FILE="${ROOT_DIR}/deployment/native-packages/src/xroad/ubuntu/generic/xroad-secret-store-local.postrm"
SECRET_STORE_SCRIPTS_DIR="${PACKAGE_SOURCES}/secret-store-local/usr/share/xroad/scripts"
BASE_SCRIPTS_DIR="${PACKAGE_SOURCES}/base/usr/share/xroad/scripts"
NODE1_CONTAINER="secret-store-init-test-$$"
NODE2_CONTAINER="${NODE1_CONTAINER}-2"
CONTAINER="${NODE1_CONTAINER}"
PG_CONTAINER="${NODE1_CONTAINER}-pg"
NETWORK="${NODE1_CONTAINER}-net"
PG_IMAGE="${PG_IMAGE:-postgres:18-alpine}"
PSQL_IMAGE="${PSQL_IMAGE:-postgres:18}"
PSQL_CONTAINER="${NODE1_CONTAINER}-psql"
PG_PASSWORD="test-pg-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
WORK_DIR="$(mktemp -d)"
SCRIPTS_DIR="${WORK_DIR}/scripts"
BASE_ONLY_CONTAINER="${NODE1_CONTAINER}-base-only"
ENV_PARSE_CONTAINER="${NODE1_CONTAINER}-env-parse"
ANSIBLE_ENV_TEMPLATE="${ROOT_DIR}/development/ansible/roles/xroad-ss/templates/xroad-openbao.env.j2"
CONFIG_SCRIPT_SOURCE="${ROOT_DIR}/deployment/native-packages/src/xroad/ubuntu/generic/xroad-secret-store-local.config"
ENV_HELPER_SCRIPT="/usr/share/xroad/scripts/_openbao_env.sh"
INIT_DB_SCRIPT="/usr/share/xroad/scripts/secret-store-init-db.sh"
BACKUP_DB_SCRIPT="/usr/share/xroad/scripts/backup_openbao_db.sh"
RESTORE_DB_SCRIPT="/usr/share/xroad/scripts/restore_openbao_db.sh"
PG_CALLS_FILE="/var/log/pg-calls"
DEBCONF_CALLS_FILE="/tmp/debconf-calls"
ENV_KEYS=(BAO_PG_USER BAO_PG_PASSWORD BAO_PG_HOST BAO_PG_PORT BAO_PG_DATABASE BAO_PG_SCHEMA BAO_PG_CONNECTION_URL)

ADDR="http://127.0.0.1:8200"
ROOT_TOKEN_FILE="/etc/openbao/root-token"
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"
CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"
SETUP_SCRIPT="/usr/share/xroad/scripts/secret-store-init.sh"
BUNDLE_SCRIPT="/usr/share/xroad/scripts/secret-store-cluster-bundle.sh"
ENV_FILE="/etc/openbao/xroad-openbao.env"
CONFIG_WRITER="/usr/share/xroad/scripts/secret-store-cluster-config.sh"
TLS_SCRIPT="/usr/share/xroad/scripts/secret-store-generate-tls-certificate.sh"
UNSEAL_SCRIPT="/usr/share/xroad/scripts/secret-store-unseal.sh"
SERVER_SCRIPT="/usr/share/xroad/scripts/secret-store-server.sh"
LISTENER_SCRIPT="${ROOT_DIR}/development/docker/central-server/files/unseal-on-start-listener.sh"
HA_GUIDE="${ROOT_DIR}/doc/Manuals/ig-csha_x-road_6_ha_installation_guide.md"
BASE_CONFIG_FILE="/usr/share/xroad/openbao/xroad-openbao.hcl"
BASE_CONFIG_SOURCE="${PACKAGE_SOURCES}/secret-store-local/usr/share/xroad/openbao/xroad-openbao.hcl"
NODE_CONFIG_DIR="/etc/xroad-secret-store"
NODE_CONFIG_FILE="${NODE_CONFIG_DIR}/node.hcl"
OLD_CONFIG_FILE="/etc/openbao/xroad-openbao.hcl"
DROP_IN_FILE="/usr/lib/systemd/system/openbao.service.d/xroad.conf"
DROP_IN_SOURCE="${PACKAGE_SOURCES}/secret-store-local/usr/lib/systemd/system/openbao.service.d/xroad.conf"
WRITER_DROP_IN_DIR="/etc/systemd/system/openbao.service.d"
BAO_LOG="/var/log/bao.log"
SHIM_CALLS_FILE="/var/log/shim-calls"
SHIM_FAIL_PREFIX="/var/log/shim-fail-"
READY_FILE="/run/test-entrypoint-ready"
OPERATOR_STEP="Copy the root token file ${ROOT_TOKEN_FILE} from another node"
SKIP_LOG="No root token on this node"

FAILURES=0
ROOT_TOKEN=""
RC=0

OUT_FILE="${WORK_DIR}/setup.out"
ERR_FILE="${WORK_DIR}/setup.err"

dump_last_run() {
  local file
  for file in "${OUT_FILE}" "${ERR_FILE}"; do
    if [[ -s "${file}" ]]; then
      log_error "Last setup run, ${file##*.}:"
      sed 's/^/    /' "${file}" >&2
    fi
  done
}

cleanup() {
  local rc=$?
  if [[ ${rc} -ne 0 && ${FAILURES} -eq 0 ]]; then dump_last_run; fi
  docker rm -f "${NODE1_CONTAINER}" "${NODE2_CONTAINER}" "${BASE_ONLY_CONTAINER}" "${ENV_PARSE_CONTAINER}" "${PSQL_CONTAINER}" "${PG_CONTAINER}" >/dev/null 2>&1
  docker network rm "${NETWORK}" >/dev/null 2>&1
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

pass() { log_success "  ok: $1"; }
fail() { log_error "  FAILED: $1"; FAILURES=$((FAILURES + 1)); dump_last_run; }

check() {
  local description="$1"
  shift
  if "$@"; then pass "${description}"; else fail "${description}"; fi
}

check_eq() {
  local description="$1" expected="$2" actual="$3"
  if [[ "${expected}" == "${actual}" ]]; then pass "${description}"; else fail "${description}"; fi
}

check_ne() {
  local description="$1" first="$2" second="$3"
  if [[ "${first}" != "${second}" ]]; then pass "${description}"; else fail "${description}"; fi
}

in_container() { docker exec "${CONTAINER}" "$@"; }

stage_scripts() {
  mkdir -p "${SCRIPTS_DIR}"
  cp -p "${SECRET_STORE_SCRIPTS_DIR}"/* "${BASE_SCRIPTS_DIR}/secret-store-cluster-bundle.sh" "${BASE_SCRIPTS_DIR}/_restore_xroad.sh" "${BASE_SCRIPTS_DIR}/_setup_db.sh" "${SCRIPTS_DIR}/"
}
write_shim() {
  cat >"${WORK_DIR}/curl-shim" <<'SHIM'
#!/bin/sh
case "$*" in
  *auth/token/lookup-self*)
    echo "$*" >>/shim/lookups
    printf '%s' "${SHIM_STATUS:-000}"
    if [ "${SHIM_STATUS:-000}" = "000" ]; then exit 7; fi
    exit 0
    ;;
esac
exec /usr/bin/curl "$@"
SHIM
  cat >"${WORK_DIR}/install-shim" <<'SHIM'
#!/bin/sh
case "$*" in
  *root-token) exit 1 ;;
esac
exec /usr/bin/install "$@"
SHIM
  cat >"${WORK_DIR}/debconf-confmodule" <<'SHIM'
db_get() {
  RET=$(sed -n "s|^$1=||p" /tmp/debconf-values)
  return 0
}
db_stop() { :; }
SHIM
  cat >"${WORK_DIR}/systemd-invoke-shim" <<SHIM
#!/bin/sh
echo "deb-systemd-invoke \$*" >>${SHIM_CALLS_FILE}
exit 0
SHIM
  cat >"${WORK_DIR}/restore-driver" <<'SHIM'
#!/bin/bash
RESTORE_SCRIPT=/usr/share/xroad/scripts/_restore_xroad.sh
source /usr/share/xroad/scripts/_openbao.sh
for function_name in setup_tmp_restore_dir extract_to_tmp_restore_dir remove_old_existing_files restore_openbao restart_services; do
  eval "$(sed -n "/^${function_name} *() *{/,/^}/p" "${RESTORE_SCRIPT}")"
done
die() { echo "$@" >&2; exit 1; }
RESTORE_DIR=/var/tmp/xroad/restore
BACKUP_FILENAME=/tmp/node-a-backup.tar
SKIP_DB_RESTORE=true
SKIP_OPENBAO_RESTORE=false
SKIP_REMOVAL=false
OPENBAO_DATABASE_RESTORE_SCRIPT=/bin/true
OPENBAO_DATABASE_DUMP_FILENAME=/var/lib/xroad/openbao_dbdump.dat
START_CMD="echo start"
BAO_ADDR=https://127.0.0.1:8200
CONF_FILE_LIST=$(find /etc/openbao -not -path /etc/openbao/unseal-keys -not -path /etc/openbao/root-token -type f)
for step in "$@"; do
  "${step}"
done
SHIM
  cat >"${WORK_DIR}/pg-stub" <<'SHIM'
#!/bin/sh
cat >/dev/null
echo "${0##*/} host=$PGHOST port=$PGPORT user=$PGUSER pass=$PGPASSWORD db=$PGDATABASE opts=$PGOPTIONS args=$*" >>/var/log/pg-calls
SHIM
  chmod +x "${WORK_DIR}/pg-stub"
  cat >"${WORK_DIR}/config-confmodule" <<'SHIM'
db_set() { echo "db_set $*" >>/tmp/debconf-calls; return 0; }
db_input() { return 0; }
db_go() { return 0; }
db_stop() { :; }
SHIM
  cat >"${WORK_DIR}/sleep-shim" <<'SHIM'
#!/bin/sh
echo "$1" >>/shim/sleeps
SHIM
  cat >"${WORK_DIR}/listener-unseal-stub" <<'SHIM'
#!/bin/sh
echo called >>/tmp/unseal-calls
wc -c | tr -d ' ' >/tmp/unseal-stdin-bytes
SHIM
  chmod +x "${WORK_DIR}/listener-unseal-stub"
  cat >"${WORK_DIR}/listener-driver" <<'SHIM'
#!/bin/bash
header() { printf 'ver:3.0 server:supervisor serial:1 pool:openbao-unseal poolserial:1 eventname:PROCESS_STATE_RUNNING len:%s\n%s' "${#1}" "$1"; }
events="$(header 'processname:xroad-signer groupname:xroad-signer from_state:STARTING pid:1'; header 'processname:openbao groupname:openbao from_state:STARTING pid:2'; header 'processname:openbao-sidecar groupname:openbao-sidecar from_state:STARTING pid:3')"
reply=$(printf '%s' "${events}" | UNSEAL_SCRIPT=/tmp/listener-unseal-stub bash /listener.sh 2>/dev/null | tr '\n' '|')
echo "reply=${reply}"
echo "calls=$(wc -l </tmp/unseal-calls | tr -d ' ')"
echo "stdin-bytes=$(cat /tmp/unseal-stdin-bytes)"
SHIM
}

wait_for_server() {
  for _ in $(seq 1 60); do
    if in_container curl -sk -o /dev/null "${ADDR}/v1/sys/health"; then return 0; fi
    sleep 1
  done
  log_error "OpenBao did not start in the test container"
  exit 1
}

start_dev_server() {
  ROOT_TOKEN="test-root-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --name "${CONTAINER}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    -e "DEV_ROOT_TOKEN=${ROOT_TOKEN}" \
    --entrypoint sh "${IMAGE}" -c '
      mkdir -p /etc/openbao /etc/xroad
      chown xroad:xroad /etc/xroad
      su -s /bin/sh -c "exec bao server -dev -dev-root-token-id=\$DEV_ROOT_TOKEN -dev-listen-address=127.0.0.1:8200" openbao &
      wait' >/dev/null || exit 1
  docker cp "${WORK_DIR}/curl-shim" "${CONTAINER}:/curl-shim" >/dev/null
  docker cp "${WORK_DIR}/sleep-shim" "${CONTAINER}:/sleep-shim" >/dev/null
  wait_for_server
}

start_postgres() {
  docker rm -f "${NODE1_CONTAINER}" "${NODE2_CONTAINER}" "${PG_CONTAINER}" >/dev/null 2>&1
  CONTAINER="${NODE1_CONTAINER}"
  docker network rm "${NETWORK}" >/dev/null 2>&1
  docker network create "${NETWORK}" >/dev/null || exit 1
  docker run -d --name "${PG_CONTAINER}" --network "${NETWORK}" \
    -e "POSTGRES_PASSWORD=${PG_PASSWORD}" "${PG_IMAGE}" >/dev/null || exit 1
  for _ in $(seq 1 60); do
    if docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  log_error "PostgreSQL did not start in the test container"
  exit 1
}
# run_node_container yes|no: starts the current container with the shims and the X-Road configuration.
# "no" leaves the OpenBao environment file to the cluster bundle import.
run_node_container() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --init --name "${CONTAINER}" --network "${NETWORK}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    -v "${DROP_IN_SOURCE}:${DROP_IN_FILE}:ro" \
    -v "${BASE_CONFIG_SOURCE}:${BASE_CONFIG_FILE}:ro" \
    -e "WRITE_ENV=$1" \
    -e "BAO_PG_CONNECTION_URL=postgres://postgres:${PG_PASSWORD}@${PG_CONTAINER}:5432/postgres?sslmode=disable" \
    --entrypoint sh "${IMAGE}" -c '
      rm -f '"${READY_FILE}"'
      mkdir -p /etc/openbao /opt/openbao/tls /etc/xroad /run/systemd/system /usr/local/share/ca-certificates /usr/local/bin
      chown xroad:xroad /etc/xroad
      if [ "$WRITE_ENV" = yes ]; then
        printf "BAO_PG_CONNECTION_URL=%s\n" "$BAO_PG_CONNECTION_URL" > '"${ENV_FILE}"'
        chown openbao:xroad '"${ENV_FILE}"'
        chmod 640 '"${ENV_FILE}"'
      fi
      for tool in systemctl update-ca-certificates; do
        printf "#!/bin/sh\necho \"\${0##*/} \$*\" >>'"${SHIM_CALLS_FILE}"'\n[ ! -e '"${SHIM_FAIL_PREFIX}"'\${0##*/} ] || { rm -f '"${SHIM_FAIL_PREFIX}"'\${0##*/}; exit 1; }\nexit 0\n" > /usr/local/bin/$tool
        chmod +x /usr/local/bin/$tool
      done
      if [ "$WRITE_ENV" = yes ] && [ ! -f '"${GENERATED_HASH_FILE}"' ]; then
        bash '"${CONFIG_WRITER}"' || exit 1
      fi
      touch '"${READY_FILE}"'
      exec tail -f /dev/null' >/dev/null || exit 1
}
start_real_server() {
  start_postgres
  run_node_container yes
  start_bao
  wait_for_server
}
start_bao() {
  for _ in $(seq 1 60); do
    if in_container test -e "${READY_FILE}" 2>/dev/null; then break; fi
    sleep 1
  done
  docker exec -d "${CONTAINER}" sh -c '
    wrapper=$(sed -n "s/^ExecStart=\(.\)/\1/p" '"${DROP_IN_FILE}"' | tail -n 1)
    exec su -s /bin/sh -c "${wrapper}" openbao >'"${BAO_LOG}"' 2>&1'
}
# run_wrapper_without_env_file; runs the drop-in's ExecStart as the openbao user while the environment file is moved aside.
run_wrapper_without_env_file() {
  run_in_container -- sh -c '
    mv '"${ENV_FILE}"' '"${ENV_FILE}"'.aside
    wrapper=$(sed -n "s/^ExecStart=\(.\)/\1/p" '"${DROP_IN_FILE}"' | tail -n 1)
    timeout 20 su -s /bin/sh -c "${wrapper}" openbao
    rc=$?
    mv '"${ENV_FILE}"'.aside '"${ENV_FILE}"'
    exit $rc'
}
stop_bao() {
  in_container pkill -f '(^|/)bao server'
  for _ in $(seq 1 30); do
    if ! in_container pgrep -f '(^|/)bao server' >/dev/null; then return 0; fi
    sleep 1
  done
  log_error "OpenBao did not stop in the test container"
  exit 1
}
restart_bao() {
  stop_bao
  start_bao
  wait_for_server
}
restart_container() {
  in_container rm -f "${READY_FILE}"
  docker restart "${CONTAINER}" >/dev/null
  start_bao
  wait_for_server
}
# run_in_container [docker exec option ...] -- COMMAND [ARG ...]; sets RC, stdout in OUT_FILE and stderr in ERR_FILE.
run_in_container() {
  local options=()
  while [[ "$1" != -- ]]; do options+=("$1"); shift; done
  shift
  docker exec ${options[@]+"${options[@]}"} "${CONTAINER}" "$@" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}

# run_setup [VAR=value ...]
run_setup() {
  local env_args=(-e "BAO_ADDR=${ADDR}")
  local arg
  for arg in "$@"; do env_args+=(-e "${arg}"); done
  run_in_container "${env_args[@]}" -- bash "${SETUP_SCRIPT}"
}

# run_unseal [address]; runs the script the drop-in names as ExecStartPost.
run_unseal() {
  run_in_container -e "BAO_ADDR=${1:-${ADDR}}" -- timeout 150 bash "${UNSEAL_SCRIPT}"
}

start_hanging_listener() {
  docker exec -d "${CONTAINER}" sh -c 'nc -ll -p 9999 -e sleep 600'
  sleep 1
}

wait_for_active() {
  for _ in $(seq 1 40); do
    if [[ "$(in_container curl -sk -o /dev/null -w '%{http_code}' "${ADDR}/v1/sys/health")" == 200 ]]; then return 0; fi
    sleep 1
  done
  log_error "OpenBao did not become active in the test container"
  exit 1
}

seal_status_field() {
  in_container curl -sk "${ADDR}/v1/sys/seal-status" | jq -r ".$1"
}

run_setup_with_curl_shim() {
  local status="$1"
  shift
  local env_args=(-e "BAO_ADDR=${ADDR}" -e "SHIM_STATUS=${status}")
  local arg
  for arg in "$@"; do env_args+=(-e "${arg}"); done
  run_in_container "${env_args[@]}" -- sh -c \
    'mkdir -p /shim && : >/shim/lookups && : >/shim/sleeps && cp /curl-shim /shim/curl && cp /sleep-shim /shim/sleep && chmod +x /shim/curl /shim/sleep && PATH=/shim:$PATH exec bash '"${SETUP_SCRIPT}"
}

shim_lookup_count() { in_container cat /shim/lookups | wc -l | tr -d ' '; }

shim_slept_seconds() { in_container awk '{ total += $1 } END { print total + 0 }' /shim/sleeps; }

bao_get() {
  in_container curl -sk -H "X-Vault-Token: ${ROOT_TOKEN}" "${ADDR}/v1/$1"
}

mounts_snapshot() {
  bao_get sys/mounts | jq -S '(.data // .) | to_entries | map({path: .key, accessor: .value.accessor})'
}

policy_text() {
  bao_get sys/policies/acl/xroad-policy | jq -r '.data.policy // empty'
}

# Reads a policy on stdin and prints it without comment lines and blank lines.
policy_rules() {
  grep -v -e '^[[:space:]]*#' -e '^[[:space:]]*$'
}

# Prints the HCL block that follows the "Policy." paragraph of the HA guide.
guide_policy_block() {
  awk '/^\*\*Policy\.\*\*/ {seen = 1} seen && /^```hcl$/ {inside = 1; next} inside && /^```$/ {exit} inside' "${HA_GUIDE}"
}

ca_fingerprint() {
  in_container sh -c "curl -sk ${ADDR}/v1/xrd-pki/ca/pem | sha256sum"
}

client_token_hash() {
  in_container sha256sum "${CLIENT_TOKEN_FILE}" 2>/dev/null | cut -d' ' -f1
}

token_accessor_count() {
  in_container curl -sk -X LIST -H "X-Vault-Token: ${ROOT_TOKEN}" "${ADDR}/v1/auth/token/accessors" \
    | jq '.data.keys | length'
}

client_token_status() {
  in_container sh -c "curl -sk -o /dev/null -w '%{http_code}' -H \"X-Vault-Token: \$(cat ${CLIENT_TOKEN_FILE})\" ${ADDR}/v1/auth/token/lookup-self"
}

has_mount() {
  bao_get sys/mounts | jq -e --arg m "$1" '(.data // .) | has($m)' >/dev/null
}

state_snapshot() {
  printf '%s\n%s\n%s\n%s\n' "$(mounts_snapshot)" "$(policy_text)" "$(client_token_hash)" "$(ca_fingerprint)"
}

file_mode() { in_container stat -c %a "$1" 2>/dev/null; }

file_owner() { in_container stat -c %U:%G "$1" 2>/dev/null; }

file_exists() { in_container test -e "$1"; }

file_absent() { in_container test ! -e "$1"; }

# text_absent FILE TEXT: succeeds when FILE exists on the host and does not contain TEXT.
text_absent() {
  [[ -e "$1" && -n "$2" ]] || return 1
  ! grep -qF -- "$2" "$1"
}

# secret_absent FILE TEXT: like text_absent, and fails when the last run left both output files empty.
secret_absent() {
  [[ -s "${OUT_FILE}" || -s "${ERR_FILE}" ]] || return 1
  text_absent "$@"
}

policy_write_count() { grep -c "Writing policy\\.\\.\\." "${ERR_FILE}"; }

check_no_secret_in_output() {
  local label="$1" what="$2" secret="$3"
  check "${label}: stderr holds no ${what}" secret_absent "${ERR_FILE}" "${secret}"
  check "${label}: stdout holds no ${what}" secret_absent "${OUT_FILE}" "${secret}"
}

check_no_issued_secrets_in_output() {
  local label="$1" key
  check_no_secret_in_output "${label}" "root token" "${ROOT_TOKEN}"
  while IFS= read -r key; do
    check_no_secret_in_output "${label}" "unseal key" "${key}"
  done < <(in_container cat "${UNSEAL_KEYS_FILE}" 2>/dev/null)
  check_no_secret_in_output "${label}" "client token" "$(in_container cat "${CLIENT_TOKEN_FILE}" 2>/dev/null)"
}

secret_files_state() {
  in_container sh -c "stat -c '%n %U:%G %a' ${ROOT_TOKEN_FILE} ${UNSEAL_KEYS_FILE} && sha256sum ${ROOT_TOKEN_FILE} ${UNSEAL_KEYS_FILE}" 2>/dev/null
}

check_modes() {
  local label="$1"
  if file_exists "${ROOT_TOKEN_FILE}"; then
    check_eq "${label}: root token file mode" 600 "$(file_mode "${ROOT_TOKEN_FILE}")"
    check_eq "${label}: root token file owner" root:root "$(file_owner "${ROOT_TOKEN_FILE}")"
  fi
  if file_exists "${UNSEAL_KEYS_FILE}"; then
    check_eq "${label}: unseal keys file mode" 600 "$(file_mode "${UNSEAL_KEYS_FILE}")"
    check_eq "${label}: unseal keys file owner" root:root "$(file_owner "${UNSEAL_KEYS_FILE}")"
  fi
  if file_exists "${CLIENT_TOKEN_FILE}"; then check_eq "${label}: client token file mode" 640 "$(file_mode "${CLIENT_TOKEN_FILE}")"; fi
}

check_operator_step() {
  local label="$1"
  check "${label}: stderr names copying the root token file" grep -qF -- "${OPERATOR_STEP}" "${ERR_FILE}"
  check "${label}: stderr names restarting the X-Road services" grep -q "restart the X-Road services" "${ERR_FILE}"
  check "${label}: stderr names no main node" text_absent "${ERR_FILE}" "main node"
  check "${label}: stderr does not ask to copy the client token" text_absent "${ERR_FILE}" "client token file"
}

check_provisioned() {
  local label="$1"
  check "${label}: exit 0" test "${RC}" -eq 0
  check "${label}: xrd-pki mount created" has_mount "xrd-pki/"
  check "${label}: xrd-secret mount created" has_mount "xrd-secret/"
  check "${label}: xrd-ds-secret mount created" has_mount "xrd-ds-secret/"
  check "${label}: policy created" test -n "$(policy_text)"
  check_eq "${label}: client token valid" 200 "$(client_token_status)"
  check_modes "${label}"
}

write_root_token_file() {
  in_container sh -c "printf '%s' '${ROOT_TOKEN}' > ${ROOT_TOKEN_FILE} && chmod 600 ${ROOT_TOKEN_FILE}"
}

scenario_root_token_file() {
  log_info "Scenario: root token file on the node"
  start_dev_server
  write_root_token_file

  run_setup
  check_provisioned "root token file"
  check_eq "root token file: first run writes the policy once" 1 "$(policy_write_count)"
  check_no_issued_secrets_in_output "root token file"
  local first_policy
  first_policy="$(policy_text)"

  log_info "Scenario: second run changes nothing"
  local before after
  before="$(state_snapshot)"
  run_setup
  after="$(state_snapshot)"
  check "second run: exit 0" test "${RC}" -eq 0
  check_eq "second run: mounts, policy, client token and CA unchanged" "${before}" "${after}"
  check_modes "second run"
  check_eq "second run: policy written once" 1 "$(policy_write_count)"

  log_info "Scenario: stale policy is rewritten"
  local stale_payload
  stale_payload='{"policy":"path \"stale/*\" { capabilities = [\"read\"] }"}'
  in_container curl -sk -X PUT -H "X-Vault-Token: ${ROOT_TOKEN}" -d "${stale_payload}" \
    "${ADDR}/v1/sys/policies/acl/xroad-policy" >/dev/null
  check_ne "stale policy: policy replaced with stale text" "${first_policy}" "$(policy_text)"
  run_setup
  check "stale policy: exit 0" test "${RC}" -eq 0
  check_eq "stale policy: policy rewritten" "${first_policy}" "$(policy_text)"

  log_info "Scenario: root token and invalid client token"
  in_container sh -c "printf 'bogus' > ${CLIENT_TOKEN_FILE}"
  local bogus_hash
  bogus_hash="$(client_token_hash)"
  run_setup
  check "invalid client token: exit 0" test "${RC}" -eq 0
  check_ne "invalid client token: token file replaced" "${bogus_hash}" "$(client_token_hash)"
  check_eq "invalid client token: new token valid" 200 "$(client_token_status)"
  check_modes "invalid client token"

  log_info "Scenario: root token and missing client token"
  in_container rm -f "${CLIENT_TOKEN_FILE}"
  run_setup
  check "missing client token: exit 0" test "${RC}" -eq 0
  check_eq "missing client token: new token valid" 200 "$(client_token_status)"
  check_modes "missing client token"

  log_info "Scenario: root token and a client token id from the environment"
  in_container rm -f "${CLIENT_TOKEN_FILE}"
  local token_id="test-client-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
  run_setup "XROAD_SECRET_STORE_TOKEN_OVERRIDE=${token_id}"
  check "token id override: exit 0" test "${RC}" -eq 0
  check_eq "token id override: client token has the requested id" "${token_id}" "$(in_container cat "${CLIENT_TOKEN_FILE}")"
  check_no_issued_secrets_in_output "token id override"

  log_info "Scenario: root token and lookup-self unreachable or failing"
  local hash accessors status
  for status in 000 500 503; do
    hash="$(client_token_hash)"
    accessors="$(token_accessor_count)"
    run_setup_with_curl_shim "${status}"
    check "lookup-self ${status}: exit non-zero" test "${RC}" -ne 0
    check "lookup-self ${status}: at least 10 lookups" test "$(shim_lookup_count)" -ge 10
    check "lookup-self ${status}: retry window covers 18 s" test "$(shim_slept_seconds)" -ge 18
    check_eq "lookup-self ${status}: token file untouched" "${hash}" "$(client_token_hash)"
    check_eq "lookup-self ${status}: no new token issued" "${accessors}" "$(token_accessor_count)"
  done

  log_info "Scenario: no root token and valid client token"
  in_container rm -f "${ROOT_TOKEN_FILE}"
  before="$(state_snapshot)"
  run_setup
  check "no root token, valid client token: exit 0" test "${RC}" -eq 0
  check "no root token, valid client token: skip logged" grep -q "${SKIP_LOG}" "${OUT_FILE}"
  check_eq "no root token, valid client token: state unchanged" "${before}" "$(state_snapshot)"
  check_modes "no root token, valid client token"

  log_info "Scenario: no root token and lookup-self unreachable"
  hash="$(client_token_hash)"
  run_setup_with_curl_shim 000
  check "no root token, lookup-self unreachable: exit non-zero" test "${RC}" -ne 0
  check_eq "no root token, lookup-self unreachable: token file untouched" "${hash}" "$(client_token_hash)"

  log_info "Scenario: no root token and invalid client token"
  in_container sh -c "printf 'bogus' > ${CLIENT_TOKEN_FILE}"
  bogus_hash="$(client_token_hash)"
  local mounts_before policy_before
  mounts_before="$(mounts_snapshot)"
  policy_before="$(policy_text)"
  run_setup
  check "no root token, invalid client token: exit non-zero" test "${RC}" -ne 0
  check_operator_step "no root token, invalid client token"
  check_eq "no root token, invalid client token: token file untouched" "${bogus_hash}" "$(client_token_hash)"
  check_eq "no root token, invalid client token: mounts unchanged" "${mounts_before}" "$(mounts_snapshot)"
  check_eq "no root token, invalid client token: policy unchanged" "${policy_before}" "$(policy_text)"

  log_info "Scenario: no root token and missing client token"
  in_container rm -f "${CLIENT_TOKEN_FILE}"
  run_setup
  check "no root token, missing client token: exit non-zero" test "${RC}" -ne 0
  check_operator_step "no root token, missing client token"
  check "no root token, missing client token: no token file created" file_absent "${CLIENT_TOKEN_FILE}"
}

scenario_bao_token_env() {
  log_info "Scenario: BAO_TOKEN set and no root token file"
  start_dev_server
  run_setup "BAO_TOKEN=${ROOT_TOKEN}"
  check_provisioned "BAO_TOKEN only"
  check "BAO_TOKEN only: no root token file written" file_absent "${ROOT_TOKEN_FILE}"
}

scenario_no_root_token() {
  log_info "Scenario: neither BAO_TOKEN nor root token file, fresh OpenBao"
  start_dev_server
  local mounts_before
  mounts_before="$(mounts_snapshot)"
  run_setup
  check "no root token: skip logged" grep -q "${SKIP_LOG}" "${OUT_FILE}"
  check_eq "no root token: mounts unchanged" "${mounts_before}" "$(mounts_snapshot)"
  check "no root token: no policy written" test -z "$(policy_text)"
  check "no root token: exit non-zero without a client token" test "${RC}" -ne 0
}

scenario_pki_setup_writes_policy() {
  log_info "Scenario: PKI set-up writes the policy on first set-up"
  start_dev_server
  in_container bash -c ". /usr/share/xroad/scripts/_openbao.sh; configure_pki ${ADDR} ${ROOT_TOKEN}" >/dev/null 2>&1
  check "PKI set-up: xrd-pki mount created" has_mount "xrd-pki/"
  check "PKI set-up: policy written" test -n "$(policy_text)"
  local policy_after_pki
  policy_after_pki="$(policy_text)"
  check "PKI set-up: the HA guide holds a policy block" test -n "$(guide_policy_block)"
  check_eq "PKI set-up: the policy block of the HA guide equals the policy written" "$(policy_rules <<<"${policy_after_pki}")" "$(guide_policy_block | policy_rules)"
  run_setup "BAO_TOKEN=${ROOT_TOKEN}"
  check "root run after PKI set-up: exit 0" test "${RC}" -eq 0
  check_eq "root run after PKI set-up: policy unchanged" "${policy_after_pki}" "$(policy_text)"
}

config_state() {
  in_container sh -c "if [ -e ${NODE_CONFIG_FILE} ]; then stat -c '%U:%G %a %i %Y' ${NODE_CONFIG_FILE} && sha256sum ${NODE_CONFIG_FILE}; else echo no-node-file; fi"
}
scenario_initializing_node() {
  log_info "Scenario: node writes the X-Road configuration, initialises and unseals a real OpenBao"
  ADDR="https://127.0.0.1:8200"
  start_real_server
  local listeners
  listeners="$(in_container grep -c '^listener ' "${BASE_CONFIG_FILE}")"
  check_eq "base configuration: one listener" 1 "${listeners}"
  check "base configuration: listener binds 127.0.0.1:8200 only" in_container grep -q '^  address *= "127.0.0.1:8200"$' "${BASE_CONFIG_FILE}"
  check "base configuration: PostgreSQL storage with high availability" in_container grep -q '^  ha_enabled = "true"$' "${BASE_CONFIG_FILE}"
  check "base configuration: api_addr is the local address" in_container grep -q '^api_addr = "https://127.0.0.1:8200"$' "${BASE_CONFIG_FILE}"
  check "single node: the writer wrote no node file" file_absent "${NODE_CONFIG_FILE}"
  check "single node: the writer wrote no file in /etc/openbao" file_absent "${OLD_CONFIG_FILE}"
  check "drop-in: ExecStart is cleared first" in_container grep -qx 'ExecStart=' "${DROP_IN_FILE}"
  check_eq "drop-in: ExecStart is the start script" "${SERVER_SCRIPT}" "$(in_container sed -n 's/^ExecStart=\(.\)/\1/p' "${DROP_IN_FILE}")"
  check "drop-in: the unit loads no environment file" test "$(in_container grep -cE '^EnvironmentFile=.+' "${DROP_IN_FILE}")" -eq 0
  check "drop-in: no ExecStart line runs the environment file through a shell" test "$(in_container grep -cE '^ExecStart=.*(bash|sh) -c' "${DROP_IN_FILE}")" -eq 0
  check_eq "start script: base file first, then the node file" "-config=\"\$base_config\")-config=\"\$node_config\")" \
    "$(in_container grep -o -- '-config="[^"]*")' "${SERVER_SCRIPT}" | tr -d '\n')"
  check_eq "start script: execs bao with the collected -config arguments" 'exec /usr/bin/bao server "${config_args[@]}"' \
    "$(in_container grep -E '^exec ' "${SERVER_SCRIPT}")"
  check "start script: never sources the environment file" test "$(in_container grep -cE '(^|[;&|[:space:]])(\.|source)[[:space:]]+[^[:space:]]*(xroad-openbao\.env|\$env_file)' "${SERVER_SCRIPT}")" -eq 0
  check "drop-in: the upstream environment file is reset" in_container grep -qx 'EnvironmentFile=' "${DROP_IN_FILE}"
  check_eq "drop-in: ExecStartPost runs the unseal-only script" "${UNSEAL_SCRIPT}" "$(in_container sed -n 's/^ExecStartPost=[-+@!:]*//p' "${DROP_IN_FILE}")"
  check "drop-in: the package file sets no restart policy" test "$(in_container grep -cE '^Restart(Sec)?=' "${DROP_IN_FILE}")" -eq 0
  run_wrapper_without_env_file
  check "drop-in: without the environment file the wrapper fails" test "${RC}" -ne 0
  check "drop-in: without the environment file the message names the file" grep -qF "${ENV_FILE}" "${ERR_FILE}"
  check "drop-in: the environment file is back in place" in_container test -f "${ENV_FILE}"
  check "writer: wrote no drop-in" in_container test ! -e "${WRITER_DROP_IN_DIR}"
  check "OpenBao log: no unknown or unsupported field" test "$(in_container grep -c 'unknown or unsupported field' "${BAO_LOG}")" -eq 0
  local config_before
  config_before="$(config_state)"
  run_writer
  check "writer second run: exit 0" test "${RC}" -eq 0
  check_eq "writer second run: files unchanged" "${config_before}" "$(config_state)"
  run_setup
  ROOT_TOKEN="$(in_container cat "${ROOT_TOKEN_FILE}")"
  check_provisioned "initialising node"
  check "initialising node: unseal keys file written" file_exists "${UNSEAL_KEYS_FILE}"
  check_no_issued_secrets_in_output "initialising node"
  check_eq "initialising node: high availability enabled" true "$(bao_get sys/leader | jq -r '.ha_enabled')"
  check_eq "initialising node: PostgreSQL storage" postgresql "$(bao_get sys/seal-status | jq -r '.storage_type')"
  check_eq "initialising node: advertised address is the loopback address" "https://127.0.0.1:8200" "$(leader_address)"
  local before after files_before
  files_before="$(secret_files_state)"
  before="$(state_snapshot)"
  run_setup
  after="$(state_snapshot)"
  check "initialised node rerun: exit 0" test "${RC}" -eq 0
  check_eq "initialised node rerun: state unchanged" "${before}" "${after}"
  check_modes "initialised node rerun"
  check_eq "initialised node rerun: files unchanged" "${files_before}" "$(secret_files_state)"

  log_info "Scenario: setup run after the group of /etc/openbao was changed"
  in_container chgrp -R xroad /etc/openbao
  check_eq "group changed: root token file group" root:xroad "$(file_owner "${ROOT_TOKEN_FILE}")"
  check_eq "group changed: unseal keys file group" root:xroad "$(file_owner "${UNSEAL_KEYS_FILE}")"
  run_setup
  check "group changed rerun: exit 0" test "${RC}" -eq 0
  check_modes "group changed rerun"
  check_eq "group changed rerun: files unchanged" "${files_before}" "$(secret_files_state)"

  log_info "Scenario: symbolic link in place of the root token file"
  local link_target=/tmp/root-token.target
  in_container sh -c "mv ${ROOT_TOKEN_FILE} ${link_target} && ln -s ${link_target} ${ROOT_TOKEN_FILE} && chown openbao:xroad ${link_target} && chmod 640 ${link_target}"
  run_setup
  check "symlink: exit 0" test "${RC}" -eq 0
  check "symlink: stderr names the link" grep -qF "Not changing ${ROOT_TOKEN_FILE}" "${ERR_FILE}"
  check "symlink: still a link" in_container test -L "${ROOT_TOKEN_FILE}"
  check_eq "symlink: target owner unchanged" openbao:xroad "$(file_owner "${link_target}")"
  check_eq "symlink: target mode unchanged" 640 "$(file_mode "${link_target}")"
  check_eq "symlink: unseal keys file still root:root" root:root "$(file_owner "${UNSEAL_KEYS_FILE}")"
  in_container sh -c "rm ${ROOT_TOKEN_FILE} && chown root:root ${link_target} && chmod 600 ${link_target} && mv ${link_target} ${ROOT_TOKEN_FILE}"

  log_info "Scenario: missing root token file"
  in_container mv "${ROOT_TOKEN_FILE}" "${ROOT_TOKEN_FILE}.saved"
  run_setup
  check "missing root token file: exit 0" test "${RC}" -eq 0
  check "missing root token file: no file created" file_absent "${ROOT_TOKEN_FILE}"
  check_eq "missing root token file: unseal keys file still root:root" root:root "$(file_owner "${UNSEAL_KEYS_FILE}")"
  in_container mv "${ROOT_TOKEN_FILE}.saved" "${ROOT_TOKEN_FILE}"
  check_eq "restored: files unchanged" "${files_before}" "$(secret_files_state)"

  log_info "Scenario: unseal-only script on a sealed OpenBao after a restart"
  restart_container
  check_eq "after restart: OpenBao is sealed" true "$(seal_status_field sealed)"
  run_unseal
  check "unseal-only: exit 0" test "${RC}" -eq 0
  check_eq "unseal-only: OpenBao is unsealed" false "$(seal_status_field sealed)"
  check_no_issued_secrets_in_output "unseal-only"
  wait_for_active

  log_info "Scenario: unseal-only script on an unsealed OpenBao"
  run_unseal
  check "unseal-only on unsealed: exit 0" test "${RC}" -eq 0
  check "unseal-only on unsealed: reports it is already unsealed" grep -q "already unsealed" "${OUT_FILE}"
  check_eq "unseal-only on unsealed: OpenBao stays unsealed" false "$(seal_status_field sealed)"
  check_eq "unseal-only on unsealed: state unchanged" "${before}" "$(state_snapshot)"

  log_info "Scenario: unseal-only script with OpenBao unreachable"
  run_unseal "https://127.0.0.1:9"
  check "unseal-only unreachable: exit 0" test "${RC}" -eq 0
  check "unseal-only unreachable: stops after the last readiness attempt" grep -q "attempt 30/30" "${OUT_FILE}"
  check "unseal-only unreachable: logs why" grep -q "not reachable" "${ERR_FILE}"

  log_info "Scenario: unseal-only script with a listener that accepts and never answers"
  start_hanging_listener
  run_unseal "http://127.0.0.1:9999"
  check "unseal-only hung: exit 0" test "${RC}" -eq 0
  check "unseal-only hung: each call is cut off by its time limit" grep -q "Connection failed (exit code: 28)" "${ERR_FILE}"
  check "unseal-only hung: logs that the run was cut off" grep -q "did not complete within" "${ERR_FILE}"

  log_info "Scenario: unseal-only script on a sealed OpenBao without usable unseal keys"
  restart_container
  check_eq "no keys: OpenBao is sealed" true "$(seal_status_field sealed)"
  in_container mv "${UNSEAL_KEYS_FILE}" "${UNSEAL_KEYS_FILE}.saved"
  run_unseal
  check "missing keys file: exit 0" test "${RC}" -eq 0
  check "missing keys file: logs the keys file is missing or empty" grep -q "missing or empty" "${ERR_FILE}"
  check_eq "missing keys file: OpenBao stays sealed" true "$(seal_status_field sealed)"
  in_container sh -c ": > ${UNSEAL_KEYS_FILE}"
  run_unseal
  check "empty keys file: exit 0" test "${RC}" -eq 0
  check "empty keys file: logs the keys file is missing or empty" grep -q "missing or empty" "${ERR_FILE}"
  check_eq "empty keys file: OpenBao stays sealed" true "$(seal_status_field sealed)"
  in_container mv "${UNSEAL_KEYS_FILE}.saved" "${UNSEAL_KEYS_FILE}"

  log_info "Scenario: setup script on a sealed OpenBao after a restart"
  restart_container
  check_eq "after restart: configuration unchanged" "${config_before}" "$(config_state)"
  run_setup
  check_provisioned "after restart"
  check_eq "after restart: state unchanged" "${before}" "$(state_snapshot)"
}

TLS_CERT_FILE="/opt/openbao/tls/tls.crt"
STATE_DIR="/var/lib/xroad-secret-store"
GENERATED_HASH_FILE="${STATE_DIR}/generated-certificate.sha256"
TRUST_MARKER_FILE="${STATE_DIR}/trusted-certificate.sha256"
OPENBAO_OWNED_MARKER_FILE="/opt/openbao/tls/trusted-certificate.sha256"
TRUST_ANCHOR_FILE="/usr/local/share/ca-certificates/openbao.crt"
run_writer() { run_in_container -- bash "${CONFIG_WRITER}" "$@"; }
run_writer_unprivileged() { run_in_container -u openbao -- bash "${CONFIG_WRITER}" "$@"; }
cert_names() {
  in_container openssl x509 -in "${TLS_CERT_FILE}" -noout -ext subjectAltName | tail -n +2 | tr -d ' ' | tr ',' '\n' | sort | paste -sd, -
}
expected_names() { printf '%s\n' "$@" | sort | paste -sd, -; }
cert_hash() { in_container sha256sum "${TLS_CERT_FILE}" | cut -d' ' -f1; }
trust_anchor_hash() { in_container sha256sum "${TRUST_ANCHOR_FILE}" | cut -d' ' -f1; }
marker_hash() { in_container cat "${TRUST_MARKER_FILE}" 2>/dev/null; }
fail_next_call() { in_container touch "${SHIM_FAIL_PREFIX}$1"; }
config_hash() { in_container sh -c "sha256sum ${NODE_CONFIG_FILE} 2>/dev/null || echo no-node-file" | cut -d' ' -f1; }
leader_address() { in_container curl -sk "${ADDR}/v1/sys/leader" | jq -r '.leader_address'; }
clear_shim_calls() { in_container sh -c ": > ${SHIM_CALLS_FILE}"; }
shim_calls() { in_container cat "${SHIM_CALLS_FILE}" 2>/dev/null; }
shim_call_count() { shim_calls | grep -c -- "$1" || true; }
listener_addresses() {
  in_container sh -c "cat ${BASE_CONFIG_FILE} ${NODE_CONFIG_FILE} 2>/dev/null" | sed -n 's/^  address *= "\(.*\)"$/\1/p' | paste -sd, -
}
config_line() { in_container grep -x -- "$1" "${NODE_CONFIG_FILE}" >/dev/null; }
config_lacks() { ! in_container grep -q -- "$1" "${NODE_CONFIG_FILE}"; }
serves_name() {
  [[ "$(in_container curl -s -o /dev/null -w '%{http_code}' --cacert "${TLS_CERT_FILE}" \
    --resolve "$1:8200:$2" "https://$1:8200/v1/sys/seal-status")" == 200 ]]
}
not_listening() { ! in_container curl -sk -o /dev/null -m 3 "https://$1:8200/v1/sys/seal-status"; }
check_unchanged_run() {
  local label="$1" config_before cert_before
  shift
  config_before="$(config_state)"
  cert_before="$(cert_hash)"
  clear_shim_calls
  run_writer "$@"
  check "${label}: exit 0" test "${RC}" -eq 0
  check_eq "${label}: configuration unchanged" "${config_before}" "$(config_state)"
  check_eq "${label}: certificate unchanged" "${cert_before}" "$(cert_hash)"
  check_eq "${label}: no trust install, daemon-reload or restart" "" "$(shim_calls)"
}
# run_start_script_with_env_content CONTENT; runs the drop-in's ExecStart as the openbao user with CONTENT as the environment file.
run_start_script_with_env_content() {
  printf '%s' "$1" | docker exec -i "${CONTAINER}" sh -c 'cat > '"${ENV_FILE}"
  run_in_container -- sh -c '
    wrapper=$(sed -n "s/^ExecStart=\(.\)/\1/p" '"${DROP_IN_FILE}"' | tail -n 1)
    timeout 20 su -s /bin/sh -c "${wrapper}" openbao'
}
pg_exec() { docker exec -i -u postgres "${PG_CONTAINER}" psql -U postgres -qtA "$@"; }
# check_start_with_url LABEL ROLE PASSWORD URL: starts bao through the drop-in with URL as the connection setting
# and checks that it connects to PostgreSQL as ROLE. The server accepts only the exact PASSWORD.
check_start_with_url() {
  local label="$1" role="$2" password="$3" url="$4" i tables=0
  pg_exec -v ON_ERROR_STOP=1 -v "role=${role}" -v "pw=${password}" >/dev/null <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pw') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'role', :'role') \gexec
SQL
  docker exec -i "${CONTAINER}" sh -c 'cat > '"${ENV_FILE}" < <(printf 'BAO_PG_CONNECTION_URL=%s\n' "${url}")
  start_bao
  for i in $(seq 1 30); do
    tables="$(pg_exec -d "${role}" -c "SELECT count(*) FROM pg_tables WHERE tableowner = '${role}'")"
    if [[ "${tables}" -gt 0 ]]; then break; fi
    sleep 1
  done
  check "${label}: bao connected to PostgreSQL as the role" test "${tables}" -gt 0
  check "${label}: no process argument list holds the password" test "$(docker exec -u root "${CONTAINER}" sh -c 'cat /proc/[0-9]*/cmdline 2>/dev/null' | grep -acF -- "${password}")" -eq 0
  stop_bao
}
scenario_start_script_reads_env_as_data() {
  log_info "Scenario: the OpenBao start script reads the environment file as data"
  ADDR="https://127.0.0.1:8200"
  start_postgres
  run_node_container yes
  for _ in $(seq 1 60); do
    if in_container test -e "${READY_FILE}" 2>/dev/null; then break; fi
    sleep 1
  done
  check "start script: not owned by the OpenBao user" test "$(in_container stat -c '%U' "${SERVER_SCRIPT}")" != openbao
  check "start script: the OpenBao user can run it" in_container su -s /bin/sh -c "test -x ${SERVER_SCRIPT}" openbao
  run_start_script_with_env_content $'BAO_PG_USER=x\n'
  check "missing key: exit non-zero" test "${RC}" -ne 0
  check "missing key: the message names the key" grep -qF "BAO_PG_CONNECTION_URL" "${ERR_FILE}"
  check "missing key: the message names the file" grep -qF "${ENV_FILE}" "${ERR_FILE}"
  run_start_script_with_env_content $'BAO_PG_CONNECTION_URL=\n'
  check "empty key: exit non-zero" test "${RC}" -ne 0
  check "empty key: the message names the key" grep -qF "BAO_PG_CONNECTION_URL" "${ERR_FILE}"
  local password='Pw$x'"'"'q'"'"' "d" ;`b` e'
  local dsn_password=${password//\'/\\\'}
  check_start_with_url "key-value connection string" hostile_dsn "${password}" \
    "host=${PG_CONTAINER} port=5432 user=hostile_dsn password='${dsn_password}' dbname=hostile_dsn sslmode=disable"
  local url_password='Pw$x'"'"'q'"'"';z!*(),='
  check_start_with_url "connection URL" hostile_url "${url_password}" \
    "postgres://hostile_url:${url_password}@${PG_CONTAINER}:5432/hostile_url?sslmode=disable"
  CONTAINER="${NODE1_CONTAINER}"
}
scenario_cluster_member() {
  log_info "Scenario: single server, then convert to a cluster member"
  ADDR="https://127.0.0.1:8200"
  start_real_server
  local node_ip
  node_ip="$(in_container hostname -i | awk '{print $1}')"
  check_eq "single server: certificate names only 127.0.0.1" "IPAddress:127.0.0.1" "$(cert_names)"
  check_eq "single server: one listener on 127.0.0.1" "127.0.0.1:8200" "$(listener_addresses)"
  check "single server: no node file" file_absent "${NODE_CONFIG_FILE}"
  check "single server: no file in /etc/openbao" file_absent "${OLD_CONFIG_FILE}"
  check_eq "single server: trust installed once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "single server: no daemon-reload" 0 "$(shim_call_count daemon-reload)"
  check_eq "single server: trust marker holds the certificate hash" "$(cert_hash)" "$(marker_hash)"
  check_unchanged_run "single server rerun"
  local single_cert
  single_cert="$(cert_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}"
  check "convert: exit 0" test "${RC}" -eq 0
  check_eq "convert: listeners on 127.0.0.1 and the node address, same port" "127.0.0.1:8200,${node_ip}:8200" "$(listener_addresses)"
  check_eq "convert: node file owner and mode" root:root:644 "$(file_owner "${NODE_CONFIG_FILE}"):$(file_mode "${NODE_CONFIG_FILE}")"
  check_eq "convert: node file directory owner and mode" root:root:755 "$(file_owner "${NODE_CONFIG_DIR}"):$(file_mode "${NODE_CONFIG_DIR}")"
  check_eq "convert: no file in /etc/openbao" absent "$(file_absent "${OLD_CONFIG_FILE}" && echo absent)"
  check "convert: api_addr is the node address" config_line "api_addr = \"https://${node_ip}:8200\""
  check "convert: cluster_addr is the node address on the cluster port" config_line "cluster_addr = \"https://${node_ip}:8201\""
  check "convert: no standby reads key" config_lacks "disable_standby_reads"
  check_eq "convert: certificate names 127.0.0.1 and the node address" "$(expected_names "IPAddress:${node_ip}" IPAddress:127.0.0.1)" "$(cert_names)"
  check_ne "convert: certificate regenerated" "${single_cert}" "$(cert_hash)"
  check_eq "convert: trust installed again" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "convert: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  check_eq "convert: no daemon-reload" 0 "$(shim_call_count daemon-reload)"
  check "convert: wrote no drop-in" in_container test ! -e "${WRITER_DROP_IN_DIR}"
  restart_bao
  check "convert: serves on 127.0.0.1 with the certificate" serves_name 127.0.0.1 127.0.0.1
  check "convert: serves on the node address with the certificate" serves_name "${node_ip}" "${node_ip}"
  check "convert: OpenBao log has no unknown or unsupported field" test "$(in_container grep -c 'unknown or unsupported field' "${BAO_LOG}")" -eq 0
  run_setup
  ROOT_TOKEN="$(in_container cat "${ROOT_TOKEN_FILE}")"
  check_provisioned "convert: initialised on the converted node"
  check_eq "convert: high availability enabled" true "$(bao_get sys/leader | jq -r '.ha_enabled')"
  check_eq "convert: advertised address is the node address" "https://${node_ip}:8200" "$(bao_get sys/leader | jq -r '.leader_address')"
  check_unchanged_run "convert rerun" --node-address "${node_ip}"
  log_info "Scenario: node host"
  local before_cert
  before_cert="$(cert_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host Node-1.Example.Test
  check "node host: exit 0" test "${RC}" -eq 0
  check "node host: api_addr uses the host" config_line 'api_addr = "https://node-1.example.test:8200"'
  check "node host: cluster_addr uses the host" config_line 'cluster_addr = "https://node-1.example.test:8201"'
  check_eq "node host: listeners unchanged" "127.0.0.1:8200,${node_ip}:8200" "$(listener_addresses)"
  check_eq "node host: certificate names 127.0.0.1, the address and the host" "$(expected_names DNS:node-1.example.test "IPAddress:${node_ip}" IPAddress:127.0.0.1)" "$(cert_names)"
  check_ne "node host: certificate regenerated" "${before_cert}" "$(cert_hash)"
  check_eq "node host: trust installed again" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "node host: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  restart_bao
  check "node host: serves the host name" serves_name node-1.example.test "${node_ip}"
  run_unseal
  wait_for_active
  check_eq "node host: advertised address is the host" "https://node-1.example.test:8200" "$(leader_address)"
  check "node host: still serves 127.0.0.1" serves_name 127.0.0.1 127.0.0.1
  check_unchanged_run "node host rerun" --node-address "${node_ip}" --node-host node-1.example.test
  log_info "Scenario: certificate replaced while the configuration stays the same"
  in_container bash "${TLS_SCRIPT}" >/dev/null 2>&1
  check_eq "stale certificate: names only 127.0.0.1" "IPAddress:127.0.0.1" "$(cert_names)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "stale certificate: exit 0" test "${RC}" -eq 0
  check_eq "stale certificate: names restored" "$(expected_names DNS:node-1.example.test "IPAddress:${node_ip}" IPAddress:127.0.0.1)" "$(cert_names)"
  check_eq "stale certificate: trust installed again" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "stale certificate: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  restart_bao
  local host_names="IP:127.0.0.1,IP:${node_ip},DNS:node-1.example.test"
  log_info "Scenario: the trust update fails and the writer is run again"
  in_container bash "${TLS_SCRIPT}" >/dev/null 2>&1
  fail_next_call update-ca-certificates
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "failed trust: exit non-zero" test "${RC}" -ne 0
  check_eq "failed trust: trust attempted once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "failed trust: OpenBao not restarted" 0 "$(shim_call_count 'restart openbao.service')"
  check_ne "failed trust: marker does not match the certificate" "$(cert_hash)" "$(marker_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "retried trust: exit 0" test "${RC}" -eq 0
  check_eq "retried trust: trust installed once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "retried trust: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  check_eq "retried trust: marker holds the certificate hash" "$(cert_hash)" "$(marker_hash)"
  log_info "Scenario: certificate replaced with the same names while trust is missing"
  in_container bash "${TLS_SCRIPT}" "${host_names}" >/dev/null 2>&1
  check_eq "same names: names unchanged" "$(expected_names DNS:node-1.example.test "IPAddress:${node_ip}" IPAddress:127.0.0.1)" "$(cert_names)"
  check_ne "same names: marker does not match the new certificate" "$(cert_hash)" "$(marker_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "same names: exit 0" test "${RC}" -eq 0
  check_eq "same names: trust installed once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "same names: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  check_eq "same names: marker holds the certificate hash" "$(cert_hash)" "$(marker_hash)"
  log_info "Scenario: the openbao user replaces the certificate and writes a marker"
  check_eq "generated record: holds the certificate hash" "$(cert_hash)" "$(in_container cat "${GENERATED_HASH_FILE}")"
  check "generated record: not writable by the openbao user" in_container su -s /bin/sh -c "test ! -w ${GENERATED_HASH_FILE} && test ! -w ${STATE_DIR}" openbao
  in_container rm -f "${TRUST_MARKER_FILE}"
  docker exec -u openbao "${CONTAINER}" sh -c "openssl req -x509 -newkey rsa:2048 -nodes -keyout /tmp/forged.key -out ${TLS_CERT_FILE} \
    -subj /CN=Forged -days 2 -addext 'subjectAltName = ${host_names}' -addext 'basicConstraints = critical,CA:TRUE' 2>/dev/null \
    && sha256sum ${TLS_CERT_FILE} | cut -d' ' -f1 >${OPENBAO_OWNED_MARKER_FILE}"
  check_ne "forged certificate: differs from the generated one" "$(in_container cat "${GENERATED_HASH_FILE}")" "$(cert_hash)"
  local anchor_before
  anchor_before="$(trust_anchor_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "forged certificate: exit non-zero" test "${RC}" -ne 0
  check "forged certificate: message names the file" grep -q "${TLS_CERT_FILE}" "${ERR_FILE}"
  check_eq "forged certificate: no trust install, daemon-reload or restart" "" "$(shim_calls)"
  check_eq "forged certificate: trust anchor unchanged" "${anchor_before}" "$(trust_anchor_hash)"
  check_eq "forged certificate: trust marker still missing" "" "$(marker_hash)"
  in_container rm -f "${OPENBAO_OWNED_MARKER_FILE}"
  in_container bash "${TLS_SCRIPT}" "${host_names}" >/dev/null 2>&1
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "forged certificate removed: exit 0" test "${RC}" -eq 0
  log_info "Scenario: certificate and no generated record"
  in_container rm -f "${GENERATED_HASH_FILE}"
  local unrecorded_cert
  unrecorded_cert="$(cert_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "no record: exit 0" test "${RC}" -eq 0
  check_ne "no record: certificate regenerated" "${unrecorded_cert}" "$(cert_hash)"
  check_eq "no record: record holds the certificate hash" "$(cert_hash)" "$(in_container cat "${GENERATED_HASH_FILE}")"
  check_eq "no record: trust installed once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "no record: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  check_eq "no record: marker holds the certificate hash" "$(cert_hash)" "$(marker_hash)"
  log_info "Scenario: certificate without a trust marker"
  in_container rm -f "${TRUST_MARKER_FILE}"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host node-1.example.test
  check "no marker: exit 0" test "${RC}" -eq 0
  check_eq "no marker: trust installed once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "no marker: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  check_eq "no marker: marker holds the certificate hash" "$(cert_hash)" "$(marker_hash)"
  check_unchanged_run "marked certificate rerun" --node-address "${node_ip}" --node-host node-1.example.test
  restart_bao
  log_info "Scenario: convert again with a new node host"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host other.example.test
  check "new host: exit 0" test "${RC}" -eq 0
  check "new host: api_addr uses the new host" config_line 'api_addr = "https://other.example.test:8200"'
  check_eq "new host: certificate regenerated, 127.0.0.1 kept, old host gone" "$(expected_names DNS:other.example.test "IPAddress:${node_ip}" IPAddress:127.0.0.1)" "$(cert_names)"
  check_eq "new host: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  restart_bao
  check "new host: serves the new host name" serves_name other.example.test "${node_ip}"
  log_info "Scenario: standby reads"
  local cert_before_standby
  cert_before_standby="$(cert_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host other.example.test --disable-standby-reads
  check "standby reads off: exit 0" test "${RC}" -eq 0
  check "standby reads off: top-level disable_standby_reads = true" config_line 'disable_standby_reads = true'
  check_eq "standby reads off: certificate unchanged" "${cert_before_standby}" "$(cert_hash)"
  check_eq "standby reads off: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  restart_bao
  check "standby reads off: OpenBao log has no unknown or unsupported field" test "$(in_container grep -c 'unknown or unsupported field' "${BAO_LOG}")" -eq 0
  check "standby reads off: serves the host name" serves_name other.example.test "${node_ip}"
  check_unchanged_run "standby reads off rerun" --node-address "${node_ip}" --node-host other.example.test --disable-standby-reads
  run_writer --node-address "${node_ip}" --node-host other.example.test
  check "standby reads back on: key removed" config_lacks "disable_standby_reads"
  log_info "Scenario: invalid arguments change nothing"
  local config_before_invalid
  config_before_invalid="$(config_hash)"
  clear_shim_calls
  run_writer --node-address "not-an-address"
  check "invalid address: exit non-zero" test "${RC}" -ne 0
  run_writer --node-address "10.0.0.256"
  check "address out of range: exit non-zero" test "${RC}" -ne 0
  run_writer --node-address "${node_ip}" --node-host 'a"; evil = "b'
  check "injected host: exit non-zero" test "${RC}" -ne 0
  run_writer --node-host other.example.test
  check "host without address: exit non-zero" test "${RC}" -ne 0
  run_writer --bogus
  check "unknown option: exit non-zero" test "${RC}" -ne 0
  run_writer --node-address 192.0.2.77
  check "address of another host: exit non-zero" test "${RC}" -ne 0
  check "address of another host: stderr says it is not an address of this host" grep -qF "is not an IPv4 address of this host" "${ERR_FILE}"
  check_eq "invalid arguments: configuration unchanged" "${config_before_invalid}" "$(config_hash)"
  check_eq "invalid arguments: no restart or trust install" "" "$(shim_calls)"
  log_info "Scenario: a missing or empty database connection file changes nothing"
  local state_before_refusal
  state_before_refusal="$(config_state)"
  in_container mv "${ENV_FILE}" "${ENV_FILE}.saved"
  clear_shim_calls
  run_writer --node-address "${node_ip}" --node-host other.example.test --disable-standby-reads
  check "absent connection file: exit non-zero" test "${RC}" -ne 0
  check "absent connection file: stderr names the file" grep -qF -- "${ENV_FILE}" "${ERR_FILE}"
  check_eq "absent connection file: configuration unchanged" "${state_before_refusal}" "$(config_state)"
  check_eq "absent connection file: no restart or trust install" "" "$(shim_calls)"
  in_container sh -c ": > ${ENV_FILE}"
  run_writer --node-address "${node_ip}" --node-host other.example.test --disable-standby-reads
  check "empty connection file: exit non-zero" test "${RC}" -ne 0
  check "empty connection file: stderr names the file" grep -qF -- "${ENV_FILE}" "${ERR_FILE}"
  check_eq "empty connection file: configuration unchanged" "${state_before_refusal}" "$(config_state)"
  check_eq "empty connection file: no restart or trust install" "" "$(shim_calls)"
  in_container mv -f "${ENV_FILE}.saved" "${ENV_FILE}"
  log_info "Scenario: a failed write is reported"
  run_writer_unprivileged --node-address "${node_ip}" --node-host unwritable.example.test
  check "unwritable configuration: exit non-zero" test "${RC}" -ne 0
  check "unwritable configuration: stderr names the node file" grep -qF -- "${NODE_CONFIG_FILE}" "${ERR_FILE}"
  check_eq "unwritable configuration: configuration unchanged" "${config_before_invalid}" "$(config_hash)"
  log_info "Scenario: the single-node options are checked"
  run_writer --single-node --node-address "${node_ip}"
  check "single node with an address: exit non-zero" test "${RC}" -ne 0
  run_writer --single-node --node-host other.example.test
  check "single node with a host: exit non-zero" test "${RC}" -ne 0
  run_writer --disable-standby-reads
  check "standby reads without an address: exit non-zero" test "${RC}" -ne 0
  check_eq "single-node options: node file unchanged" "${config_before_invalid}" "$(config_hash)"
  check_eq "single-node options: no restart or trust install" "" "$(shim_calls)"
  log_info "Scenario: a node file and no options"
  local state_before_guard
  state_before_guard="$(config_state)"
  clear_shim_calls
  run_writer
  check "node file without options: exit non-zero" test "${RC}" -ne 0
  check "node file without options: stderr names the node file" grep -qF -- "${NODE_CONFIG_FILE}" "${ERR_FILE}"
  check "node file without options: stderr names --single-node" grep -qF -- "--single-node" "${ERR_FILE}"
  check_eq "node file without options: node file unchanged" "${state_before_guard}" "$(config_state)"
  check_eq "node file without options: no restart or trust install" "" "$(shim_calls)"
  local cluster_names
  cluster_names="$(cert_names)"
  log_info "Scenario: back to a single server"
  clear_shim_calls
  run_writer --single-node
  check "single again: exit 0" test "${RC}" -eq 0
  check "single again: node file removed" file_absent "${NODE_CONFIG_FILE}"
  check_eq "single again: one listener on 127.0.0.1" "127.0.0.1:8200" "$(listener_addresses)"
  check_eq "single again: certificate names only 127.0.0.1" "IPAddress:127.0.0.1" "$(cert_names)"
  check_eq "single again: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  restart_bao
  check "single again: serves 127.0.0.1" serves_name 127.0.0.1 127.0.0.1
  check "single again: node address no longer served" not_listening "${node_ip}"
  run_unseal
  wait_for_active
  check_eq "single again: advertised address is the loopback address" "https://127.0.0.1:8200" "$(leader_address)"
  check_unchanged_run "single again rerun" --single-node
  log_info "Scenario: single, cluster, single, cluster ends as a direct cluster run"
  run_writer --node-address "${node_ip}" --node-host other.example.test
  check "cluster again: exit 0" test "${RC}" -eq 0
  check_eq "cluster again: node file equals the direct cluster run" "${config_before_invalid}" "$(config_hash)"
  check_eq "cluster again: node file owner and mode" root:root:644 "$(file_owner "${NODE_CONFIG_FILE}"):$(file_mode "${NODE_CONFIG_FILE}")"
  check_eq "cluster again: certificate names equal the direct cluster run" "${cluster_names}" "$(cert_names)"
  restart_bao
  run_unseal
  wait_for_active
  check_eq "cluster again: advertised address is the host" "https://other.example.test:8200" "$(leader_address)"
  check "cluster again: serves the node address" serves_name other.example.test "${node_ip}"
}
run_join() { run_in_container -e "BAO_ADDR=${ADDR}" -- bash "${SETUP_SCRIPT}" --join; }
run_bundle() { run_in_container -- bash "${BUNDLE_SCRIPT}" "$@"; }
store_initialised() { in_container curl -sk "${ADDR}/v1/sys/init" | jq -r '.initialized'; }
bundle_hashes() { in_container sha256sum "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}" 2>/dev/null | cut -d' ' -f1; }
wait_for_ready_file() {
  for _ in $(seq 1 60); do
    if in_container test -e "${READY_FILE}" 2>/dev/null; then return 0; fi
    sleep 1
  done
  log_error "The test container did not become ready"
  exit 1
}
copy_between_nodes() {
  docker cp "${NODE1_CONTAINER}:$1" "${WORK_DIR}/transfer" >/dev/null && docker cp "${WORK_DIR}/transfer" "${NODE2_CONTAINER}:$1" >/dev/null
}
scenario_join_guards() {
  log_info "Scenario: join mode never initialises OpenBao"
  ADDR="https://127.0.0.1:8200"
  start_real_server
  local file other
  check_eq "join guards: the store starts uninitialised" false "$(store_initialised)"
  run_join
  check "no bundle files: exit non-zero" test "${RC}" -ne 0
  check "no bundle files: stderr names the unseal keys file" grep -qF -- "${UNSEAL_KEYS_FILE}" "${ERR_FILE}"
  check "no bundle files: stderr names the root token file" grep -qF -- "${ROOT_TOKEN_FILE}" "${ERR_FILE}"
  check_eq "no bundle files: still uninitialised" false "$(store_initialised)"
  in_container sh -c "printf 'placeholder' > ${UNSEAL_KEYS_FILE}; printf 'placeholder' > ${ROOT_TOKEN_FILE}; chmod 600 ${UNSEAL_KEYS_FILE} ${ROOT_TOKEN_FILE}"
  for file in "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}"; do
    in_container mv "${file}" "${file}.saved"
    run_join
    check "missing ${file}: exit non-zero" test "${RC}" -ne 0
    check "missing ${file}: stderr names it" grep -qF -- "${file}" "${ERR_FILE}"
    for other in "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}"; do
      if [[ "${other}" != "${file}" ]]; then check "missing ${file}: stderr does not name ${other}" text_absent "${ERR_FILE}" "${other}"; fi
    done
    check_eq "missing ${file}: still uninitialised" false "$(store_initialised)"
    in_container mv "${file}.saved" "${file}"
  done
  run_join
  check "uninitialised store: exit non-zero" test "${RC}" -ne 0
  check "uninitialised store: stderr says it is not initialized" grep -qF "is not initialized" "${ERR_FILE}"
  check_eq "uninitialised store: still uninitialised" false "$(store_initialised)"
  check_eq "uninitialised store: root token file untouched" placeholder "$(in_container cat "${ROOT_TOKEN_FILE}")"
  check_eq "uninitialised store: unseal keys file untouched" placeholder "$(in_container cat "${UNSEAL_KEYS_FILE}")"
}
scenario_join_node() {
  log_info "Scenario: single server, convert, export, import on a second node, join"
  ADDR="https://127.0.0.1:8200"
  start_real_server
  local ip1 ip2 first_hashes first_client_hash first_mounts before after file
  ip1="$(in_container hostname -i | awk '{print $1}')"
  run_setup
  ROOT_TOKEN="$(in_container cat "${ROOT_TOKEN_FILE}")"
  check_provisioned "first node, single server"
  run_writer --node-address "${ip1}"
  check "first node: convert exit 0" test "${RC}" -eq 0
  restart_bao
  run_setup
  check_provisioned "first node, converted"
  first_hashes="$(bundle_hashes)"
  first_client_hash="$(client_token_hash)"
  first_mounts="$(mounts_snapshot)"

  log_info "Scenario: export on the first node"
  run_bundle export /tmp/cluster-bundle.tar
  check "export: exit 0" test "${RC}" -eq 0
  check_eq "export: archive mode" 600 "$(file_mode /tmp/cluster-bundle.tar)"
  check_eq "export: archive owner" root:root "$(file_owner /tmp/cluster-bundle.tar)"
  check_eq "export: archive holds the three files and no client token" root-token,unseal-keys,xroad-openbao.env \
    "$(in_container tar -tf /tmp/cluster-bundle.tar | sort | paste -sd, -)"
  check_no_issued_secrets_in_output "export"
  run_bundle export /tmp/cluster-bundle.tar
  check "export over an existing archive: exit non-zero" test "${RC}" -ne 0
  check "export over an existing archive: stderr says it refuses" grep -qF "Refusing to overwrite" "${ERR_FILE}"
  in_container mv "${UNSEAL_KEYS_FILE}" "${UNSEAL_KEYS_FILE}.saved"
  run_bundle export /tmp/missing-file.tar
  check "export without the unseal keys file: exit non-zero" test "${RC}" -ne 0
  check "export without the unseal keys file: stderr names it" grep -qF -- "${UNSEAL_KEYS_FILE}" "${ERR_FILE}"
  check "export without the unseal keys file: no archive written" file_absent /tmp/missing-file.tar
  in_container mv "${UNSEAL_KEYS_FILE}.saved" "${UNSEAL_KEYS_FILE}"
  in_container sh -c 'mkdir /tmp/bad && cp /etc/openbao/xroad-openbao.env /etc/openbao/unseal-keys /etc/openbao/root-token /tmp/bad/ && printf x > /tmp/bad/extra && cd /tmp/bad && tar -cf /tmp/bad-bundle.tar xroad-openbao.env unseal-keys root-token extra && mkdir /tmp/old && cp /etc/openbao/xroad-openbao.env /tmp/old/openbao.env && cp /etc/openbao/unseal-keys /etc/openbao/root-token /tmp/old/ && tar -cf /tmp/old-bundle.tar -C /tmp/old openbao.env unseal-keys root-token'

  log_info "Scenario: import on the second node"
  CONTAINER="${NODE2_CONTAINER}"
  run_node_container no
  wait_for_ready_file
  ip2="$(in_container hostname -i | awk '{print $1}')"
  CONTAINER="${NODE1_CONTAINER}"
  copy_between_nodes /tmp/cluster-bundle.tar
  copy_between_nodes /tmp/bad-bundle.tar
  copy_between_nodes /tmp/old-bundle.tar
  CONTAINER="${NODE2_CONTAINER}"
  run_bundle import /tmp/bad-bundle.tar
  check "import with an unknown entry: exit non-zero" test "${RC}" -ne 0
  check "import with an unknown entry: nothing imported" file_absent "${ENV_FILE}"
  run_bundle import /tmp/old-bundle.tar
  check "import of an old-format bundle: exit non-zero" test "${RC}" -ne 0
  check "import of an old-format bundle: stderr lists the expected names" grep -qF "must hold exactly: xroad-openbao.env unseal-keys root-token" "${ERR_FILE}"
  check "import of an old-format bundle: the environment file is not written" file_absent "${ENV_FILE}"
  check "import of an old-format bundle: no upstream environment file is written" file_absent /etc/openbao/openbao.env
  check "import of an old-format bundle: the unseal keys file is not written" file_absent "${UNSEAL_KEYS_FILE}"
  in_container sh -c "printf 'keepme' > ${ROOT_TOKEN_FILE}"
  run_bundle import /tmp/cluster-bundle.tar
  check "import over an existing file: exit non-zero" test "${RC}" -ne 0
  check "import over an existing file: stderr names it" grep -qF -- "${ROOT_TOKEN_FILE}" "${ERR_FILE}"
  check_eq "import over an existing file: the file is unchanged" keepme "$(in_container cat "${ROOT_TOKEN_FILE}")"
  check "import over an existing file: the environment file is not written" file_absent "${ENV_FILE}"
  check "import over an existing file: the unseal keys file is not written" file_absent "${UNSEAL_KEYS_FILE}"
  in_container rm -f "${ROOT_TOKEN_FILE}"
  docker cp "${WORK_DIR}/install-shim" "${NODE2_CONTAINER}:/usr/local/bin/install" >/dev/null
  in_container chmod +x /usr/local/bin/install
  run_bundle import /tmp/cluster-bundle.tar
  check "import with a failing install step: exit non-zero" test "${RC}" -ne 0
  check "import with a failing install step: the environment file is rolled back" file_absent "${ENV_FILE}"
  check "import with a failing install step: the unseal keys file is rolled back" file_absent "${UNSEAL_KEYS_FILE}"
  check "import with a failing install step: the root token file is not written" file_absent "${ROOT_TOKEN_FILE}"
  in_container rm -f /usr/local/bin/install
  run_bundle import /tmp/cluster-bundle.tar
  check "import: exit 0" test "${RC}" -eq 0
  check_eq "import: the three files equal the first node's" "${first_hashes}" "$(bundle_hashes)"
  for file in "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}"; do
    check_eq "import: ${file} mode" 600 "$(file_mode "${file}")"
    check_eq "import: ${file} owner" root:root "$(file_owner "${file}")"
  done
  check_no_secret_in_output "import" "root token" "${ROOT_TOKEN}"
  run_bundle import /tmp/cluster-bundle.tar
  check "second import: exit non-zero" test "${RC}" -ne 0
  check_eq "second import: files unchanged" "${first_hashes}" "$(bundle_hashes)"

  log_info "Scenario: join on the second node"
  in_container sh -c "chown -R openbao:openbao /etc/openbao && chgrp -R xroad /etc/openbao"
  check_eq "package steps: root token file owner is the OpenBao user" openbao:xroad "$(file_owner "${ROOT_TOKEN_FILE}")"
  check_eq "package steps: unseal keys file owner is the OpenBao user" openbao:xroad "$(file_owner "${UNSEAL_KEYS_FILE}")"
  run_writer --node-address "${ip2}"
  check "second node: convert exit 0" test "${RC}" -eq 0
  start_bao
  wait_for_server
  check_eq "second node: starts sealed" true "$(seal_status_field sealed)"
  run_join
  check "join: exit 0" test "${RC}" -eq 0
  check "join: never initialises" text_absent "${OUT_FILE}" "Initializing OpenBao"
  check_eq "join: OpenBao is unsealed" false "$(seal_status_field sealed)"
  check_eq "join: client token valid" 200 "$(client_token_status)"
  check_ne "join: the node's own client token differs from the first node's" "${first_client_hash}" "$(client_token_hash)"
  check_modes "join"
  check_no_issued_secrets_in_output "join"
  check_eq "join: the three files are unchanged" "${first_hashes}" "$(bundle_hashes)"
  check_eq "join: mounts equal the first node's" "${first_mounts}" "$(mounts_snapshot)"
  before="$(state_snapshot)"
  run_join
  after="$(state_snapshot)"
  check "join rerun: exit 0" test "${RC}" -eq 0
  check_eq "join rerun: state unchanged" "${before}" "${after}"
  check_eq "join rerun: files unchanged" "${first_hashes}" "$(bundle_hashes)"
  check_modes "join rerun"
  CONTAINER="${NODE1_CONTAINER}"
  check_eq "first node: client token still valid" 200 "$(client_token_status)"
  check_eq "first node: client token unchanged" "${first_client_hash}" "$(client_token_hash)"
}
run_postinst() { run_in_container -e "BAO_ADDR=${ADDR}" -- bash /tmp/postinst configure; }
scenario_postinst_join() {
  log_info "Scenario: Debian postinst of a joining node"
  CONTAINER="${NODE2_CONTAINER}"
  ADDR="https://127.0.0.1:8200"
  local ip2 config_before
  ip2="$(in_container hostname -i | awk '{print $1}')"
  in_container sh -c 'mkdir -p /usr/share/debconf /etc/xroad/backup.d && : > /etc/xroad/backup.d/10_openbao'
  docker cp "${POSTINST_FILE}" "${CONTAINER}:/tmp/postinst" >/dev/null || exit 1
  docker cp "${WORK_DIR}/debconf-confmodule" "${CONTAINER}:/usr/share/debconf/confmodule" >/dev/null || exit 1
  docker cp "${WORK_DIR}/systemd-invoke-shim" "${CONTAINER}:/usr/local/bin/deb-systemd-invoke" >/dev/null || exit 1
  in_container chmod +x /usr/local/bin/deb-systemd-invoke
  config_before="$(config_hash)"
  in_container sh -c "printf 'xroad-common/secret-store-join=true\n' > /tmp/debconf-values"
  run_postinst
  check "join without a node address: exit non-zero" test "${RC}" -ne 0
  check "join without a node address: stderr names the node address question" grep -qF "xroad-common/secret-store-node-address" "${ERR_FILE}"
  check_eq "join without a node address: configuration unchanged" "${config_before}" "$(config_hash)"
  in_container sh -c "printf 'xroad-common/secret-store-join=true\nxroad-common/secret-store-node-address=${ip2}\n' > /tmp/debconf-values"
  in_container sh -c "chown root:root ${ENV_FILE} && chmod 600 ${ENV_FILE}"
  check_eq "join with a node address: the imported environment file starts as root-only" root:root:600 "$(file_owner "${ENV_FILE}"):$(file_mode "${ENV_FILE}")"
  run_postinst
  check "join with a node address: exit 0" test "${RC}" -eq 0
  check "join with a node address: the node joins the cluster" grep -qF "Joining the OpenBao cluster" "${OUT_FILE}"
  check_eq "join with a node address: configuration unchanged" "${config_before}" "$(config_hash)"
  check_eq "join with a node address: the environment file is owned by the OpenBao user" openbao:xroad "$(file_owner "${ENV_FILE}")"
  check_eq "join with a node address: the environment file mode" 640 "$(file_mode "${ENV_FILE}")"
}
scenario_restore_keeps_node_config() {
  log_info "Scenario: restore leaves the node file alone and unseals OpenBao"
  CONTAINER="${NODE2_CONTAINER}"
  ADDR="https://127.0.0.1:8200"
  local config_before env_before
  in_container sh -c 'mkdir -p /etc/xroad/backup.d /var/lib/xroad /tmp/node-a/etc/openbao /tmp/node-a/etc/xroad /tmp/node-a/var/lib/xroad &&
    : > /etc/xroad/backup.d/10_openbao &&
    printf "BAO_PG_CONNECTION_URL=archive\n" > /tmp/node-a/etc/openbao/xroad-openbao.env &&
    printf x > /tmp/node-a/etc/xroad/archive-marker &&
    printf dump > /tmp/node-a/var/lib/xroad/openbao_dbdump.dat &&
    tar -cf /tmp/node-a-backup.tar -C /tmp/node-a etc var'
  docker cp "${WORK_DIR}/restore-driver" "${CONTAINER}:/tmp/restore-driver" >/dev/null || exit 1
  config_before="$(config_hash)"
  env_before="$(in_container sha256sum "${ENV_FILE}")"
  restart_bao
  check_eq "restore: OpenBao starts sealed" true "$(seal_status_field sealed)"
  run_restore_driver setup_tmp_restore_dir extract_to_tmp_restore_dir remove_old_existing_files restore_openbao restart_services
  check "restore: exit 0" test "${RC}" -eq 0
  check_eq "restore: the node's OpenBao configuration is kept" "${config_before}" "$(config_hash)"
  check_eq "restore: the node file owner and mode" root:root:644 "$(file_owner "${NODE_CONFIG_FILE}"):$(file_mode "${NODE_CONFIG_FILE}")"
  check "restore: no copy is made beside the node file" file_absent "${NODE_CONFIG_FILE}.restored"
  check "restore: the script no longer handles the OpenBao configuration file" test "$(grep -c 'xroad-openbao.hcl' "${BASE_SCRIPTS_DIR}/_restore_xroad.sh")" -eq 0
  check_eq "restore: the node's OpenBao environment file is kept" "${env_before}" "$(in_container sha256sum "${ENV_FILE}")"
  check_eq "restore: OpenBao is unsealed" false "$(seal_status_field sealed)"
  restart_bao
  in_container mv "${UNSEAL_KEYS_FILE}" "${UNSEAL_KEYS_FILE}.saved"
  run_restore_driver restart_services
  check "restore without usable unseal keys: exit non-zero" test "${RC}" -ne 0
  check "restore without usable unseal keys: stderr says OpenBao is still sealed" grep -qF "still sealed" "${ERR_FILE}"
  check_eq "restore without usable unseal keys: OpenBao stays sealed" true "$(seal_status_field sealed)"
  in_container mv "${UNSEAL_KEYS_FILE}.saved" "${UNSEAL_KEYS_FILE}"
  CONTAINER="${NODE1_CONTAINER}"
}
run_postrm() { run_in_container -- bash /tmp/postrm "$1"; }
scenario_postrm_purge() {
  log_info "Scenario: Debian postrm removes the node file on purge only"
  CONTAINER="${NODE2_CONTAINER}"
  docker cp "${POSTRM_FILE}" "${CONTAINER}:/tmp/postrm" >/dev/null || exit 1
  in_container cp -p "${NODE_CONFIG_FILE}" /tmp/node.hcl.saved
  run_postrm remove
  check "postrm remove: exit 0" test "${RC}" -eq 0
  check "postrm remove: the node file is kept" in_container test -f "${NODE_CONFIG_FILE}"
  in_container touch "${NODE_CONFIG_DIR}/other"
  run_postrm purge
  check "postrm purge: exit 0" test "${RC}" -eq 0
  check "postrm purge: the node file is removed" file_absent "${NODE_CONFIG_FILE}"
  check "postrm purge: a directory that is not empty is kept" in_container test -d "${NODE_CONFIG_DIR}"
  in_container rm "${NODE_CONFIG_DIR}/other"
  run_postrm purge
  check "postrm purge of an empty directory: exit 0" test "${RC}" -eq 0
  check "postrm purge of an empty directory: the directory is removed" file_absent "${NODE_CONFIG_DIR}"
  in_container sh -c "mkdir -p ${NODE_CONFIG_DIR} && cp -p /tmp/node.hcl.saved ${NODE_CONFIG_FILE}"
}
run_restore_driver() { run_in_container -- bash /tmp/restore-driver "$@"; }
driver_field() { sed -n "s/^$1=//p" "${OUT_FILE}"; }
scenario_supervisor_listener() {
  log_info "Scenario: supervisor listener unseals OpenBao when the openbao program starts"
  docker run --rm \
    -v "${LISTENER_SCRIPT}:/listener.sh:ro" \
    -v "${WORK_DIR}/listener-unseal-stub:/tmp/listener-unseal-stub:ro" \
    -v "${WORK_DIR}/listener-driver:/tmp/listener-driver:ro" \
    --entrypoint bash "${IMAGE}" /tmp/listener-driver >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
  check "listener: exit 0" test "${RC}" -eq 0
  check_eq "listener: answers READY, then RESULT 2 OK to each of the three events" \
    'READY|RESULT 2|OKREADY|RESULT 2|OKREADY|RESULT 2|OKREADY|' "$(driver_field reply)"
  check_eq "listener: runs the unseal script once, for processname:openbao only" 1 "$(driver_field calls)"
  check_eq "listener: the unseal script reads no supervisor input" 0 "$(driver_field stdin-bytes)"
}
scenario_import_base_only() {
  log_info "Scenario: import on a host that has only the xroad-base scripts"
  local file first_hashes
  CONTAINER="${NODE1_CONTAINER}"
  if ! file_exists /tmp/cluster-bundle.tar; then
    log_error "scenario_import_base_only imports the bundle that scenario_join_node exports on the first node; run scenario_join_node first"
    exit 1
  fi
  first_hashes="$(bundle_hashes)"
  docker cp "${NODE1_CONTAINER}:/tmp/cluster-bundle.tar" "${WORK_DIR}/transfer" >/dev/null || exit 1
  docker rm -f "${BASE_ONLY_CONTAINER}" >/dev/null 2>&1
  docker run -d --init --name "${BASE_ONLY_CONTAINER}" \
    -v "${BASE_SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    --entrypoint sh "${IMAGE}" -c 'exec tail -f /dev/null' >/dev/null || exit 1
  docker cp "${WORK_DIR}/transfer" "${BASE_ONLY_CONTAINER}:/tmp/cluster-bundle.tar" >/dev/null || exit 1
  CONTAINER="${BASE_ONLY_CONTAINER}"
  check_eq "base only: no other secret-store script is present" secret-store-cluster-bundle.sh \
    "$(in_container ls /usr/share/xroad/scripts | grep -E 'secret-store|openbao' | paste -sd, -)"
  check "base only: no OpenBao directory yet" file_absent /etc/openbao
  run_bundle import /tmp/cluster-bundle.tar
  check "base only: import exit 0" test "${RC}" -eq 0
  check_eq "base only: the three files equal the first node's" "${first_hashes}" "$(bundle_hashes)"
  for file in "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}"; do
    check_eq "base only: ${file} mode" 600 "$(file_mode "${file}")"
    check_eq "base only: ${file} owner" root:root "$(file_owner "${file}")"
  done
  run_bundle check
  check "base only: check exit 0" test "${RC}" -eq 0
  run_bundle import /tmp/cluster-bundle.tar
  check "base only: a second import refuses" test "${RC}" -ne 0
  CONTAINER="${NODE1_CONTAINER}"
}
put_env_file() {
  docker exec -i "${CONTAINER}" sh -c "cat > ${ENV_FILE} && chown openbao:xroad ${ENV_FILE} && chmod 640 ${ENV_FILE}"
}
write_hostile_env_file() {
  put_env_file <<'HOSTILE'
BAO_PG_USER=openbao
BAO_PG_PASSWORD=hostile-test-password
BAO_PG_HOST=db; touch /tmp/hostile-marker-semicolon
BAO_PG_PORT=5432
BAO_PG_DATABASE=openbao
BAO_PG_SCHEMA=openbao
BAO_PG_CONNECTION_URL=`touch /tmp/hostile-marker-backtick`
touch /tmp/hostile-marker-command
HOSTILE
}
env_by_source() {
  in_container bash -c 'set -a; . "$1"; shift; for key in "$@"; do printf "%s=%s\n" "$key" "${!key}"; done' _ "${ENV_FILE}" "${ENV_KEYS[@]}"
}
env_by_parser() {
  in_container bash -c '. '"${ENV_HELPER_SCRIPT}"'; file=$1; shift; for key in "$@"; do printf "%s=%s\n" "$key" "$(read_env_value "$file" "$key")"; done' _ "${ENV_FILE}" "${ENV_KEYS[@]}"
}
check_parser_matches_source() {
  check_eq "$1: the parser returns the same values as sourcing the file" "$(env_by_source)" "$(env_by_parser)"
}
hostile_markers() { in_container sh -c 'ls /tmp/hostile-marker-* 2>/dev/null'; }
clear_hostile_markers() { in_container sh -c 'rm -f /tmp/hostile-marker-*'; }
clear_pg_calls() { in_container sh -c ": > ${PG_CALLS_FILE}"; }
pg_calls() { in_container cat "${PG_CALLS_FILE}"; }
# config_database_host_answer: runs the Debian config script and prints the database host it offers to debconf.
config_database_host_answer() {
  in_container sh -c ": > ${DEBCONF_CALLS_FILE}"
  run_in_container -- bash /tmp/secret-store-local.config configure
  in_container sed -n 's/^db_set xroad-common\/database-host //p' "${DEBCONF_CALLS_FILE}"
}
scenario_env_file_parsing() {
  log_info "Scenario: root-run scripts read the OpenBao environment file as data"
  local first_call env_hash password="Pw-with=equals_and-dash" expected
  CONTAINER="${ENV_PARSE_CONTAINER}"
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --init --name "${CONTAINER}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    --entrypoint sh "${IMAGE}" -c 'exec tail -f /dev/null' >/dev/null || exit 1
  in_container mkdir -p /etc/openbao /etc/xroad /usr/share/debconf /usr/local/bin
  docker cp "${WORK_DIR}/pg-stub" "${CONTAINER}:/usr/local/bin/pg-stub" >/dev/null || exit 1
  docker cp "${WORK_DIR}/config-confmodule" "${CONTAINER}:/usr/share/debconf/confmodule" >/dev/null || exit 1
  docker cp "${CONFIG_SCRIPT_SOURCE}" "${CONTAINER}:/tmp/secret-store-local.config" >/dev/null || exit 1
  in_container sh -c 'for tool in psql pg_dump pg_restore; do cp /usr/local/bin/pg-stub /usr/local/bin/$tool; done'

  run_in_container -- bash "${INIT_DB_SCRIPT}"
  check "init-db format: the script writes the file, exit 0" test "${RC}" -eq 0
  check_eq "init-db format: owner and mode" openbao:xroad:640 "$(file_owner "${ENV_FILE}"):$(file_mode "${ENV_FILE}")"
  check_parser_matches_source "init-db format"
  check_eq "init-db format: the config script offers the host and port" "127.0.0.1:5432" "$(config_database_host_answer)"
  first_call="$(pg_calls)"
  env_hash="$(in_container sha256sum "${ENV_FILE}")"
  check_ne "init-db format: the first run called psql" "" "${first_call}"
  clear_pg_calls
  run_in_container -- bash "${INIT_DB_SCRIPT}"
  check "init-db format: a second run, exit 0" test "${RC}" -eq 0
  check "init-db format: a second run loads the credentials" grep -qF "Successfully loaded credentials" "${OUT_FILE}"
  check_eq "init-db format: the second run connects with the values the first run generated" "${first_call}" "$(pg_calls)"
  check_eq "init-db format: the file is kept" "${env_hash}" "$(in_container sha256sum "${ENV_FILE}")"

  sed -e "s|{{database_admin_password}}|${password}|" -e "s|{{database_host}}|db.example.test|" "${ANSIBLE_ENV_TEMPLATE}" | put_env_file
  check_parser_matches_source "ansible template format"
  check_eq "ansible template format: the config script offers the host and port" "db.example.test:5432" "$(config_database_host_answer)"
  clear_pg_calls
  run_in_container -- bash "${BACKUP_DB_SCRIPT}" /tmp/dump
  check "ansible template format: backup exit 0" test "${RC}" -eq 0
  check_eq "ansible template format: backup dumps with the file's values" \
    "pg_dump host=db.example.test port=5432 user= pass=${password} db= opts=-c client-min-messages=warning -c search_path=openbao args=-n openbao -x -O -F c -U openbao -f /tmp/dump -d openbao" \
    "$(pg_calls)"
  clear_pg_calls
  run_in_container -- bash "${RESTORE_DB_SCRIPT}" /tmp/dump
  check "ansible template format: restore exit 0" test "${RC}" -eq 0
  check "ansible template format: restore connects with the file's values" \
    grep -qxF "psql host= port= user=openbao pass=${password} db=openbao opts=-c client-min-messages=warning -c search_path=openbao args=-v ON_ERROR_STOP=1 -h db.example.test -p 5432 -qtA -v bao_user=openbao -v bao_db=openbao" <(pg_calls)
  check "ansible template format: restore runs pg_restore with the file's values" \
    grep -qxF "pg_restore host=db.example.test port=5432 user=openbao pass=${password} db= opts= args=--no-owner --single-transaction --clean -d openbao --schema=openbao /tmp/dump" <(pg_calls)

  printf 'BAO_PG_CONNECTION_URL=postgres://postgres:single-line-password@pg:5432/postgres?sslmode=disable\n' | put_env_file
  check_parser_matches_source "single-line format"
  check_eq "single-line format: the config script offers no host" "" "$(config_database_host_answer)"

  printf 'BAO_PG_HOST=first\nBAO_PG_HOST=second\r\nBAO_PG_PORT=5432=x\nBAO_PG_USER=last-line-without-newline' | put_env_file
  expected="$(printf '%s\n' BAO_PG_USER=last-line-without-newline BAO_PG_PASSWORD= BAO_PG_HOST=second BAO_PG_PORT=5432=x BAO_PG_DATABASE= BAO_PG_SCHEMA= BAO_PG_CONNECTION_URL=)"
  check_eq "line rules: the last line of a key wins, a carriage return is dropped, the first = splits, a missing key is empty" "${expected}" "$(env_by_parser)"

  write_hostile_env_file
  clear_hostile_markers
  in_container bash -c ". ${ENV_FILE}" >/dev/null 2>&1
  check_ne "hostile file: sourcing it would run its commands" "" "$(hostile_markers)"
  clear_hostile_markers
  clear_pg_calls
  run_in_container -- bash "${INIT_DB_SCRIPT}"
  check "hostile file: init-db exit 0" test "${RC}" -eq 0
  check_eq "hostile file: init-db runs no command from the file" "" "$(hostile_markers)"
  run_in_container -- bash "${BACKUP_DB_SCRIPT}" /tmp/dump
  check "hostile file: backup exit 0" test "${RC}" -eq 0
  check_eq "hostile file: backup runs no command from the file" "" "$(hostile_markers)"
  run_in_container -- bash "${RESTORE_DB_SCRIPT}" /tmp/dump
  check "hostile file: restore exit 0" test "${RC}" -eq 0
  check_eq "hostile file: restore runs no command from the file" "" "$(hostile_markers)"
  check_eq "hostile file: the config script takes the host as text" "db; touch /tmp/hostile-marker-semicolon:5432" "$(config_database_host_answer)"
  check_eq "hostile file: the config script runs no command from the file" "" "$(hostile_markers)"

  local accepted refused long_name
  long_name="$(printf 'a%.0s' $(seq 1 128))"
  for accepted in openbao openbao@server.example-1 a.b-c_1 0db _x "${long_name}"; do
    run_in_container -- bash -c '. "$1"; require_env_name BAO_PG_USER "$2"' _ "${ENV_HELPER_SCRIPT}" "${accepted}"
    check_eq "name pattern: accepts ${accepted:0:40}" 0 "${RC}"
  done
  for refused in "" "a b" "a=b" "a'b" 'a"b' 'a\b' "a;b" "a|b" 'a$b' 'a`b' "-a" ".a" "@a" $'a\nb' "${long_name}a" \
    "host=db port=5999 dbname=x" "openbao -c log_statement=all"; do
    run_in_container -- bash -c '. "$1"; require_env_name BAO_PG_SCHEMA "$2"' _ "${ENV_HELPER_SCRIPT}" "${refused}"
    check_eq "name pattern: refuses ${refused:0:40}" 1 "${RC}"
    check "name pattern: the message names the key" grep -qF "Invalid BAO_PG_SCHEMA" "${ERR_FILE}"
  done
  CONTAINER="${NODE1_CONTAINER}"
}
psql_admin() { docker exec -i -u postgres "${CONTAINER}" psql -qtA "$@"; }
psql_reset() {
  psql_admin -v ON_ERROR_STOP=1 >/dev/null <<'SQL'
SELECT format('DROP DATABASE %I WITH (FORCE)', datname) FROM pg_database WHERE datname NOT IN ('postgres', 'template0', 'template1') \gexec
SELECT format('DROP ROLE %I', rolname) FROM pg_roles WHERE rolname <> 'postgres' AND rolname NOT LIKE 'pg\_%' \gexec
SQL
}
# psql_count NAME VALUE [DATABASE]: counts the rows of the catalog query chosen by NAME that match VALUE.
psql_count() {
  local query
  case "$1" in
    database) query="SELECT count(*) FROM pg_database WHERE datname = :'v'" ;;
    role) query="SELECT count(*) FROM pg_roles WHERE rolname = :'v'" ;;
    schema) query="SELECT count(*) FROM information_schema.schemata WHERE schema_name = :'v'" ;;
    schema_owner) query="SELECT schema_owner FROM information_schema.schemata WHERE schema_name = :'v'" ;;
  esac
  printf '%s\n' "${query}" | psql_admin -d "${3:-postgres}" -v "v=$2"
}
# psql_login USER PASSWORD DATABASE: prints the user name when the password is accepted over the network.
psql_login() {
  docker exec -e "PGPASSWORD=$2" "${CONTAINER}" bash -c 'psql -h "$(hostname -i)" -U "$1" -d "$2" -qtA -c "SELECT current_user"' _ "$1" "$3" 2>/dev/null
}
set_psql_mode() {
  if [[ "$1" == remote ]]; then
    printf 'postgres.connection.password=%s\n' "${PG_PASSWORD}" | docker exec -i "${CONTAINER}" sh -c 'cat > /etc/xroad.properties'
  else
    in_container rm -f /etc/xroad.properties
  fi
}
# write_psql_env_file USER PASSWORD HOST PORT DATABASE SCHEMA
write_psql_env_file() {
  printf 'BAO_PG_USER=%s\nBAO_PG_PASSWORD=%s\nBAO_PG_HOST=%s\nBAO_PG_PORT=%s\nBAO_PG_DATABASE=%s\nBAO_PG_SCHEMA=%s\nBAO_PG_CONNECTION_URL=unused\n' "$@" | put_env_file
}
env_file_value() {
  in_container bash -c '. '"${ENV_HELPER_SCRIPT}"'; read_env_value "$1" "$2"' _ "${ENV_FILE}" "$1"
}
PSQL_INVOCATIONS_FILE="/tmp/psql-invocations"
PSQL_CMDLINES_FILE="/tmp/psql-cmdlines"
LISTENER_RECEIVED_FILE="/tmp/listener-received"
LISTENER_PID_FILE="/tmp/listener-pid"
LISTENER_PORT=5999

# install_psql_shim: puts a psql in front of the real one. It records that it started and the argument lists of
# all processes (su and the init script included) before it runs the real psql.
install_psql_shim() {
  cat >"${WORK_DIR}/psql-shim" <<'SHIM'
#!/bin/sh
echo started >>/tmp/psql-invocations
for cmdline in /proc/[0-9]*/cmdline; do
  tr '\0' ' ' <"$cmdline" 2>/dev/null
  echo
done >>/tmp/psql-cmdlines
exec /usr/bin/psql "$@"
SHIM
  docker cp "${WORK_DIR}/psql-shim" "${CONTAINER}:/usr/local/bin/psql" >/dev/null || exit 1
  in_container chmod +x /usr/local/bin/psql
  in_container sh -c "touch ${PSQL_INVOCATIONS_FILE} ${PSQL_CMDLINES_FILE} ${LISTENER_RECEIVED_FILE} && chmod 666 ${PSQL_INVOCATIONS_FILE} ${PSQL_CMDLINES_FILE} ${LISTENER_RECEIVED_FILE}"
}
clear_psql_shim_logs() { in_container sh -c ": > ${PSQL_INVOCATIONS_FILE}; : > ${PSQL_CMDLINES_FILE}"; }
psql_invocations() { in_container cat "${PSQL_INVOCATIONS_FILE}"; }
cmdlines_contain() { in_container grep -qF -- "$1" "${PSQL_CMDLINES_FILE}"; }
cmdlines_lack() { ! in_container grep -qF -- "$1" "${PSQL_CMDLINES_FILE}"; }

# check_psql_password_not_in_arguments DESCRIPTION PASSWORD: the last init run sampled the argument lists of all
# processes when psql started. The sample holds the psql arguments and never the password.
check_psql_password_not_in_arguments() {
  check "$1: the sampled argument lists include the psql run" cmdlines_contain "bao_db="
  check "$1: no process argument list holds the role password" cmdlines_lack "$2"
}

start_listener() {
  in_container sh -c ": > ${LISTENER_RECEIVED_FILE}; : > ${LISTENER_PID_FILE}"
  docker exec -d "${CONTAINER}" perl -MIO::Socket::INET -e '
    $server = IO::Socket::INET->new(Listen => 5, LocalAddr => "127.0.0.1", LocalPort => $ARGV[0], ReuseAddr => 1) or die;
    open(PID, ">", $ARGV[1]); print PID $$; close(PID);
    while ($client = $server->accept) {
      $client->recv($data, 4096);
      open(RX, ">>", $ARGV[2]); print RX "connection: $data\n"; close(RX);
      close($client);
    }' "${LISTENER_PORT}" "${LISTENER_PID_FILE}" "${LISTENER_RECEIVED_FILE}"
  local i
  for i in $(seq 1 50); do
    if in_container test -s "${LISTENER_PID_FILE}"; then return 0; fi
    sleep 0.1
  done
  return 1
}
stop_listener() { in_container sh -c "kill \$(cat ${LISTENER_PID_FILE}) 2>/dev/null; : > ${LISTENER_PID_FILE}"; }
listener_received() { in_container cat "${LISTENER_RECEIVED_FILE}"; }

text_absent_in_outputs() {
  text_absent "${OUT_FILE}" "$1" && text_absent "${ERR_FILE}" "$1"
}

# check_name_refused MODE KEY VALUE: the init, backup and restore scripts stop on the value of KEY before they
# start psql, name the key and print nothing of the password.
check_name_refused() {
  local mode="$1" key="$2" value="$3" user=openbao database=openbao schema=openbao script
  local password="Refused-pw-8a3f6" label="${mode} mode, ${key}=${3:0:30}"
  case "${key}" in
    BAO_PG_USER) user="${value}" ;;
    BAO_PG_DATABASE) database="${value}" ;;
    BAO_PG_SCHEMA) schema="${value}" ;;
  esac
  write_psql_env_file "${user}" "${password}" 127.0.0.1 5432 "${database}" "${schema}"
  clear_psql_shim_logs
  clear_hostile_markers
  for script in "${INIT_DB_SCRIPT}" "${BACKUP_DB_SCRIPT}" "${RESTORE_DB_SCRIPT}"; do
    run_in_container -- bash "${script}" /tmp/refused-dump
    check_eq "${label}, ${script##*/}: exit 1" 1 "${RC}"
    check "${label}, ${script##*/}: the message names the key" grep -qF "Invalid ${key}" "${ERR_FILE}"
    check "${label}, ${script##*/}: no output holds the password" text_absent_in_outputs "${password}"
  done
  check_eq "${label}: psql never started" "" "$(psql_invocations)"
  check_eq "${label}: no command from the env file runs" "" "$(hostile_markers)"
}

# check_psql_hostile_case MODE LABEL USER PASSWORD PORT DATABASE SCHEMA EXPECTED_RC
# Runs the init script with the given values in the env file. EXPECTED_RC is a number or "any".
check_psql_hostile_case() {
  local mode="$1" label="$2" user="$3" password="$4" port="$5" database="$6" schema="$7" expected_rc="$8"
  local description="${mode} mode, ${label}"
  psql_reset
  clear_hostile_markers
  set_psql_mode "${mode}"
  write_psql_env_file "${user}" "${password}" 127.0.0.1 "${port}" "${database}" "${schema}"
  clear_psql_shim_logs
  run_in_container -- bash "${INIT_DB_SCRIPT}"
  if [[ "${expected_rc}" == any ]]; then
    pass "${description}: exit ${RC}"
  else
    check_eq "${description}: exit ${expected_rc}" "${expected_rc}" "${RC}"
  fi
  check_eq "${description}: no command from the env file runs" "" "$(hostile_markers)"
  if [[ "${expected_rc}" == 0 ]]; then
    check_eq "${description}: the database is created under the literal name" 1 "$(psql_count database "${database}")"
    check_eq "${description}: the role is created under the literal name" 1 "$(psql_count role "${user}")"
    check_eq "${description}: the schema is created under the literal name" 1 "$(psql_count schema "${schema}" "${database}")"
    check_eq "${description}: the role logs in with its password" "${user}" "$(psql_login "${user}" "${password}" "${database}")"
    check_psql_password_not_in_arguments "${description}" "${password}"
  fi
}
# start_central_server_container: a bare container with the init scripts, a crudini shim and no database.
start_central_server_container() {
  CONTAINER="${ENV_PARSE_CONTAINER}"
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --init --name "${CONTAINER}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    --entrypoint sh "${IMAGE}" -c 'exec tail -f /dev/null' >/dev/null || exit 1
  in_container mkdir -p /etc/openbao /etc/xroad /usr/local/bin
  cat >"${WORK_DIR}/crudini-shim" <<'SHIM'
#!/bin/sh
sed -n "s/^$4[[:space:]]*=[[:space:]]*//p" "$2" | { read -r value || exit 1; printf '%s' "${value}"; }
SHIM
  docker cp "${WORK_DIR}/crudini-shim" "${CONTAINER}:/usr/local/bin/crudini" >/dev/null || exit 1
  in_container chmod +x /usr/local/bin/crudini
}

scenario_central_server_host_list() {
  log_info "Scenario: the Central Server host-list rule in the OpenBao storage step"
  local hosts="db1.example.test:5432,db2.example.test:5432"
  start_central_server_container
  cat >"${WORK_DIR}/psql-writable-stub" <<'SHIM'
#!/bin/sh
case "$*" in
  *server_version_num*) echo 180000; exit 0 ;;
  *transaction_read_only*) echo off; exit 0 ;;
esac
cat >/dev/null
SHIM
  docker cp "${WORK_DIR}/psql-writable-stub" "${CONTAINER}:/usr/local/bin/psql" >/dev/null || exit 1
  in_container chmod +x /usr/local/bin/psql

  in_container sh -c "printf 'postgres.connection.password = master-pw-for-test\n' > /etc/xroad.properties"
  in_container rm -f "${ENV_FILE}" /etc/xroad/db.properties
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${hosts}"
  check_eq "no existing URL, host list: exit 1" 1 "${RC}"
  check "no existing URL, host list: the message asks for one write address" grep -qF "is a host list. Give one write address" "${ERR_FILE}"
  check_eq "no existing URL, host list: no OpenBao environment file is written" "" "$(in_container ls "${ENV_FILE}" 2>/dev/null)"

  in_container sh -c "printf 'spring.datasource.url = jdbc:postgresql://%s/centerui_production\n' '${hosts}' > /etc/xroad/db.properties"
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${hosts}"
  check_eq "existing URL, host list: exit 0" 0 "${RC}"
  check "existing URL, host list: the warning names the list" grep -qF "Warning: the database address '${hosts}' is a host list" "${ERR_FILE}"
  check "existing URL, host list: the first host is probed for writes" grep -qF "Database at 'db1.example.test:5432' accepts writes" "${ERR_FILE}"
  check "existing URL, host list: the OpenBao environment file is written" in_container test -s "${ENV_FILE}"
}

PSQL_ROLE_CALLS_FILE="/tmp/psql-role-calls"
ROLE_LOGIN_OK_FILE="/tmp/stub-role-login-ok"
ROLE_LOGIN_LATE_FILE="/tmp/stub-role-login-late"
PSQL_ROLE_INPUT_FILE="/tmp/psql-role-input"
# role_check_calls USER: the version and write checks the stub answered as USER.
role_check_calls() { in_container sh -c "grep -c \"^$1 .*\\(server_version_num\\|transaction_read_only\\)\" ${PSQL_ROLE_CALLS_FILE} || true"; }

scenario_central_server_check_role() {
  log_info "Scenario: the Central Server checks in the OpenBao storage step run as the role that the step uses next"
  start_central_server_container
  cat >"${WORK_DIR}/psql-role-stub" <<'SHIM'
#!/bin/sh
echo "${PGUSER:-none} $*" >>/tmp/psql-role-calls
case "$*" in
  *'\q'*)
    [ -e /tmp/stub-role-login-ok ] && [ "${PGUSER}" = openbao ] || exit 1
    if [ -e /tmp/stub-role-login-late ] && [ "$(grep -cF '\q' /tmp/psql-role-calls)" -le 1 ]; then exit 1; fi
    exit 0 ;;
  *server_version_num*) echo 180000; exit 0 ;;
  *transaction_read_only*) if [ -e "/tmp/stub-read-only-${PGUSER}" ]; then echo on; else echo off; fi; exit 0 ;;
esac
cat >>/tmp/psql-role-input
SHIM
  docker cp "${WORK_DIR}/psql-role-stub" "${CONTAINER}:/usr/local/bin/psql" >/dev/null || exit 1
  in_container chmod +x /usr/local/bin/psql
  in_container sh -c "printf 'postgres.connection.password = master-pw-for-test\n' > /etc/xroad.properties"
  in_container sh -c "touch ${PSQL_ROLE_CALLS_FILE} ${PSQL_ROLE_INPUT_FILE} && chmod 666 ${PSQL_ROLE_CALLS_FILE} ${PSQL_ROLE_INPUT_FILE}"
  local address="127.0.0.1:5432"

  in_container sh -c "rm -f ${ENV_FILE} /etc/xroad/db.properties /tmp/stub-read-only-*; : > ${PSQL_ROLE_CALLS_FILE}; touch ${ROLE_LOGIN_OK_FILE} /tmp/stub-read-only-postgres"
  write_psql_env_file openbao role-pw-for-test 127.0.0.1 5432 openbao openbao
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${address}"
  check_eq "working role, writable for the role: exit 0" 0 "${RC}"
  check "working role, writable for the role: the write check passes" grep -qF "Database at '${address}' accepts writes" "${ERR_FILE}"
  check_ne "working role: the checks run as the OpenBao role" 0 "$(role_check_calls openbao)"
  check_eq "working role: the master user runs no check" 0 "$(role_check_calls postgres)"

  in_container sh -c "rm -f /tmp/stub-read-only-*; : > ${PSQL_ROLE_CALLS_FILE}; touch /tmp/stub-read-only-openbao"
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${address}"
  check_eq "working role, read-only for the role: exit 1" 1 "${RC}"
  check "working role, read-only for the role: the message says the database does not accept writes" grep -qF "The database at '${address}' does not accept writes" "${ERR_FILE}"
  check_eq "working role, read-only for the role: the master user runs no check" 0 "$(role_check_calls postgres)"

  in_container sh -c "rm -f ${ROLE_LOGIN_OK_FILE} /tmp/stub-read-only-*; : > ${PSQL_ROLE_CALLS_FILE}; touch /tmp/stub-read-only-openbao"
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${address}"
  check_eq "role login fails, writable for the master user: exit 0" 0 "${RC}"
  check "role login fails: the write check passes" grep -qF "Database at '${address}' accepts writes" "${ERR_FILE}"
  check_ne "role login fails: the checks run as the master user" 0 "$(role_check_calls postgres)"
  check_eq "role login fails: the OpenBao role runs no check" 0 "$(role_check_calls openbao)"

  in_container sh -c "rm -f ${ROLE_LOGIN_LATE_FILE} /tmp/stub-read-only-*; : > ${PSQL_ROLE_CALLS_FILE}; : > ${PSQL_ROLE_INPUT_FILE}; touch ${ROLE_LOGIN_OK_FILE} ${ROLE_LOGIN_LATE_FILE}"
  write_psql_env_file openbao role-pw-for-test 127.0.0.1 5432 openbao openbao
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${address}"
  check_eq "first role login fails, a later one works: exit 0" 0 "${RC}"
  check_eq "first role login fails, a later one works: the role is probed again" 2 "$(in_container sh -c "grep -cF '\\q' ${PSQL_ROLE_CALLS_FILE}")"
  check "first role login fails, a later one works: the existing database is kept" grep -qF "Database and user exists, skipping database creation." "${OUT_FILE}"
  check_eq "first role login fails, a later one works: no CREATE DATABASE is sent" 0 "$(in_container sh -c "grep -c 'CREATE DATABASE' ${PSQL_ROLE_INPUT_FILE} || true")"

  in_container sh -c "rm -f ${ENV_FILE} /etc/xroad/db.properties ${ROLE_LOGIN_OK_FILE} ${ROLE_LOGIN_LATE_FILE} /tmp/stub-read-only-*; : > ${PSQL_ROLE_CALLS_FILE}; touch /tmp/stub-read-only-openbao"
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${address}"
  check_eq "fresh install, writable for the master user: exit 0" 0 "${RC}"
  check_ne "fresh install: the checks run as the master user" 0 "$(role_check_calls postgres)"
  check "fresh install: the OpenBao environment file is written" in_container test -s "${ENV_FILE}"

  in_container sh -c "rm -f ${ENV_FILE} /etc/xroad/db.properties /tmp/stub-read-only-*; touch /tmp/stub-read-only-postgres"
  run_in_container -- bash "${INIT_DB_SCRIPT}" --central-server "${address}"
  check_eq "fresh install, read-only for the master user: exit 1" 1 "${RC}"
  check "fresh install, read-only for the master user: the message says the database does not accept writes" grep -qF "The database at '${address}' does not accept writes" "${ERR_FILE}"
}

scenario_psql_values_not_executed() {
  log_info "Scenario: values from the OpenBao environment file reach a real psql only as data"
  local mode marker user password dbname generated_user generated_password env_hash key value
  local meta_password='Pw:x :'"'"'y'"'"' ${x} $(id) \n \x41 `id` & ; 100%'
  local normal_password="Pw'\"\\\$x-with-quote-doublequote-backslash-dollar"
  CONTAINER="${PSQL_CONTAINER}"
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --name "${CONTAINER}" -e "POSTGRES_PASSWORD=${PG_PASSWORD}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" "${PSQL_IMAGE}" >/dev/null || exit 1
  local i
  for i in $(seq 1 60); do
    if docker exec "${CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1; then break; fi
    sleep 1
  done
  in_container mkdir -p /etc/openbao /etc/xroad
  in_container sh -c 'getent group xroad >/dev/null || groupadd xroad; id openbao >/dev/null 2>&1 || useradd -g xroad openbao'
  cat >"${WORK_DIR}/crudini-shim" <<'SHIM'
#!/bin/sh
sed -n "s/^$4[[:space:]]*=[[:space:]]*//p" "$2" | { read -r value || exit 1; printf '%s' "${value}"; }
SHIM
  docker cp "${WORK_DIR}/crudini-shim" "${CONTAINER}:/usr/local/bin/crudini" >/dev/null || exit 1
  in_container chmod +x /usr/local/bin/crudini
  install_psql_shim

  clear_hostile_markers
  psql_admin >/dev/null <<<'\! touch /tmp/hostile-marker-psql-baseline'
  check_ne "baseline: a psql shell escape in the SQL input runs a command" "" "$(hostile_markers)"

  check "baseline: the listener starts" start_listener
  in_container bash -c "exec 3<>/dev/tcp/127.0.0.1/${LISTENER_PORT}; printf 'probe' >&3"
  sleep 0.5
  check "baseline: the listener records a connection" grep -qF "connection: probe" <(listener_received)
  in_container sh -c ": > ${LISTENER_RECEIVED_FILE}"

  for mode in local remote; do
    log_info "  ${mode}-DB mode"
    set_psql_mode "${mode}"
    psql_reset
    in_container rm -f "${ENV_FILE}"
    clear_psql_shim_logs
    run_in_container -- bash "${INIT_DB_SCRIPT}"
    check "${mode} mode, fresh init: exit 0" test "${RC}" -eq 0
    generated_user="$(env_file_value BAO_PG_USER)"
    generated_password="$(env_file_value BAO_PG_PASSWORD)"
    check_psql_password_not_in_arguments "${mode} mode, fresh init" "${generated_password}"
    check_eq "${mode} mode, fresh init: the database exists" 1 "$(psql_count database openbao)"
    check_eq "${mode} mode, fresh init: the role exists" 1 "$(psql_count role "${generated_user}")"
    check_eq "${mode} mode, fresh init: the schema belongs to the role" "${generated_user}" "$(psql_count schema_owner openbao openbao)"
    check_eq "${mode} mode, fresh init: the role logs in with the generated password" "${generated_user}" "$(psql_login "${generated_user}" "${generated_password}" openbao)"
    check_eq "${mode} mode, fresh init: a wrong password is refused" "" "$(psql_login "${generated_user}" "wrong-${generated_password}" openbao)"
    env_hash="$(in_container sha256sum "${ENV_FILE}")"
    run_in_container -- bash "${INIT_DB_SCRIPT}"
    check "${mode} mode, second run: exit 0" test "${RC}" -eq 0
    check "${mode} mode, second run: the existing database and user are kept" grep -qF "Database and user exists" "${OUT_FILE}"
    check_eq "${mode} mode, second run: the env file is kept" "${env_hash}" "$(in_container sha256sum "${ENV_FILE}")"

    psql_reset
    psql_admin >/dev/null <<<"CREATE ROLE ${generated_user} LOGIN PASSWORD 'unrelated'"
    run_in_container -- bash "${INIT_DB_SCRIPT}"
    check "${mode} mode, existing role: exit 0" test "${RC}" -eq 0
    check_eq "${mode} mode, existing role: the database is created" 1 "$(psql_count database openbao)"
    check_eq "${mode} mode, existing role: the schema is created" 1 "$(psql_count schema openbao openbao)"

    check_psql_hostile_case "${mode}" password-quotes openbao "${normal_password}" 5432 openbao openbao 0
    marker="/tmp/hostile-marker-psql-${mode}-password"
    check_psql_hostile_case "${mode}" password-escape openbao "p' \\! id -un > ${marker} #" 5432 openbao openbao 0
    check_psql_hostile_case "${mode}" password-meta openbao "${meta_password}" 5432 openbao openbao 0
    check_psql_hostile_case "${mode}" unusual-names "x.y-z_1" "Plain-pw-4c7e1" 5432 "db-1.x" "sch_1" 0
    marker="/tmp/hostile-marker-psql-${mode}-database"
    check_psql_hostile_case "${mode}" database openbao "Plain-pw-4c7e1" 5432 "openbao\" \\! id -un > ${marker} #" openbao 1
    check "${mode} mode, database: the script names the key" grep -qF "Invalid BAO_PG_DATABASE" "${ERR_FILE}"
    marker="/tmp/hostile-marker-psql-${mode}-user"
    check_psql_hostile_case "${mode}" user "x' \\! id -un > ${marker} #" "Plain-pw-4c7e1" 5432 openbao openbao 1
    check "${mode} mode, user: the script names the key" grep -qF "Invalid BAO_PG_USER" "${ERR_FILE}"
    marker="/tmp/hostile-marker-psql-${mode}-schema"
    check_psql_hostile_case "${mode}" schema openbao "Plain-pw-4c7e1" 5432 openbao "sch\" \\! id -un > ${marker} #" 1
    check "${mode} mode, schema: the script names the key" grep -qF "Invalid BAO_PG_SCHEMA" "${ERR_FILE}"
    marker="/tmp/hostile-marker-psql-${mode}-port"
    check_psql_hostile_case "${mode}" port openbao pw "5432; id -un > ${marker}" openbao openbao 1
    check "${mode} mode, port: the script names the invalid port" grep -qF "Invalid database port" "${ERR_FILE}"
    for key in BAO_PG_USER BAO_PG_DATABASE BAO_PG_SCHEMA; do
      for value in "a b" "a=b" "a'b" 'a"b' 'a\b' "a;b" 'a$(id)'; do
        check_name_refused "${mode}" "${key}" "${value}"
      done
    done
    check_name_refused "${mode}" BAO_PG_DATABASE "host=127.0.0.1 port=${LISTENER_PORT} dbname=x"
    check_eq "${mode} mode, database as connection string: the listener receives nothing" "" "$(listener_received)"
    check_name_refused "${mode}" BAO_PG_SCHEMA "openbao -c log_statement=all"
    check_name_refused "${mode}" BAO_PG_SCHEMA "openbao -c session_preload_libraries=/tmp/preload"
    check_name_refused "${mode}" BAO_PG_SCHEMA '$(touch /tmp/hostile-marker-substitution)'
  done

  log_info "  local-DB mode, non-default port"
  set_psql_mode local
  write_psql_env_file openbao "Plain-pw-4c7e1" 127.0.0.1 5433 openbao openbao
  clear_psql_shim_logs
  run_in_container -- bash "${INIT_DB_SCRIPT}"
  check "local mode, port 5433: exit non-zero, nothing listens there" test "${RC}" -ne 0
  check "local mode, port 5433: the database creation call gets the configured port" cmdlines_contain "-qtA -p 5433 -v bao_db="

  stop_listener

  log_info "  restore path"
  set_psql_mode local
  psql_reset
  clear_hostile_markers
  user="x.y-z_1"
  password="p' \\! id -un > /tmp/hostile-marker-psql-restore #"
  dbname=openbao
  write_psql_env_file "${user}" "${password}" 127.0.0.1 5432 "${dbname}" openbao
  run_in_container -- bash "${INIT_DB_SCRIPT}"
  check "restore path: the init script creates the database with a hostile password, exit 0" test "${RC}" -eq 0
  docker exec -i -e "PGUSER=${user}" -e "PGPASSWORD=${password}" "${CONTAINER}" psql -h 127.0.0.1 -d "${dbname}" -qtA >/dev/null \
    <<<"CREATE TABLE openbao.kv (v text); INSERT INTO openbao.kv VALUES ('kept')"
  run_in_container -- bash "${BACKUP_DB_SCRIPT}" /tmp/restore-dump
  check "restore path: backup exit 0" test "${RC}" -eq 0
  docker exec -e "PGUSER=${user}" -e "PGPASSWORD=${password}" "${CONTAINER}" psql -h 127.0.0.1 -d "${dbname}" -qtA -c "DELETE FROM openbao.kv" >/dev/null
  run_in_container -- bash "${RESTORE_DB_SCRIPT}" /tmp/restore-dump
  check "restore path: restore exit 0" test "${RC}" -eq 0
  check_eq "restore path: restore runs no command from the env file" "" "$(hostile_markers)"
  check_eq "restore path: restore brings the data back" kept \
    "$(docker exec -e "PGUSER=${user}" -e "PGPASSWORD=${password}" "${CONTAINER}" psql -h 127.0.0.1 -d "${dbname}" -qtA -c "SELECT v FROM openbao.kv")"
  run_in_container -- bash "${RESTORE_DB_SCRIPT}" -F /tmp/restore-dump
  check "restore path: forced restore exit 0" test "${RC}" -eq 0
  check_eq "restore path: forced restore runs no command from the env file" "" "$(hostile_markers)"
  CONTAINER="${NODE1_CONTAINER}"
}
if ! command -v jq >/dev/null 2>&1; then
  log_error "jq is required on the host"
  exit 1
fi

if ! docker image inspect "${IMAGE}" >/dev/null 2>&1; then
  docker pull "${IMAGE}" >/dev/null || { log_error "Image ${IMAGE} not available"; exit 1; }
fi
log_info "Image: ${IMAGE}"
write_shim
stage_scripts

scenario_root_token_file
scenario_bao_token_env
scenario_no_root_token
scenario_pki_setup_writes_policy
scenario_initializing_node
scenario_start_script_reads_env_as_data
scenario_cluster_member
scenario_join_guards
scenario_join_node
scenario_postinst_join
scenario_restore_keeps_node_config
scenario_postrm_purge
scenario_import_base_only
scenario_env_file_parsing
scenario_central_server_host_list
scenario_central_server_check_role
scenario_psql_values_not_executed
scenario_supervisor_listener

if [[ "${FAILURES}" -ne 0 ]]; then
  log_error "${FAILURES} check(s) failed"
  exit 1
fi
log_success "All checks passed"
