#!/usr/bin/env bash
# Turns on the Artifactory mirrors for the rest of a GitHub Actions job.
#
# Reads the mirror settings from MIRROR_* environment variables (the action maps its inputs to them).
# The mirrors are enabled only when the token and the user name are set and the off switch is not.
# When enabled, appends the XROAD_MIRROR_* and Gradle plugin repository variables to $GITHUB_ENV
# and logs in to the Docker mirror host. The plugin repository variables are written only when the
# plugins mirror URL is set. The off switch is "true" in any letter case, ignoring surrounding spaces.
# Logs a warning that names the empty inputs (never their values) when the token is set but the user
# name is empty, or when the mirrors are enabled with some URL empty.
# Always appends "enabled=true|false" to $GITHUB_OUTPUT.
# Prints no URL and no username.

set -euo pipefail

: "${GITHUB_ENV:?GITHUB_ENV is not set}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is not set}"

write_output() {
  echo "enabled=$1" >> "${GITHUB_OUTPUT}"
}

write_env() {
  local name="$1" value="$2" delimiter
  [[ -n "${value}" ]] || return 0
  delimiter="ghadelimiter_$(od -An -tx1 -N16 /dev/urandom | tr -d ' \n')"
  printf '%s<<%s\n%s\n%s\n' "${name}" "${delimiter}" "${value}" "${delimiter}" >> "${GITHUB_ENV}"
}

if [[ -z "${MIRROR_TOKEN:-}" ]]; then
  echo "Artifactory mirrors disabled: no token"
  write_output false
  exit 0
fi

off_switch="${MIRRORS_DISABLED:-}"
off_switch="${off_switch#"${off_switch%%[![:space:]]*}"}"
off_switch="${off_switch%"${off_switch##*[![:space:]]}"}"
off_switch="$(printf '%s' "${off_switch}" | tr '[:upper:]' '[:lower:]')"

if [[ "${off_switch}" == "true" ]]; then
  echo "Artifactory mirrors disabled: off switch"
  write_output false
  exit 0
fi

empty_inputs=()
for input in username:MIRROR_USERNAME maven-url:MIRROR_MAVEN_URL plugins-url:MIRROR_PLUGINS_URL \
  npm-url:MIRROR_NPM_URL ubuntu-url:MIRROR_UBUNTU_URL docker-url:MIRROR_DOCKER_URL \
  github-url:MIRROR_GITHUB_URL k8s-url:MIRROR_K8S_URL; do
  variable="${input#*:}"
  [[ -n "${!variable:-}" ]] || empty_inputs+=("${input%%:*}")
done
empty_list=""
if [[ ${#empty_inputs[@]} -gt 0 ]]; then
  empty_list="$(printf ', %s' "${empty_inputs[@]}")"
  empty_list="${empty_list#, }"
fi

if [[ -z "${MIRROR_USERNAME:-}" ]]; then
  echo "::warning::Artifactory mirrors disabled: empty inputs: ${empty_list}"
  write_output false
  exit 0
fi

if [[ -n "${empty_list}" ]]; then
  echo "::warning::Artifactory mirror inputs empty: ${empty_list}"
fi

write_env XROAD_MIRROR_TOKEN "${MIRROR_TOKEN}"
write_env XROAD_MIRROR_USERNAME "${MIRROR_USERNAME:-}"
write_env XROAD_MIRROR_MAVEN_URL "${MIRROR_MAVEN_URL:-}"
write_env XROAD_MIRROR_PLUGINS_URL "${MIRROR_PLUGINS_URL:-}"
write_env XROAD_MIRROR_NPM_URL "${MIRROR_NPM_URL:-}"
write_env XROAD_MIRROR_UBUNTU_URL "${MIRROR_UBUNTU_URL:-}"
write_env XROAD_MIRROR_DOCKER_URL "${MIRROR_DOCKER_URL:-}"
write_env XROAD_MIRROR_GITHUB_URL "${MIRROR_GITHUB_URL:-}"
write_env XROAD_MIRROR_K8S_URL "${MIRROR_K8S_URL:-}"

if [[ -n "${MIRROR_PLUGINS_URL:-}" ]]; then
  for prefix in GRADLE_PLUGIN_REPOSITORY DEVELOCITY_INJECTION_PLUGIN_REPOSITORY; do
    write_env "${prefix}_URL" "${MIRROR_PLUGINS_URL}"
    write_env "${prefix}_USERNAME" "${MIRROR_USERNAME:-}"
    write_env "${prefix}_PASSWORD" "${MIRROR_TOKEN}"
  done
fi

docker_host="${MIRROR_DOCKER_URL:-}"
docker_host="${docker_host#*://}"
docker_host="${docker_host%%/*}"

if [[ -n "${docker_host}" && -n "${MIRROR_USERNAME:-}" ]]; then
  if ! printf '%s' "${MIRROR_TOKEN}" | docker login "${docker_host}" --username "${MIRROR_USERNAME}" --password-stdin >/dev/null 2>&1; then
    echo "Docker login to the mirror failed" >&2
    write_output false
    exit 1
  fi
fi

echo "Artifactory mirrors enabled"
write_output true
