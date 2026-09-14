#!/bin/bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify.sh [options]

Builds the sidecar image, boots it with no configuration and no volumes, and
probes it until every check below passes or the timeout is reached, then
removes the container it started:

  - every supervisord program (except --exclude) reaches RUNNING and stays
    RUNNING with an unchanged pid across a sampling window (a program that
    is merely RUNNING once may be mid-restart-loop)
  - OpenBao is unsealed, the xrd-pki/xrd-secret/xrd-ds-secret mounts exist,
    and the secret-store client token file is non-empty
  - the proxy liveness endpoint (/q/health/live on the health-check port)
    reports UP
  - the admin UI answers on its port
  - docker reports the container's own HEALTHCHECK status as "healthy"

Exits 0 when every probe passes. Exits non-zero naming the probe that was
still failing when --timeout was reached.

Options:
  --version=X                  Image version to build/run (default: 8.0.0)
  --tag=NAME                   Image repository name
                                (default: xroad-security-server-sidecar)
  --timeout=SECONDS            Overall probe timeout (default: 600)
  --stability-window=SECONDS   How long a supervisord program must stay
                               RUNNING with an unchanged pid before it
                               counts as stable (default: 60)
  --stability-interval=SECONDS Sampling interval, used for every probe
                               (default: 10)
  --exclude=LIST               Comma-separated supervisord program names to
                               skip in the stability check. Default:
                               "xroad-secret-store-gate" - a one-shot init
                               program that is supposed to exit 0 and stay
                               stopped once it has unsealed OpenBao and
                               released the X-Road services.
  --keep                       Do not remove the container on exit (for
                               inspecting a failed run).
  --no-build                   Skip the docker-build.sh step; probe
                               whatever image already exists for
                               --version/--tag.
  -h, --help                   Show this help.
USAGE
}

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" >&/dev/null && pwd)"

version="8.0.0"
tag="xroad-security-server-sidecar"
timeout=600
stability_window=60
stability_interval=10
exclude="xroad-secret-store-gate"
keep=false
do_build=true

for arg in "$@"; do
  case "$arg" in
    --version=*) version="${arg#--version=}" ;;
    --tag=*) tag="${arg#--tag=}" ;;
    --timeout=*) timeout="${arg#--timeout=}" ;;
    --stability-window=*) stability_window="${arg#--stability-window=}" ;;
    --stability-interval=*) stability_interval="${arg#--stability-interval=}" ;;
    --exclude=*) exclude="${arg#--exclude=}" ;;
    --keep) keep=true ;;
    --no-build) do_build=false ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage >&2
      exit 1
      ;;
  esac
done

container_name="xroad-sidecar-verify-$$"
pid_state_file=$(mktemp)

now_iso() { date -u +%Y-%m-%dT%H:%M:%SZ; }
log() { echo "$(now_iso) INFO [verify] $*"; }
warn() { echo "$(now_iso) WARN [verify] $*" >&2; }
fail() {
  warn "FAIL: $*"
  exit 1
}

is_excluded() {
  local name="$1" item
  IFS=',' read -ra items <<<"$exclude"
  for item in "${items[@]}"; do
    [[ "$item" == "$name" ]] && return 0
  done
  return 1
}

# Bash on macOS ships as 3.2 (no associative arrays), so the per-program pid
# seen on the previous sample is tracked in a plain "name:pid" line file
# instead of a `declare -A` map.
get_last_pid() {
  [[ -f "$pid_state_file" ]] && awk -F: -v n="$1" '$1==n{print $2}' "$pid_state_file"
}
set_last_pid() {
  local name="$1" pid="$2" tmp
  tmp="${pid_state_file}.tmp"
  { [[ -f "$pid_state_file" ]] && grep -v "^${name}:" "$pid_state_file"; echo "${name}:${pid}"; } >"$tmp" || true
  mv "$tmp" "$pid_state_file"
}

cleanup() {
  rm -f "$pid_state_file" "${pid_state_file}.tmp"
  if [[ "$keep" == "true" ]]; then
    log "Leaving container $container_name running (--keep)"
    return
  fi
  docker rm -f "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if $do_build; then
  log "Building image ($tag:$version)"
  if ! "$dir/docker-build.sh" --target=full "$version" "$tag"; then
    fail "docker-build.sh --target=full failed"
  fi
fi

docker rm -f "$container_name" >/dev/null 2>&1 || true
log "Starting $tag:$version with no configuration and no volumes"
if ! docker run -d --name "$container_name" "$tag:$version" >/dev/null; then
  fail "failed to start a container from $tag:$version"
fi

# Every configured supervisord program, except --exclude, must be RUNNING
# with a pid that does not change between samples (a changing pid means it
# was restarted, i.e. a crash loop). A one-shot program that has completed
# is reported as EXITED by supervisord, not RUNNING; that is accepted here
# so genuinely one-shot programs are not forced into --exclude just to pass.
check_supervisord() {
  local status_output name state pid line previous_pid
  status_output=$(docker exec "$container_name" supervisorctl status 2>/dev/null) || true
  if [[ -z "$status_output" || "$status_output" == *"no such file"* || "$status_output" == *"refused"* ]]; then
    echo "supervisord not reachable yet"
    return 1
  fi
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    name=$(awk '{print $1}' <<<"$line")
    name="${name##*:}"
    is_excluded "$name" && continue
    state=$(awk '{print $2}' <<<"$line")
    case "$state" in
      RUNNING)
        pid=$(grep -oE 'pid [0-9]+' <<<"$line" | awk '{print $2}')
        previous_pid=$(get_last_pid "$name")
        if [[ -n "$previous_pid" && -n "$pid" && "$previous_pid" != "$pid" ]]; then
          echo "program $name restarted (pid $previous_pid -> $pid) - possible restart loop"
          return 1
        fi
        [[ -n "$pid" ]] && set_last_pid "$name" "$pid"
        ;;
      EXITED) : ;;
      *)
        echo "program $name is $state"
        return 1
        ;;
    esac
  done <<<"$status_output"
  return 0
}

check_openbao() {
  local result
  result=$(docker exec -i "$container_name" bash -s <<'EOS' 2>/dev/null
TOKEN_FILE=/etc/xroad/secret-store-client-token
ROOT_TOKEN_FILE=/etc/xroad/secret-store/root-token
if [ ! -s "$TOKEN_FILE" ]; then echo "FAIL:client token file missing or empty"; exit 0; fi
if [ ! -s "$ROOT_TOKEN_FILE" ]; then echo "FAIL:root token file missing or empty"; exit 0; fi
root_token=$(cat "$ROOT_TOKEN_FILE")
seal_json=$(curl -s -k --max-time 5 https://127.0.0.1:8200/v1/sys/seal-status 2>/dev/null)
seal=$(echo "$seal_json" | jq -r '.sealed' 2>/dev/null)
if [ "$seal" != "false" ]; then echo "FAIL:OpenBao is sealed or unreachable"; exit 0; fi
mounts_json=$(curl -s -k --max-time 5 -H "X-Vault-Token: $root_token" https://127.0.0.1:8200/v1/sys/mounts 2>/dev/null)
for m in xrd-pki xrd-secret xrd-ds-secret; do
  ok=$(echo "$mounts_json" | jq -e --arg m "$m/" 'has($m)' 2>/dev/null)
  if [ "$ok" != "true" ]; then echo "FAIL:OpenBao mount $m/ not present"; exit 0; fi
done
echo "OK"
EOS
  ) || true
  if [[ "$result" == "OK" ]]; then
    return 0
  fi
  echo "${result:-OpenBao not reachable yet}" | sed 's/^FAIL://'
  return 1
}

check_proxy_liveness() {
  local status
  status=$(docker exec "$container_name" \
    bash -c "curl -s --max-time 5 http://localhost:5588/q/health/live 2>/dev/null | jq -r '.status' 2>/dev/null") || true
  if [[ "$status" == "UP" ]]; then
    return 0
  fi
  echo "proxy liveness endpoint not UP yet (status=${status:-unreachable})"
  return 1
}

check_admin_ui() {
  local code
  code=$(docker exec "$container_name" \
    bash -c "curl -s -k -o /dev/null -w '%{http_code}' --max-time 5 https://localhost:4000/ 2>/dev/null") || true
  case "$code" in
    2??|3??) return 0 ;;
    *)
      echo "admin UI not answering on port 4000 yet (http_code=${code:-none})"
      return 1
      ;;
  esac
}

check_docker_health() {
  local status
  status=$(docker inspect --format='{{.State.Health.Status}}' "$container_name" 2>/dev/null) || true
  if [[ "$status" == "healthy" ]]; then
    return 0
  fi
  echo "container HEALTHCHECK status is ${status:-unknown}, not healthy yet"
  return 1
}

log "Probing (timeout ${timeout}s, stability window ${stability_window}s @ ${stability_interval}s interval, excluding: ${exclude})"

start_ts=$(date +%s)
stable_since=""
last_msg="probing has not run yet"

while true; do
  now=$(date +%s)
  elapsed=$((now - start_ts))
  if (( elapsed > timeout )); then
    fail "timed out after ${timeout}s; last failing probe: ${last_msg}"
  fi

  if sup_msg=$(check_supervisord); then sup_ok=true; else sup_ok=false; fi
  if $sup_ok; then
    [[ -z "$stable_since" ]] && stable_since=$now
  else
    stable_since=""
  fi
  sup_stable=false
  if $sup_ok && [[ -n "$stable_since" ]] && (( now - stable_since >= stability_window )); then
    sup_stable=true
  fi

  if ob_msg=$(check_openbao); then ob_ok=true; else ob_ok=false; fi
  if px_msg=$(check_proxy_liveness); then px_ok=true; else px_ok=false; fi
  if ui_msg=$(check_admin_ui); then ui_ok=true; else ui_ok=false; fi
  if dh_msg=$(check_docker_health); then dh_ok=true; else dh_ok=false; fi

  if ! $sup_ok; then
    last_msg="supervisord: $sup_msg"
  elif ! $sup_stable; then
    last_msg="supervisord: RUNNING, waiting out the ${stability_window}s stability window ($((now - stable_since))s so far)"
  elif ! $ob_ok; then
    last_msg="OpenBao: $ob_msg"
  elif ! $px_ok; then
    last_msg="proxy liveness: $px_msg"
  elif ! $ui_ok; then
    last_msg="admin UI: $ui_msg"
  elif ! $dh_ok; then
    last_msg="docker HEALTHCHECK: $dh_msg"
  else
    log "All probes passed after ${elapsed}s:"
    log "  supervisord: stable for $((now - stable_since))s (excluding: ${exclude})"
    log "  OpenBao: unsealed, xrd-pki/xrd-secret/xrd-ds-secret mounts present, client token non-empty"
    log "  proxy liveness: UP"
    log "  admin UI: answering on port 4000"
    log "  docker HEALTHCHECK: healthy"
    exit 0
  fi

  log "waiting (${elapsed}s elapsed): ${last_msg}"
  sleep "$stability_interval"
done
