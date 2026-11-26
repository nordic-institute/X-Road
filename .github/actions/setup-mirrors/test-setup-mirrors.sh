#!/usr/bin/env bash
# Test for the setup-mirrors action, with dummy values only.
#
# Usage:
#   test-setup-mirrors.sh                      run setup-mirrors.sh locally in every case and check the result
#   test-setup-mirrors.sh install-stub <dir>   write a fake "docker" into <dir>; it records its calls in $DOCKER_STUB_LOG
#   test-setup-mirrors.sh assert <case>        check the current job environment after the real action ran
#                                              (<case>: no-token; ENABLED holds the action output)
#
# The local mode feeds the MIRROR_* environment variables to the script directly, because the action
# reads the repository variables itself. The fake docker fails with an error text when DOCKER_STUB_FAIL
# is set.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../../../scripts/lib/test-helpers.sh"

DUMMY_TOKEN="dummy-token-value"
DUMMY_USERNAME="dummy-user"
DUMMY_BASE="https://mirror.example.test/artifactory"
DUMMY_HOST="mirror.example.test"
STUB_ERROR="stub docker error: wrong username"

EXPECT_BASE="${DUMMY_BASE}"
EXPECT_HOST="${DUMMY_HOST}"

MIRROR_VARIABLES=(
  XROAD_MIRROR_TOKEN XROAD_MIRROR_USERNAME XROAD_MIRROR_MAVEN_URL XROAD_MIRROR_PLUGINS_URL
  XROAD_MIRROR_NPM_URL XROAD_MIRROR_UBUNTU_URL XROAD_MIRROR_DOCKER_URL XROAD_MIRROR_GITHUB_URL
  XROAD_MIRROR_K8S_URL
  GRADLE_PLUGIN_REPOSITORY_URL GRADLE_PLUGIN_REPOSITORY_USERNAME GRADLE_PLUGIN_REPOSITORY_PASSWORD
  DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_USERNAME
  DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_PASSWORD
)

expected_value() {
  case "$1" in
    *_TOKEN | *_PASSWORD) echo "${DUMMY_TOKEN}" ;;
    *_USERNAME) echo "${DUMMY_USERNAME}" ;;
    XROAD_MIRROR_MAVEN_URL) echo "${EXPECT_BASE}/mirror-mavencentral/" ;;
    XROAD_MIRROR_NPM_URL) echo "${EXPECT_BASE}/api/npm/mirror-npm/" ;;
    XROAD_MIRROR_UBUNTU_URL) echo "${EXPECT_BASE}/mirror-ubuntu-ports/" ;;
    XROAD_MIRROR_DOCKER_URL) echo "${EXPECT_HOST}/mirror-docker-hub/" ;;
    XROAD_MIRROR_GITHUB_URL) echo "${EXPECT_BASE}/mirror-github/" ;;
    XROAD_MIRROR_K8S_URL) echo "${EXPECT_BASE}/mirror-k8s-dl/" ;;
    *_PLUGINS_URL | *_PLUGIN_REPOSITORY_URL) echo "${EXPECT_BASE}/mirror-gradle-plugins/" ;;
  esac
}

FAILURES=0

install_stub() {
  mkdir -p "$1"
  cat > "$1/docker" <<'STUB'
#!/usr/bin/env bash
echo "$* stdin=$(cat)" >> "${DOCKER_STUB_LOG:?DOCKER_STUB_LOG is not set}"
if [[ -n "${DOCKER_STUB_FAIL:-}" ]]; then
  echo "stub docker error: wrong username" >&2
  exit 1
fi
STUB
  chmod +x "$1/docker"
}

variable_equals() {
  [[ "${!1+x}" == "x" && "${!1}" == "$2" ]]
}

variable_unset() {
  [[ -z "${!1+x}" ]]
}

no_docker_call() {
  [[ ! -s "${DOCKER_STUB_LOG:?}" ]]
}

docker_login_only() {
  [[ "$(cat "${DOCKER_STUB_LOG:?}")" == "login ${EXPECT_HOST} --username ${DUMMY_USERNAME} --password-stdin stdin=${DUMMY_TOKEN}" ]]
}

assert_case() {
  local case_name="$1" enabled="$2" name
  case "${case_name}" in
    no-token | off-switch | no-username | no-url | no-username-no-url | bad-url)
      for name in "${MIRROR_VARIABLES[@]}"; do
        check "${name} is not set" variable_unset "${name}"
      done
      check "docker login did not run" no_docker_call
      check "enabled output is false" test "${enabled}" = "false"
      ;;
    enabled)
      for name in "${MIRROR_VARIABLES[@]}"; do
        check "${name} has the derived value" variable_equals "${name}" "$(expected_value "${name}")"
      done
      check "docker login ran once, against the mirror host" docker_login_only
      check "enabled output is true" test "${enabled}" = "true"
      ;;
    login-failed)
      check "docker login ran once, against the mirror host" docker_login_only
      check "enabled output is false" test "${enabled}" = "false"
      ;;
    *)
      fail "unknown case ${case_name}"
      ;;
  esac
}

load_github_env() {
  local file="$1" line name delimiter value
  while IFS= read -r line; do
    if [[ "${line}" == *"<<"* ]]; then
      name="${line%%<<*}"
      delimiter="${line#*<<}"
      value=""
      while IFS= read -r line && [[ "${line}" != "${delimiter}" ]]; do
        value="${value:+${value}$'\n'}${line}"
      done
      export "${name}=${value}"
    elif [[ "${line}" == *"="* ]]; then
      export "${line%%=*}=${line#*=}"
    fi
  done < "${file}"
}

run_local_case() {
  local case_name="$1" token="$2" disabled="$3" expected_case="$4" expected_log="$5"
  shift 5
  local work_dir output expected_rc=0
  [[ "${expected_case}" == "login-failed" ]] && expected_rc=1
  work_dir="$(mktemp -d)"
  echo "case ${case_name}"
  install_stub "${work_dir}/bin"
  : > "${work_dir}/env"
  : > "${work_dir}/output"

  output="$(
    cd "${work_dir}" &&
      env -i PATH="${work_dir}/bin:${PATH}" HOME="${work_dir}" \
        GITHUB_ENV="${work_dir}/env" GITHUB_OUTPUT="${work_dir}/output" DOCKER_STUB_LOG="${work_dir}/docker.log" \
        MIRROR_TOKEN="${token}" MIRROR_USERNAME="${DUMMY_USERNAME}" MIRROR_URL="${DUMMY_BASE}" \
        MIRRORS_DISABLED="${disabled}" "$@" \
        bash "${SCRIPT_DIR}/setup-mirrors.sh" 2>&1
  )"
  local rc=$?
  check "the script exits ${expected_rc}" test "${rc}" -eq "${expected_rc}"
  check "the log is exactly the expected lines" test "${output}" = "${expected_log}"
  check "the log has no URL, user name or token" bash -c \
    '! grep -qE "example\.test|niis\.org|dummy-user|dummy-token" <<< "$1"' _ "${output}"
  echo "  log: ${output}"

  (
    FAILURES=0
    for name in "${MIRROR_VARIABLES[@]}"; do unset "${name}"; done
    export DOCKER_STUB_LOG="${work_dir}/docker.log"
    load_github_env "${work_dir}/env"
    assert_case "${expected_case}" "$(sed -n 's/^enabled=//p' "${work_dir}/output")"
    exit "${FAILURES}"
  ) || FAILURES=$((FAILURES + 1))
  rm -rf "${work_dir}"
}

case "${1:-local}" in
  install-stub)
    install_stub "${2:?usage: install-stub <dir>}"
    ;;
  assert)
    assert_case "${2:?usage: assert <case>}" "${ENABLED:-}"
    ;;
  local)
    LOG_NO_TOKEN="Artifactory mirrors disabled: no token"
    LOG_OFF_SWITCH="Artifactory mirrors disabled: off switch"
    LOG_ENABLED="Artifactory mirrors enabled"
    LOG_NO_USERNAME="::warning::Artifactory mirrors disabled: empty repository variables: XROAD_MIRROR_USERNAME"
    LOG_NO_URL="::warning::Artifactory mirrors disabled: empty repository variables: XROAD_MIRROR_BASE_URL"
    LOG_NO_USERNAME_NO_URL="::warning::Artifactory mirrors disabled: empty repository variables: XROAD_MIRROR_USERNAME, XROAD_MIRROR_BASE_URL"
    LOG_BAD_URL="::warning::Artifactory mirrors disabled: repository variable XROAD_MIRROR_BASE_URL is not an http(s) URL"
    LOG_LOGIN_FAILED="${STUB_ERROR}"$'\n'"Docker login to the mirror failed"
    run_local_case "token empty" "" "false" no-token "${LOG_NO_TOKEN}"
    run_local_case "token set, off switch true" "${DUMMY_TOKEN}" "true" off-switch "${LOG_OFF_SWITCH}"
    run_local_case "token set, off switch false" "${DUMMY_TOKEN}" "false" enabled "${LOG_ENABLED}"
    run_local_case "token set, off switch unset" "${DUMMY_TOKEN}" "" enabled "${LOG_ENABLED}"
    run_local_case "base URL with a trailing slash" "${DUMMY_TOKEN}" "false" enabled "${LOG_ENABLED}" \
      MIRROR_URL="${DUMMY_BASE}/"
    EXPECT_BASE="https://artifactory.niis.org/artifactory"
    EXPECT_HOST="artifactory.niis.org"
    run_local_case "base URL of the production mirror" "${DUMMY_TOKEN}" "false" enabled "${LOG_ENABLED}" \
      MIRROR_URL="${EXPECT_BASE}"
    EXPECT_BASE="${DUMMY_BASE}"
    EXPECT_HOST="${DUMMY_HOST}"
    run_local_case "token set, user name empty" "${DUMMY_TOKEN}" "false" no-username "${LOG_NO_USERNAME}" \
      MIRROR_USERNAME=
    run_local_case "token set, base URL empty" "${DUMMY_TOKEN}" "false" no-url "${LOG_NO_URL}" \
      MIRROR_URL=
    run_local_case "token only" "${DUMMY_TOKEN}" "false" no-username-no-url "${LOG_NO_USERNAME_NO_URL}" \
      MIRROR_USERNAME= MIRROR_URL=
    run_local_case "base URL without a scheme" "${DUMMY_TOKEN}" "false" bad-url "${LOG_BAD_URL}" \
      MIRROR_URL="${DUMMY_HOST}/artifactory"
    run_local_case "base URL with another scheme" "${DUMMY_TOKEN}" "false" bad-url "${LOG_BAD_URL}" \
      MIRROR_URL="ftp://${DUMMY_HOST}/artifactory"
    run_local_case "docker login fails" "${DUMMY_TOKEN}" "false" login-failed "${LOG_LOGIN_FAILED}" \
      DOCKER_STUB_FAIL=1
    run_local_case "off switch True" "${DUMMY_TOKEN}" "True" off-switch "${LOG_OFF_SWITCH}"
    run_local_case "off switch TRUE" "${DUMMY_TOKEN}" "TRUE" off-switch "${LOG_OFF_SWITCH}"
    run_local_case "off switch with spaces" "${DUMMY_TOKEN}" " true " off-switch "${LOG_OFF_SWITCH}"
    run_local_case "off switch mixed case with spaces" "${DUMMY_TOKEN}" "  tRuE  " off-switch "${LOG_OFF_SWITCH}"
    run_local_case "off switch yes" "${DUMMY_TOKEN}" "yes" enabled "${LOG_ENABLED}"
    run_local_case "off switch 1" "${DUMMY_TOKEN}" "1" enabled "${LOG_ENABLED}"
    run_local_case "off switch truee" "${DUMMY_TOKEN}" "truee" enabled "${LOG_ENABLED}"
    run_local_case "off switch with inner space" "${DUMMY_TOKEN}" "tr ue" enabled "${LOG_ENABLED}"
    run_local_case "off switch FALSE" "${DUMMY_TOKEN}" "FALSE" enabled "${LOG_ENABLED}"
    ;;
  *)
    echo "unknown mode $1" >&2
    exit 2
    ;;
esac

if [[ ${FAILURES} -ne 0 ]]; then
  echo "${FAILURES} check(s) failed" >&2
  exit 1
fi
echo "all checks passed"
