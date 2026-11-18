#!/usr/bin/env bash
# Container test for the xroad-secret-store-local setup script.
#
# Runs the real secret-store-init.sh (mounted read-only from this checkout) against a real OpenBao
# started from the dev OpenBao image, once per scenario, and checks the resulting OpenBao state.
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
SECRET_STORE_SCRIPTS_DIR="${PACKAGE_SOURCES}/secret-store-local/usr/share/xroad/scripts"
BASE_SCRIPTS_DIR="${PACKAGE_SOURCES}/base/usr/share/xroad/scripts"
NODE1_CONTAINER="secret-store-init-test-$$"
NODE2_CONTAINER="${NODE1_CONTAINER}-2"
CONTAINER="${NODE1_CONTAINER}"
PG_CONTAINER="${NODE1_CONTAINER}-pg"
NETWORK="${NODE1_CONTAINER}-net"
PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
PG_PASSWORD="test-pg-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
WORK_DIR="$(mktemp -d)"
SCRIPTS_DIR="${WORK_DIR}/scripts"
BASE_ONLY_CONTAINER="${NODE1_CONTAINER}-base-only"

ADDR="http://127.0.0.1:8200"
ROOT_TOKEN_FILE="/etc/openbao/root-token"
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"
CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"
SETUP_SCRIPT="/usr/share/xroad/scripts/secret-store-init.sh"
BUNDLE_SCRIPT="/usr/share/xroad/scripts/secret-store-join-bundle.sh"
ENV_FILE="/etc/openbao/openbao.env"
CONFIG_WRITER="/usr/share/xroad/scripts/secret-store-cluster-config.sh"
TLS_SCRIPT="/usr/share/xroad/scripts/secret-store-generate-tls-certificate.sh"
UNSEAL_SCRIPT=""
XROAD_CONFIG_FILE="/etc/openbao/xroad-openbao.hcl"
DROP_IN_FILE="/etc/systemd/system/openbao.service.d/xroad.conf"
BAO_LOG="/var/log/bao.log"
SHIM_CALLS_FILE="/var/log/shim-calls"
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
  docker rm -f "${NODE1_CONTAINER}" "${NODE2_CONTAINER}" "${BASE_ONLY_CONTAINER}" "${PG_CONTAINER}" >/dev/null 2>&1
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
  cp -p "${SECRET_STORE_SCRIPTS_DIR}"/* "${BASE_SCRIPTS_DIR}/secret-store-join-bundle.sh" "${BASE_SCRIPTS_DIR}/_restore_xroad.sh" "${SCRIPTS_DIR}/"
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
  cat >"${WORK_DIR}/sleep-shim" <<'SHIM'
#!/bin/sh
echo "$1" >>/shim/sleeps
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
# "no" leaves the OpenBao environment file to the join bundle import.
run_node_container() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --init --name "${CONTAINER}" --network "${NETWORK}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    -e "WRITE_ENV=$1" \
    -e "BAO_PG_CONNECTION_URL=postgres://postgres:${PG_PASSWORD}@${PG_CONTAINER}:5432/postgres?sslmode=disable" \
    --entrypoint sh "${IMAGE}" -c '
      rm -f '"${READY_FILE}"'
      mkdir -p /etc/openbao /opt/openbao/tls /etc/xroad /run/systemd/system /usr/local/share/ca-certificates /usr/local/bin
      chown xroad:xroad /etc/xroad
      if [ "$WRITE_ENV" = yes ]; then
        printf "BAO_PG_CONNECTION_URL=%s\n" "$BAO_PG_CONNECTION_URL" > /etc/openbao/openbao.env
        chown openbao:xroad /etc/openbao/openbao.env
        chmod 640 /etc/openbao/openbao.env
      fi
      for tool in systemctl update-ca-certificates; do
        printf "#!/bin/sh\necho \"\${0##*/} \$*\" >>'"${SHIM_CALLS_FILE}"'\nexit 0\n" > /usr/local/bin/$tool
        chmod +x /usr/local/bin/$tool
      done
      [ -f '"${XROAD_CONFIG_FILE}"' ] || bash '"${CONFIG_WRITER}"' || exit 1
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
    config=$(sed -n "s/^ExecStart=.* server -config=//p" '"${DROP_IN_FILE}"' | tail -n 1)
    exec su -s /bin/sh -c "set -a; . /etc/openbao/openbao.env; exec bao server -config=${config}" openbao >'"${BAO_LOG}"' 2>&1'
}
stop_bao() {
  in_container pkill -x bao
  for _ in $(seq 1 30); do
    if ! in_container pgrep -x bao >/dev/null; then return 0; fi
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
# run_setup [VAR=value ...]; sets RC, stdout in OUT_FILE and stderr in ERR_FILE.
run_setup() {
  local env_args=(-e "BAO_ADDR=${ADDR}")
  local arg
  for arg in "$@"; do env_args+=(-e "${arg}"); done
  docker exec "${env_args[@]}" "${CONTAINER}" bash "${SETUP_SCRIPT}" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}

# run_unseal [address]; runs the script the drop-in names as ExecStartPost, sets RC and the output files.
run_unseal() {
  docker exec -e "BAO_ADDR=${1:-${ADDR}}" "${CONTAINER}" timeout 150 bash "${UNSEAL_SCRIPT}" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
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
  docker exec "${env_args[@]}" "${CONTAINER}" sh -c \
    'mkdir -p /shim && : >/shim/lookups && : >/shim/sleeps && cp /curl-shim /shim/curl && cp /sleep-shim /shim/sleep && chmod +x /shim/curl /shim/sleep && PATH=/shim:$PATH exec bash '"${SETUP_SCRIPT}" \
    >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
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

file_exists() { in_container test -e "$1"; }

file_absent() { in_container test ! -e "$1"; }

# text_absent FILE TEXT: succeeds when FILE exists on the host and does not contain TEXT.
text_absent() {
  [[ -e "$1" && -n "$2" ]] || return 1
  ! grep -qF -- "$2" "$1"
}

policy_write_count() { grep -c "Writing policy\\.\\.\\." "${ERR_FILE}"; }

check_no_secret_in_output() {
  local label="$1" what="$2" secret="$3"
  check "${label}: stderr holds no ${what}" text_absent "${ERR_FILE}" "${secret}"
  check "${label}: stdout holds no ${what}" text_absent "${OUT_FILE}" "${secret}"
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
  run_setup "BAO_TOKEN=${ROOT_TOKEN}"
  check "root run after PKI set-up: exit 0" test "${RC}" -eq 0
  check_eq "root run after PKI set-up: policy unchanged" "${policy_after_pki}" "$(policy_text)"
}

config_state() {
  in_container sh -c "stat -c '%i %Y' ${XROAD_CONFIG_FILE} ${DROP_IN_FILE} && sha256sum ${XROAD_CONFIG_FILE} ${DROP_IN_FILE}"
}
scenario_initializing_node() {
  log_info "Scenario: node writes the X-Road configuration, initialises and unseals a real OpenBao"
  ADDR="https://127.0.0.1:8200"
  start_real_server
  local listeners
  listeners="$(in_container grep -c '^listener ' "${XROAD_CONFIG_FILE}")"
  check_eq "configuration: one listener" 1 "${listeners}"
  check "configuration: listener binds 127.0.0.1:8200 only" in_container grep -q '^  address *= "127.0.0.1:8200"$' "${XROAD_CONFIG_FILE}"
  check "configuration: PostgreSQL storage" in_container grep -q '^storage "postgresql" {' "${XROAD_CONFIG_FILE}"
  check "configuration: api_addr is the local address" in_container grep -q '^api_addr = "https://127.0.0.1:8200"$' "${XROAD_CONFIG_FILE}"
  check_eq "drop-in: one -config argument" 1 "$(in_container grep -o -- '-config=' "${DROP_IN_FILE}" | wc -l | tr -d ' ')"
  check "drop-in: ExecStart is cleared first" in_container grep -qx 'ExecStart=' "${DROP_IN_FILE}"
  check "drop-in: only the X-Road file is read" in_container grep -qE "^ExecStart=/usr/bin/bao server -config=${XROAD_CONFIG_FILE}\$" "${DROP_IN_FILE}"
  UNSEAL_SCRIPT="$(in_container sed -n 's/^ExecStartPost=[-+@!:]*//p' "${DROP_IN_FILE}")"
  check_eq "drop-in: ExecStartPost runs the unseal-only script" /usr/share/xroad/scripts/secret-store-unseal.sh "${UNSEAL_SCRIPT}"
  check "drop-in: OpenBao restarts on failure" in_container grep -qx 'Restart=on-failure' "${DROP_IN_FILE}"
  check "drop-in: restart delay is set" in_container grep -qE '^RestartSec=[0-9]+$' "${DROP_IN_FILE}"
  check "OpenBao log: no unknown or unsupported field" test "$(in_container grep -c 'unknown or unsupported field' "${BAO_LOG}")" -eq 0
  local config_before
  config_before="$(config_state)"
  in_container bash "${CONFIG_WRITER}" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
  check "writer second run: exit 0" test "${RC}" -eq 0
  check_eq "writer second run: files unchanged" "${config_before}" "$(config_state)"
  run_setup
  ROOT_TOKEN="$(in_container cat "${ROOT_TOKEN_FILE}")"
  check_provisioned "initialising node"
  check "initialising node: unseal keys file written" file_exists "${UNSEAL_KEYS_FILE}"
  check_no_issued_secrets_in_output "initialising node"
  check_eq "initialising node: high availability enabled" true "$(bao_get sys/leader | jq -r '.ha_enabled')"
  check_eq "initialising node: PostgreSQL storage" postgresql "$(bao_get sys/seal-status | jq -r '.storage_type')"
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
run_writer() {
  docker exec "${CONTAINER}" bash "${CONFIG_WRITER}" "$@" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}
run_writer_unprivileged() {
  docker exec -u openbao "${CONTAINER}" bash "${CONFIG_WRITER}" "$@" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}
cert_names() {
  in_container openssl x509 -in "${TLS_CERT_FILE}" -noout -ext subjectAltName | tail -n +2 | tr -d ' ' | tr ',' '\n' | sort | paste -sd, -
}
expected_names() { printf '%s\n' "$@" | sort | paste -sd, -; }
cert_hash() { in_container sha256sum "${TLS_CERT_FILE}" | cut -d' ' -f1; }
config_hash() { in_container sha256sum "${XROAD_CONFIG_FILE}" | cut -d' ' -f1; }
clear_shim_calls() { in_container sh -c ": > ${SHIM_CALLS_FILE}"; }
shim_calls() { in_container cat "${SHIM_CALLS_FILE}" 2>/dev/null; }
shim_call_count() { shim_calls | grep -c -- "$1" || true; }
listener_addresses() {
  in_container sed -n 's/^  address *= "\(.*\)"$/\1/p' "${XROAD_CONFIG_FILE}" | paste -sd, -
}
config_line() { in_container grep -x -- "$1" "${XROAD_CONFIG_FILE}" >/dev/null; }
config_lacks() { ! in_container grep -q -- "$1" "${XROAD_CONFIG_FILE}"; }
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
  check_eq "${label}: configuration and drop-in unchanged" "${config_before}" "$(config_state)"
  check_eq "${label}: certificate unchanged" "${cert_before}" "$(cert_hash)"
  check_eq "${label}: no trust install, daemon-reload or restart" "" "$(shim_calls)"
}
scenario_cluster_member() {
  log_info "Scenario: single server, then convert to a cluster member"
  ADDR="https://127.0.0.1:8200"
  start_real_server
  local node_ip
  node_ip="$(in_container hostname -i | awk '{print $1}')"
  check_eq "single server: certificate names only 127.0.0.1" "IPAddress:127.0.0.1" "$(cert_names)"
  check_eq "single server: one listener on 127.0.0.1" "127.0.0.1:8200" "$(listener_addresses)"
  check "single server: no cluster_addr" config_lacks "^cluster_addr"
  check "single server: standby reads left at the default" config_lacks "disable_standby_reads"
  check_eq "single server: trust installed once" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "single server: drop-in loaded once" 1 "$(shim_call_count daemon-reload)"
  check_unchanged_run "single server rerun"
  local single_cert
  single_cert="$(cert_hash)"
  clear_shim_calls
  run_writer --node-address "${node_ip}"
  check "convert: exit 0" test "${RC}" -eq 0
  check_eq "convert: listeners on 127.0.0.1 and the node address, same port" "127.0.0.1:8200,${node_ip}:8200" "$(listener_addresses)"
  check "convert: api_addr is the node address" config_line "api_addr = \"https://${node_ip}:8200\""
  check "convert: cluster_addr is the node address on the cluster port" config_line "cluster_addr = \"https://${node_ip}:8201\""
  check "convert: no standby reads key" config_lacks "disable_standby_reads"
  check_eq "convert: certificate names 127.0.0.1 and the node address" "$(expected_names "IPAddress:${node_ip}" IPAddress:127.0.0.1)" "$(cert_names)"
  check_ne "convert: certificate regenerated" "${single_cert}" "$(cert_hash)"
  check_eq "convert: trust installed again" 1 "$(shim_call_count update-ca-certificates)"
  check_eq "convert: OpenBao restarted once" 1 "$(shim_call_count 'restart openbao.service')"
  check_eq "convert: drop-in not reloaded" 0 "$(shim_call_count daemon-reload)"
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
  log_info "Scenario: a failed write is reported"
  run_writer_unprivileged
  check "unwritable configuration: exit non-zero" test "${RC}" -ne 0
  check "unwritable configuration: stderr names the configuration file" grep -qF -- "${XROAD_CONFIG_FILE}" "${ERR_FILE}"
  check_eq "unwritable configuration: configuration unchanged" "${config_before_invalid}" "$(config_hash)"
  in_container sh -c "echo '# stale' >> ${DROP_IN_FILE}"
  run_writer_unprivileged --node-address "${node_ip}" --node-host other.example.test
  check "unwritable drop-in: exit non-zero" test "${RC}" -ne 0
  check "unwritable drop-in: stderr names the drop-in" grep -qF -- "${DROP_IN_FILE}" "${ERR_FILE}"
  check "unwritable drop-in: the stale drop-in is still there" in_container grep -qx '# stale' "${DROP_IN_FILE}"
  run_writer --node-address "${node_ip}" --node-host other.example.test
  check "drop-in repaired: exit 0" test "${RC}" -eq 0
  check_unchanged_run "drop-in repaired rerun" --node-address "${node_ip}" --node-host other.example.test
  log_info "Scenario: back to a single server"
  run_writer
  check "single again: exit 0" test "${RC}" -eq 0
  check_eq "single again: one listener on 127.0.0.1" "127.0.0.1:8200" "$(listener_addresses)"
  check_eq "single again: certificate names only 127.0.0.1" "IPAddress:127.0.0.1" "$(cert_names)"
  check "single again: no cluster_addr" config_lacks "^cluster_addr"
  restart_bao
  check "single again: serves 127.0.0.1" serves_name 127.0.0.1 127.0.0.1
  check "single again: node address no longer served" not_listening "${node_ip}"
}
run_join() {
  docker exec -e "BAO_ADDR=${ADDR}" "${CONTAINER}" bash "${SETUP_SCRIPT}" --join >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}
run_bundle() {
  docker exec "${CONTAINER}" bash "${BUNDLE_SCRIPT}" "$@" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}
store_initialised() { in_container curl -sk "${ADDR}/v1/sys/init" | jq -r '.initialized'; }
file_owner() { in_container stat -c %U:%G "$1" 2>/dev/null; }
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
  UNSEAL_SCRIPT="/usr/share/xroad/scripts/secret-store-unseal.sh"
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
  UNSEAL_SCRIPT="/usr/share/xroad/scripts/secret-store-unseal.sh"
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
  run_bundle export /tmp/join-bundle.tar
  check "export: exit 0" test "${RC}" -eq 0
  check_eq "export: archive mode" 600 "$(file_mode /tmp/join-bundle.tar)"
  check_eq "export: archive owner" root:root "$(file_owner /tmp/join-bundle.tar)"
  check_eq "export: archive holds the three files and no client token" openbao.env,root-token,unseal-keys \
    "$(in_container tar -tf /tmp/join-bundle.tar | sort | paste -sd, -)"
  check_no_issued_secrets_in_output "export"
  run_bundle export /tmp/join-bundle.tar
  check "export over an existing archive: exit non-zero" test "${RC}" -ne 0
  check "export over an existing archive: stderr says it refuses" grep -qF "Refusing to overwrite" "${ERR_FILE}"
  in_container mv "${UNSEAL_KEYS_FILE}" "${UNSEAL_KEYS_FILE}.saved"
  run_bundle export /tmp/missing-file.tar
  check "export without the unseal keys file: exit non-zero" test "${RC}" -ne 0
  check "export without the unseal keys file: stderr names it" grep -qF -- "${UNSEAL_KEYS_FILE}" "${ERR_FILE}"
  check "export without the unseal keys file: no archive written" file_absent /tmp/missing-file.tar
  in_container mv "${UNSEAL_KEYS_FILE}.saved" "${UNSEAL_KEYS_FILE}"
  in_container sh -c 'mkdir /tmp/bad && cp /etc/openbao/openbao.env /etc/openbao/unseal-keys /etc/openbao/root-token /tmp/bad/ && printf x > /tmp/bad/extra && cd /tmp/bad && tar -cf /tmp/bad-bundle.tar openbao.env unseal-keys root-token extra'

  log_info "Scenario: import on the second node"
  CONTAINER="${NODE2_CONTAINER}"
  run_node_container no
  wait_for_ready_file
  ip2="$(in_container hostname -i | awk '{print $1}')"
  run_writer --node-address "${ip2}"
  check "second node: convert exit 0" test "${RC}" -eq 0
  CONTAINER="${NODE1_CONTAINER}"
  copy_between_nodes /tmp/join-bundle.tar
  copy_between_nodes /tmp/bad-bundle.tar
  CONTAINER="${NODE2_CONTAINER}"
  run_bundle import /tmp/bad-bundle.tar
  check "import with an unknown entry: exit non-zero" test "${RC}" -ne 0
  check "import with an unknown entry: nothing imported" file_absent "${ENV_FILE}"
  in_container sh -c "printf 'keepme' > ${ROOT_TOKEN_FILE}"
  run_bundle import /tmp/join-bundle.tar
  check "import over an existing file: exit non-zero" test "${RC}" -ne 0
  check "import over an existing file: stderr names it" grep -qF -- "${ROOT_TOKEN_FILE}" "${ERR_FILE}"
  check_eq "import over an existing file: the file is unchanged" keepme "$(in_container cat "${ROOT_TOKEN_FILE}")"
  check "import over an existing file: the environment file is not written" file_absent "${ENV_FILE}"
  check "import over an existing file: the unseal keys file is not written" file_absent "${UNSEAL_KEYS_FILE}"
  in_container rm -f "${ROOT_TOKEN_FILE}"
  docker cp "${WORK_DIR}/install-shim" "${NODE2_CONTAINER}:/usr/local/bin/install" >/dev/null
  in_container chmod +x /usr/local/bin/install
  run_bundle import /tmp/join-bundle.tar
  check "import with a failing install step: exit non-zero" test "${RC}" -ne 0
  check "import with a failing install step: the environment file is rolled back" file_absent "${ENV_FILE}"
  check "import with a failing install step: the unseal keys file is rolled back" file_absent "${UNSEAL_KEYS_FILE}"
  check "import with a failing install step: the root token file is not written" file_absent "${ROOT_TOKEN_FILE}"
  in_container rm -f /usr/local/bin/install
  run_bundle import /tmp/join-bundle.tar
  check "import: exit 0" test "${RC}" -eq 0
  check_eq "import: the three files equal the first node's" "${first_hashes}" "$(bundle_hashes)"
  for file in "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}"; do
    check_eq "import: ${file} mode" 600 "$(file_mode "${file}")"
    check_eq "import: ${file} owner" root:root "$(file_owner "${file}")"
  done
  check_no_secret_in_output "import" "root token" "${ROOT_TOKEN}"
  run_bundle import /tmp/join-bundle.tar
  check "second import: exit non-zero" test "${RC}" -ne 0
  check_eq "second import: files unchanged" "${first_hashes}" "$(bundle_hashes)"

  log_info "Scenario: join on the second node"
  in_container sh -c "chown -R openbao:openbao /etc/openbao && chgrp -R xroad /etc/openbao && chmod 640 ${ENV_FILE}"
  check_eq "package steps: root token file owner is the OpenBao user" openbao:xroad "$(file_owner "${ROOT_TOKEN_FILE}")"
  check_eq "package steps: unseal keys file owner is the OpenBao user" openbao:xroad "$(file_owner "${UNSEAL_KEYS_FILE}")"
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
run_postinst() {
  docker exec -e "BAO_ADDR=${ADDR}" "${CONTAINER}" bash /tmp/postinst configure >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}
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
  run_postinst
  check "join with a node address: exit 0" test "${RC}" -eq 0
  check "join with a node address: the node joins the cluster" grep -qF "Joining the OpenBao cluster" "${OUT_FILE}"
  check_eq "join with a node address: configuration unchanged" "${config_before}" "$(config_hash)"
}
scenario_restore_keeps_node_config() {
  log_info "Scenario: restore keeps the node's OpenBao configuration and unseals OpenBao"
  CONTAINER="${NODE2_CONTAINER}"
  ADDR="https://127.0.0.1:8200"
  local config_before env_before
  in_container sh -c 'mkdir -p /etc/xroad/backup.d /var/lib/xroad /tmp/node-a/etc/openbao /tmp/node-a/etc/xroad /tmp/node-a/var/lib/xroad &&
    : > /etc/xroad/backup.d/10_openbao &&
    printf "# node A configuration\n" > /tmp/node-a/etc/openbao/xroad-openbao.hcl &&
    printf "BAO_PG_CONNECTION_URL=archive\n" > /tmp/node-a/etc/openbao/openbao.env &&
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
  check_eq "restore: the configuration owner" openbao:xroad "$(file_owner "${XROAD_CONFIG_FILE}")"
  check_eq "restore: the configuration mode" 640 "$(file_mode "${XROAD_CONFIG_FILE}")"
  check_eq "restore: the archive's configuration is kept beside it" "# node A configuration" "$(in_container cat "${XROAD_CONFIG_FILE}.restored")"
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
run_restore_driver() {
  docker exec "${CONTAINER}" bash /tmp/restore-driver "$@" >"${OUT_FILE}" 2>"${ERR_FILE}"
  RC=$?
}
scenario_import_base_only() {
  log_info "Scenario: import on a host that has only the xroad-base scripts"
  local file first_hashes
  CONTAINER="${NODE1_CONTAINER}"
  first_hashes="$(bundle_hashes)"
  docker cp "${NODE1_CONTAINER}:/tmp/join-bundle.tar" "${WORK_DIR}/transfer" >/dev/null || exit 1
  docker rm -f "${BASE_ONLY_CONTAINER}" >/dev/null 2>&1
  docker run -d --init --name "${BASE_ONLY_CONTAINER}" \
    -v "${BASE_SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    --entrypoint sh "${IMAGE}" -c 'exec tail -f /dev/null' >/dev/null || exit 1
  docker cp "${WORK_DIR}/transfer" "${BASE_ONLY_CONTAINER}:/tmp/join-bundle.tar" >/dev/null || exit 1
  CONTAINER="${BASE_ONLY_CONTAINER}"
  check_eq "base only: no other secret-store script is present" secret-store-join-bundle.sh \
    "$(in_container ls /usr/share/xroad/scripts | grep -E 'secret-store|openbao' | paste -sd, -)"
  check "base only: no OpenBao directory yet" file_absent /etc/openbao
  run_bundle import /tmp/join-bundle.tar
  check "base only: import exit 0" test "${RC}" -eq 0
  check_eq "base only: the three files equal the first node's" "${first_hashes}" "$(bundle_hashes)"
  for file in "${ENV_FILE}" "${UNSEAL_KEYS_FILE}" "${ROOT_TOKEN_FILE}"; do
    check_eq "base only: ${file} mode" 600 "$(file_mode "${file}")"
    check_eq "base only: ${file} owner" root:root "$(file_owner "${file}")"
  done
  run_bundle check
  check "base only: check exit 0" test "${RC}" -eq 0
  run_bundle import /tmp/join-bundle.tar
  check "base only: a second import refuses" test "${RC}" -ne 0
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
scenario_cluster_member
scenario_join_guards
scenario_join_node
scenario_postinst_join
scenario_restore_keeps_node_config
scenario_import_base_only

if [[ "${FAILURES}" -ne 0 ]]; then
  log_error "${FAILURES} check(s) failed"
  exit 1
fi
log_success "All checks passed"
