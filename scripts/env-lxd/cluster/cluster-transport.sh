# shellcheck shell=bash disable=SC2034
# Shared helpers of the cluster scripts (start-cluster.sh, delete-cluster.sh, list-cluster-containers.sh,
# check-cluster.sh, fault-step.sh) and the backend seam of the last two. Every host command, state query and
# address lookup of check-cluster.sh and fault-step.sh goes through the primitives below, so the checks and the
# fault flows exist once and run on every backend.
#
# Backend: FAULT_STEP_BACKEND, lxd (default). Another backend is loaded from the file CLUSTER_BACKEND_FILE
# (a relative path is taken from the directory the script was started in; a missing file exits 2), which is
# sourced at the end of this file. A backend has one function per primitive, named <backend>_<primitive>:
#   host_command HOST CMD...        sets HOST_CMD to the command line that runs CMD on HOST (for run)
#   host_exec HOST CMD...           runs CMD on HOST, no input
#   host_exec_env HOST NAME=VALUE... -- CMD...
#                                   runs CMD on HOST with the variables set; the standard input of the
#                                   caller reaches CMD, so a caller without input redirects </dev/null
#   host_state HOST                 prints RUNNING, STOPPED, FROZEN, PENDING or STOPPING
#   host_address HOST               prints the address other hosts reach HOST at
#   host_ip VANTAGE HOST            prints the IP address VANTAGE resolves HOST to
# The backend file may also set NODE_NOUN, DEFAULT_TIMEOUT, DEFAULT_CHECK_WAIT and REQUIRED_TOOLS, which hold
# the lxd values below, and define <backend>_dry_run_setup ACTION HOST, which fault-step.sh calls first in a dry run.
# A fault action is a function <backend>_<action with _ for ->, plus <backend>_pause and <backend>_resume for
# node-freeze and node-thaw; see fault-step.sh.
#
# Inventory: CLUSTER_INVENTORY (default inventory.yml next to the scripts; FAULT_STEP_INVENTORY is an alias
# of it). A relative path is taken from the directory the script was started in.
#
# Inventory readers: inventory_value HOST EXPRESSION (empty when undefined), inventory_list HOST VARIABLE,
# group_hosts GROUP, load_inventory with inventory_var, host_group and role_unit_patterns.
# Database readers: patroni_members HOST..., psql_on HOST SQL [DATABASE], and
# psql_through HOST ADDRESS PORT PASSWORD SQL, which runs psql on HOST as postgres against ADDRESS:PORT with the
# password on the standard input.
# ensure_lima prepares the lima VM on macOS; it needs log_info and log_error of lib/base-script.sh.

BACKEND="${FAULT_STEP_BACKEND:-lxd}"
CLUSTER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INVENTORY="${CLUSTER_INVENTORY:-${FAULT_STEP_INVENTORY:-$CLUSTER_DIR/inventory.yml}}"
[[ "$INVENTORY" == /* ]] || INVENTORY="${INVOKED_FROM:-$PWD}/$INVENTORY"
export CLUSTER_INVENTORY="$INVENTORY"
NODE_NOUN=container
DEFAULT_TIMEOUT=180
DEFAULT_CHECK_WAIT=120
REQUIRED_TOOLS=(ansible ansible-inventory jq lxc)

PATRONI_CONFIG="/etc/patroni/config.yml"
DRY_RUN=false
INVENTORY_JSON=""
HOST_CMD=()
ENV_ASSIGNMENTS=()
COMMAND_WORDS=()

die() { echo "ERROR: $*" >&2; exit 1; }

require_tools() {
  local tool
  for tool in "${REQUIRED_TOOLS[@]}"; do
    command -v "$tool" >/dev/null || die "$tool not found"
  done
}

load_inventory() {
  require_tools
  INVENTORY_JSON=$(ansible-inventory -i "$INVENTORY" --list 2>/dev/null) || die "cannot read $INVENTORY"
}

inventory_var() {
  jq -r --arg h "$1" --arg v "$2" '._meta.hostvars[$h][$v] // empty' <<<"$INVENTORY_JSON"
}

run_ansible_json() {
  ANSIBLE_LOAD_CALLBACK_PLUGINS=1 ANSIBLE_STDOUT_CALLBACK=json ansible -i "$INVENTORY" "$@" 2>/dev/null
}

# The value of a Jinja EXPRESSION on HOST; empty when it is undefined. Fails when ansible fails.
inventory_value() {
  local json
  json=$(run_ansible_json "$1" -m debug -a "msg={{ ($2) | default('') }}") || return 1
  jq -r '.plays[0].tasks[0].hosts[].msg // empty' <<<"$json"
}

# The elements of the list variable VARIABLE on HOST, one per line. Fails when it is not a list.
inventory_list() {
  local json
  json=$(run_ansible_json "$1" -m debug -a "var=$2") || return 1
  jq -r ".plays[0].tasks[0].hosts[].$2[]" <<<"$json"
}

# The hosts of GROUP, one per line, groups of groups expanded. Fails when ansible fails.
group_hosts() {
  local hosts
  hosts=$(ansible -i "$INVENTORY" "$1" --list-hosts 2>/dev/null) || return 1
  tail -n +2 <<<"$hosts" | tr -d ' '
}

# Patroni cluster state as JSON, read through the first of the running HOSTs that answers.
patroni_members() {
  local host json
  for host in "$@"; do
    host_state_is "$host" RUNNING || continue
    json=$(host_exec "$host" patronictl -c "$PATRONI_CONFIG" list -f json 2>/dev/null) || continue
    jq -e 'type == "array"' >/dev/null 2>&1 <<<"$json" || continue
    echo "$json"
    return 0
  done
  return 1
}

# Runs the SQL on HOST as the postgres user, in DATABASE when given; the caller chooses what happens to stderr.
psql_on() {
  local database=()
  [[ -z "${3:-}" ]] || database=(-d "$3")
  host_exec "$1" runuser -u postgres -- psql ${database[@]+"${database[@]}"} -tAXc "$2"
}

psql_through() {
  # shellcheck disable=SC2016
  printf '%s\n' "$4" | host_exec_env "$1" PGCONNECT_TIMEOUT=5 -- bash -c \
    'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -h "$1" -p "$2" -U postgres -d postgres -v ON_ERROR_STOP=1 -tAXc "$3" </dev/null' \
    bash "$2" "$3" "$5"
}

ensure_lima() {
  [[ "$(uname)" == "Darwin" ]] || return 0
  local state attempt
  for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
    state=$(limactl list | awk '$1 == "xroad-lxd" {print $2}')
    case "$state" in
      Running)
        log_info "Lima instance xroad-lxd is running"
        return 0
        ;;
      Stopped)
        log_info "Starting lima instance xroad-lxd"
        limactl start xroad-lxd
        ;;
      "")
        log_error "Lima instance xroad-lxd not found. Please create it first."
        exit 1
        ;;
      *)
        log_info "Lima instance xroad-lxd has status $state; waiting for it to be ready"
        sleep 5
        ;;
    esac
  done
  log_error "Lima instance xroad-lxd did not reach the Running state"
  exit 1
}

host_group() {
  jq -r --arg h "$1" '["cs_nodes","lb","db_nodes","repo_servers"][] as $g | select(.[$g].hosts // [] | index($h)) | $g' \
    <<<"$INVENTORY_JSON" | head -n 1
}

role_unit_patterns() {
  case "$(host_group "$1")" in
    cs_nodes) printf '%s\n' "xroad-*.service" "openbao.service" ;;
    lb) printf '%s\n' "haproxy.service" "etcd.service" ;;
    db_nodes) printf '%s\n' "patroni.service" "etcd.service" ;;
    repo_servers) printf '%s\n' "nginx.service" ;;
  esac
}

# Splits NAME=VALUE... -- CMD... into ENV_ASSIGNMENTS and COMMAND_WORDS.
split_environment() {
  ENV_ASSIGNMENTS=()
  while [[ $# -gt 0 && "$1" != -- ]]; do
    ENV_ASSIGNMENTS+=("$1")
    shift
  done
  shift
  COMMAND_WORDS=("$@")
}

host_command() { "${BACKEND}_host_command" "$@"; }
host_exec() { "${BACKEND}_host_exec" "$@"; }
host_exec_env() { "${BACKEND}_host_exec_env" "$@"; }
host_state() { "${BACKEND}_host_state" "$@"; }
host_address() { "${BACKEND}_host_address" "$@"; }
host_ip() { "${BACKEND}_host_ip" "$@"; }

host_state_is() {
  [[ "$(host_state "$1")" == "$2" ]]
}

lxd_host_command() {
  HOST_CMD=(lxc exec "$1" -- "${@:2}")
}

lxd_host_exec() {
  lxc exec "$1" -- "${@:2}"
}

lxd_host_exec_env() {
  local host=$1 assignment flags=()
  shift
  split_environment "$@"
  for assignment in ${ENV_ASSIGNMENTS[@]+"${ENV_ASSIGNMENTS[@]}"}; do
    flags+=(--env "$assignment")
  done
  lxc exec ${flags[@]+"${flags[@]}"} "$host" -- "${COMMAND_WORDS[@]}"
}

lxd_host_state() {
  lxc list -f csv -c s "^$1\$" 2>/dev/null
}

lxd_host_address() {
  echo "$1.lxd"
}

lxd_host_ip() {
  lxc list -f csv -c 4 "^$2\$" | awk '{print $1}'
}

if [[ -n "${CLUSTER_BACKEND_FILE:-}" ]]; then
  BACKEND_FILE="$CLUSTER_BACKEND_FILE"
  [[ "$BACKEND_FILE" == /* ]] || BACKEND_FILE="${INVOKED_FROM:-$PWD}/$BACKEND_FILE"
  if [[ ! -f "$BACKEND_FILE" ]]; then
    echo "ERROR: CLUSTER_BACKEND_FILE $BACKEND_FILE is not a file" >&2
    exit 2
  fi
  # shellcheck source=/dev/null
  source "$BACKEND_FILE"
fi
