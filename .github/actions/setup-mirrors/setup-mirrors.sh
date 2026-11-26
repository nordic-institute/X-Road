#!/usr/bin/env bash
# Turns on the Artifactory mirrors for the rest of a GitHub Actions job.
#
# Reads MIRROR_TOKEN, MIRROR_URL (the Artifactory base URL, for example https://host/artifactory),
# MIRROR_USERNAME and MIRRORS_DISABLED from the environment (the action maps the repository secret and
# variables to them). The mirrors are enabled only when the token, the user name and the base URL are
# set, the base URL is an http(s) URL and the off switch is not set. The off switch is "true" in any
# letter case, ignoring surrounding spaces.
# When enabled, derives the per-tool mirror URLs from the base URL and the repository keys of the mirror,
# appends the XROAD_MIRROR_* and Gradle plugin repository variables to $GITHUB_ENV and logs in to the
# Docker mirror host. A failed docker login prints Docker's own error output before the failure message.
# When the token is set but the user name or the base URL is empty or invalid, logs a warning that
# names the repository variable (never its value).
# Always appends "enabled=true|false" to $GITHUB_OUTPUT.
# Never prints the token.

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

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  printf '%s' "${value%"${value##*[![:space:]]}"}"
}

if [[ -z "${MIRROR_TOKEN:-}" ]]; then
  echo "Artifactory mirrors disabled: no token"
  write_output false
  exit 0
fi

off_switch="$(trim "${MIRRORS_DISABLED:-}" | tr '[:upper:]' '[:lower:]')"

if [[ "${off_switch}" == "true" ]]; then
  echo "Artifactory mirrors disabled: off switch"
  write_output false
  exit 0
fi

empty_variables=()
[[ -n "${MIRROR_USERNAME:-}" ]] || empty_variables+=(XROAD_MIRROR_USERNAME)
[[ -n "${MIRROR_URL:-}" ]] || empty_variables+=(XROAD_MIRROR_BASE_URL)
if [[ ${#empty_variables[@]} -gt 0 ]]; then
  empty_list="$(printf ', %s' "${empty_variables[@]}")"
  echo "::warning::Artifactory mirrors disabled: empty repository variables: ${empty_list#, }"
  write_output false
  exit 0
fi

base_url="$(trim "${MIRROR_URL}")"
base_url="${base_url%"${base_url##*[!/]}"}"
if [[ ! "${base_url}" =~ ^https?://[^/[:space:]]+ ]]; then
  echo "::warning::Artifactory mirrors disabled: repository variable XROAD_MIRROR_BASE_URL is not an http(s) URL"
  write_output false
  exit 0
fi

mirror_host="${base_url#*://}"
mirror_host="${mirror_host%%/*}"
plugins_url="${base_url}/mirror-gradle-plugins/"

write_env XROAD_MIRROR_TOKEN "${MIRROR_TOKEN}"
write_env XROAD_MIRROR_USERNAME "${MIRROR_USERNAME}"
write_env XROAD_MIRROR_MAVEN_URL "${base_url}/mirror-mavencentral/"
write_env XROAD_MIRROR_PLUGINS_URL "${plugins_url}"
write_env XROAD_MIRROR_NPM_URL "${base_url}/api/npm/mirror-npm/"
write_env XROAD_MIRROR_UBUNTU_URL "${base_url}/mirror-ubuntu-ports/"
write_env XROAD_MIRROR_DOCKER_URL "${mirror_host}/mirror-docker-hub/"
write_env XROAD_MIRROR_GITHUB_URL "${base_url}/mirror-github/"
write_env XROAD_MIRROR_K8S_URL "${base_url}/mirror-k8s-dl/"

for prefix in GRADLE_PLUGIN_REPOSITORY DEVELOCITY_INJECTION_PLUGIN_REPOSITORY; do
  write_env "${prefix}_URL" "${plugins_url}"
  write_env "${prefix}_USERNAME" "${MIRROR_USERNAME}"
  write_env "${prefix}_PASSWORD" "${MIRROR_TOKEN}"
done

if ! login_output="$(printf '%s' "${MIRROR_TOKEN}" | docker login "${mirror_host}" --username "${MIRROR_USERNAME}" --password-stdin 2>&1)"; then
  echo "${login_output}" >&2
  echo "Docker login to the mirror failed" >&2
  write_output false
  exit 1
fi

echo "Artifactory mirrors enabled"
write_output true
