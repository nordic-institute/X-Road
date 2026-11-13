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
SCRIPTS_DIR="${ROOT_DIR}/deployment/native-packages/src/xroad/common/secret-store-local/usr/share/xroad/scripts"
CONTAINER="secret-store-init-test-$$"
WORK_DIR="$(mktemp -d)"

ADDR="http://127.0.0.1:8200"
ROOT_TOKEN_FILE="/etc/openbao/root-token"
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"
CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"
SETUP_SCRIPT="/usr/share/xroad/scripts/secret-store-init.sh"
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
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
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
  cat >"${WORK_DIR}/sleep-shim" <<'SHIM'
#!/bin/sh
echo "$1" >>/shim/sleeps
SHIM
}

wait_for_server() {
  for _ in $(seq 1 60); do
    if in_container curl -s -o /dev/null "${ADDR}/v1/sys/health"; then return 0; fi
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

start_real_server() {
  docker rm -f "${CONTAINER}" >/dev/null 2>&1
  docker run -d --name "${CONTAINER}" \
    -v "${SCRIPTS_DIR}:/usr/share/xroad/scripts:ro" \
    -e 'BAO_CONFIG=disable_mlock = true
storage "file" { path = "/openbao/file" }
listener "tcp" {
  address = "127.0.0.1:8200"
  tls_disable = true
}
api_addr = "http://127.0.0.1:8200"' \
    --entrypoint sh "${IMAGE}" -c '
      mkdir -p /etc/openbao /etc/xroad
      chown xroad:xroad /etc/xroad
      printf "%s\n" "$BAO_CONFIG" > /tmp/bao.hcl
      su -s /bin/sh -c "exec bao server -config=/tmp/bao.hcl" openbao &
      wait' >/dev/null || exit 1
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
  in_container curl -s -H "X-Vault-Token: ${ROOT_TOKEN}" "${ADDR}/v1/$1"
}

mounts_snapshot() {
  bao_get sys/mounts | jq -S '(.data // .) | to_entries | map({path: .key, accessor: .value.accessor})'
}

policy_text() {
  bao_get sys/policies/acl/xroad-policy | jq -r '.data.policy // empty'
}

ca_fingerprint() {
  in_container sh -c "curl -s ${ADDR}/v1/xrd-pki/ca/pem | sha256sum"
}

client_token_hash() {
  in_container sha256sum "${CLIENT_TOKEN_FILE}" 2>/dev/null | cut -d' ' -f1
}

token_accessor_count() {
  in_container curl -s -X LIST -H "X-Vault-Token: ${ROOT_TOKEN}" "${ADDR}/v1/auth/token/accessors" \
    | jq '.data.keys | length'
}

client_token_status() {
  in_container sh -c "curl -s -o /dev/null -w '%{http_code}' -H \"X-Vault-Token: \$(cat ${CLIENT_TOKEN_FILE})\" ${ADDR}/v1/auth/token/lookup-self"
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
  [[ -s "$1" && -n "$2" ]] || return 1
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

check_modes() {
  local label="$1"
  if file_exists "${ROOT_TOKEN_FILE}"; then check_eq "${label}: root token file mode" 600 "$(file_mode "${ROOT_TOKEN_FILE}")"; fi
  if file_exists "${UNSEAL_KEYS_FILE}"; then check_eq "${label}: unseal keys file mode" 600 "$(file_mode "${UNSEAL_KEYS_FILE}")"; fi
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
  in_container curl -s -X PUT -H "X-Vault-Token: ${ROOT_TOKEN}" -d "${stale_payload}" \
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

scenario_initializing_node() {
  log_info "Scenario: node initialises and unseals a real OpenBao"
  start_real_server
  run_setup
  ROOT_TOKEN="$(in_container cat "${ROOT_TOKEN_FILE}")"
  check_provisioned "initialising node"
  check "initialising node: unseal keys file written" file_exists "${UNSEAL_KEYS_FILE}"
  check_no_issued_secrets_in_output "initialising node"

  local before after
  before="$(state_snapshot)"
  run_setup
  after="$(state_snapshot)"
  check "initialised node rerun: exit 0" test "${RC}" -eq 0
  check_eq "initialised node rerun: state unchanged" "${before}" "${after}"
  check_modes "initialised node rerun"

  log_info "Scenario: sealed OpenBao after a restart"
  docker restart "${CONTAINER}" >/dev/null
  wait_for_server
  run_setup
  check_provisioned "after restart"
  check_eq "after restart: state unchanged" "${before}" "$(state_snapshot)"
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

scenario_root_token_file
scenario_bao_token_env
scenario_no_root_token
scenario_pki_setup_writes_policy
scenario_initializing_node

if [[ "${FAILURES}" -ne 0 ]]; then
  log_error "${FAILURES} check(s) failed"
  exit 1
fi
log_success "All checks passed"
