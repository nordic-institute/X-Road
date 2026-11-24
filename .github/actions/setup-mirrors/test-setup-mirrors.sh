#!/usr/bin/env bash
# Test for the setup-mirrors action, with dummy values only.
#
# Usage:
#   test-setup-mirrors.sh                      run setup-mirrors.sh locally in every case and check the result
#   test-setup-mirrors.sh install-stub <dir>   write a fake "docker" into <dir>; it records its calls in $DOCKER_STUB_LOG
#   test-setup-mirrors.sh assert <case>        check the current job environment after the real action ran
#                                              (<case>: no-token, off-switch, enabled or no-plugins-url;
#                                              ENABLED holds the action output)
#
# The local mode also covers a run without a user name and a failing docker login (the stub fails
# when DOCKER_STUB_FAIL is set).

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../../../scripts/lib/test-helpers.sh"

DUMMY_TOKEN="dummy-token-value"
DUMMY_USERNAME="dummy-user"
DUMMY_BASE="https://mirror.example.test/artifactory"
DUMMY_DOCKER_HOST="registry.example.test"

MIRROR_VARIABLES=(
  XROAD_MIRROR_TOKEN XROAD_MIRROR_USERNAME XROAD_MIRROR_MAVEN_URL XROAD_MIRROR_PLUGINS_URL
  XROAD_MIRROR_NPM_URL XROAD_MIRROR_UBUNTU_URL XROAD_MIRROR_DOCKER_URL XROAD_MIRROR_GITHUB_URL
  XROAD_MIRROR_K8S_URL
  GRADLE_PLUGIN_REPOSITORY_URL GRADLE_PLUGIN_REPOSITORY_USERNAME GRADLE_PLUGIN_REPOSITORY_PASSWORD
  DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_USERNAME
  DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_PASSWORD
)

PLUGIN_VARIABLES=(
  XROAD_MIRROR_PLUGINS_URL
  GRADLE_PLUGIN_REPOSITORY_URL GRADLE_PLUGIN_REPOSITORY_USERNAME GRADLE_PLUGIN_REPOSITORY_PASSWORD
  DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_URL DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_USERNAME
  DEVELOCITY_INJECTION_PLUGIN_REPOSITORY_PASSWORD
)

MAVEN_URL="${DUMMY_BASE}/mirror-mavencentral/"
PLUGINS_URL="${DUMMY_BASE}/mirror-gradle-plugins/"
NPM_URL="${DUMMY_BASE}/api/npm/mirror-npm/"
UBUNTU_URL="${DUMMY_BASE}/mirror-ubuntu-ports/"
DOCKER_URL="${DUMMY_DOCKER_HOST}/mirror-docker-hub/"
GITHUB_URL="${DUMMY_BASE}/mirror-github/"
K8S_URL="${DUMMY_BASE}/mirror-k8s-dl/"

expected_value() {
  case "$1" in
    *_TOKEN | *_PASSWORD) echo "${DUMMY_TOKEN}" ;;
    *_USERNAME) echo "${DUMMY_USERNAME}" ;;
    XROAD_MIRROR_MAVEN_URL) echo "${MAVEN_URL}" ;;
    XROAD_MIRROR_NPM_URL) echo "${NPM_URL}" ;;
    XROAD_MIRROR_UBUNTU_URL) echo "${UBUNTU_URL}" ;;
    XROAD_MIRROR_DOCKER_URL) echo "${DOCKER_URL}" ;;
    XROAD_MIRROR_GITHUB_URL) echo "${GITHUB_URL}" ;;
    XROAD_MIRROR_K8S_URL) echo "${K8S_URL}" ;;
    *_PLUGINS_URL | *_PLUGIN_REPOSITORY_URL) echo "${PLUGINS_URL}" ;;
  esac
}

FAILURES=0

install_stub() {
  mkdir -p "$1"
  cat > "$1/docker" <<'STUB'
#!/usr/bin/env bash
echo "$* stdin=$(cat)" >> "${DOCKER_STUB_LOG:?DOCKER_STUB_LOG is not set}"
[[ -z "${DOCKER_STUB_FAIL:-}" ]]
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
  [[ "$(cat "${DOCKER_STUB_LOG:?}")" == "login ${DUMMY_DOCKER_HOST} --username ${DUMMY_USERNAME} --password-stdin stdin=${DUMMY_TOKEN}" ]]
}

is_plugin_variable() {
  local candidate
  for candidate in "${PLUGIN_VARIABLES[@]}"; do
    [[ "${candidate}" == "$1" ]] && return 0
  done
  return 1
}

assert_case() {
  local case_name="$1" enabled="$2" name
  case "${case_name}" in
    no-token | off-switch | no-username)
      for name in "${MIRROR_VARIABLES[@]}"; do
        check "${name} is not set" variable_unset "${name}"
      done
      check "docker login did not run" no_docker_call
      check "enabled output is false" test "${enabled}" = "false"
      ;;
    enabled)
      for name in "${MIRROR_VARIABLES[@]}"; do
        check "${name} has the input value" variable_equals "${name}" "$(expected_value "${name}")"
      done
      check "docker login ran once, against the mirror host" docker_login_only
      check "enabled output is true" test "${enabled}" = "true"
      ;;
    login-failed)
      check "docker login ran once, against the mirror host" docker_login_only
      check "enabled output is false" test "${enabled}" = "false"
      ;;
    no-plugins-url)
      for name in "${PLUGIN_VARIABLES[@]}"; do
        check "${name} is not set" variable_unset "${name}"
      done
      for name in "${MIRROR_VARIABLES[@]}"; do
        is_plugin_variable "${name}" ||
          check "${name} has the input value" variable_equals "${name}" "$(expected_value "${name}")"
      done
      check "docker login ran once, against the mirror host" docker_login_only
      check "enabled output is true" test "${enabled}" = "true"
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
        MIRROR_TOKEN="${token}" MIRROR_USERNAME="${DUMMY_USERNAME}" \
        MIRROR_MAVEN_URL="${MAVEN_URL}" MIRROR_PLUGINS_URL="${PLUGINS_URL}" \
        MIRROR_NPM_URL="${NPM_URL}" MIRROR_UBUNTU_URL="${UBUNTU_URL}" \
        MIRROR_DOCKER_URL="${DOCKER_URL}" MIRROR_GITHUB_URL="${GITHUB_URL}" \
        MIRROR_K8S_URL="${K8S_URL}" MIRRORS_DISABLED="${disabled}" "$@" \
        bash "${SCRIPT_DIR}/setup-mirrors.sh" 2>&1
  )"
  local rc=$?
  check "the script exits ${expected_rc}" test "${rc}" -eq "${expected_rc}"
  check "the log is exactly the expected lines" test "${output}" = "${expected_log}"
  check "the log has no URL, user name or token" bash -c \
    '! grep -qE "example\.test|dummy-user|dummy-token" <<< "$1"' _ "${output}"
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
    LOG_NO_PLUGINS_URL="::warning::Artifactory mirror inputs empty: plugins-url"$'\n'"${LOG_ENABLED}"
    LOG_NO_USERNAME="::warning::Artifactory mirrors disabled: empty inputs: username"
    LOG_TOKEN_ONLY="::warning::Artifactory mirrors disabled: empty inputs: username, maven-url, plugins-url, npm-url, ubuntu-url, docker-url, github-url, k8s-url"
    LOG_LOGIN_FAILED="Docker login to the mirror failed"
    run_local_case "token empty" "" "false" no-token "${LOG_NO_TOKEN}"
    run_local_case "token set, off switch true" "${DUMMY_TOKEN}" "true" off-switch "${LOG_OFF_SWITCH}"
    run_local_case "token set, off switch false" "${DUMMY_TOKEN}" "false" enabled "${LOG_ENABLED}"
    run_local_case "token set, off switch unset" "${DUMMY_TOKEN}" "" enabled "${LOG_ENABLED}"
    run_local_case "token and user name set, plugins URL empty" "${DUMMY_TOKEN}" "false" no-plugins-url \
      "${LOG_NO_PLUGINS_URL}" MIRROR_PLUGINS_URL=
    run_local_case "token set, user name empty" "${DUMMY_TOKEN}" "false" no-username "${LOG_NO_USERNAME}" \
      MIRROR_USERNAME=
    run_local_case "token only" "${DUMMY_TOKEN}" "false" no-username "${LOG_TOKEN_ONLY}" \
      MIRROR_USERNAME= MIRROR_MAVEN_URL= MIRROR_PLUGINS_URL= MIRROR_NPM_URL= MIRROR_UBUNTU_URL= \
      MIRROR_DOCKER_URL= MIRROR_GITHUB_URL= MIRROR_K8S_URL=
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
