#!/bin/bash
# Fault steps for the Central Server cluster on the lxd backend: stop, start, freeze and thaw a node,
# stop and start one service on a node, hand the database leadership to the replica (planned
# switchover or forced failover). The one sanctioned way to change a running cluster.
#
# Usage: fault-step.sh [--dry-run] <action> <host> [<service>]
#
# Exit codes: 0 state reached (or already there, except db-*), 1 action failed or state not reached in time,
# 2 unknown action, host or service, or wrong arguments (nothing was changed).
#
# Each action has one function per backend, named <backend>_<action with _ for ->. A backend
# is selected with FAULT_STEP_BACKEND (lxd, the default); another backend comes from CLUSTER_BACKEND_FILE and
# defines the same functions, plus <backend>_pause and <backend>_resume for node-freeze and node-thaw.
# Common code holds no backend commands; the transport primitives and the inventory path are in
# cluster-transport.sh. Flows that differ between backends only in how a command reaches the host
# (service_stop, service_start, node_freeze, node_thaw, db_switchover, db_promote) are written once here;
# the <backend>_ functions call them. An action a backend has no function for exits 2.
#
# A node is started when its container runs, systemd has finished booting and the units of its role
# (role_unit_patterns in cluster-transport.sh) are active; a Central Server node also needs
# xroad-center enabled and an answer to an HTTPS request on the first lb_tcp_ports port other than 80. A node
# with xroad_cs_openbao_db_stop_expected counts as started once it has booted.
# node-freeze checks that a request to the first lb_tcp_ports port other than 80 of a node that answered it
# before times out (curl exit 28). service-start also starts the units bound to the service; they stop with it.
# Repeating a node or service action that reached its state is a no-op; db-* on the current leader
# exits 1 and changes nothing. A request that cannot apply (node-start on a frozen
# node, node-freeze on a stopped node, a service action on a node that is not running) fails.
#
# --dry-run prints the commands of a real run as lines that start with "+ " and changes nothing; it reads
# state to decide what to do, so a repeated action prints no command.
#
# Adding an action:
#  1. Append its name to ACTIONS and, when it takes a service name, to the service-* pattern in main.
#  2. Write <backend>_<action>, or one shared flow that it calls. Run every changing command through run, so
#     --dry-run prints and skips it, and end with wait_for on the state.
#  3. Add it to usage and to the table in README.md.
# A new host group (for example an external OpenBao host) belongs under cluster_containers in the inventory
# and in host_group and role_unit_patterns of cluster-transport.sh, which decide when a node has started.
set -uo pipefail
INVOKED_FROM=$PWD
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1
# shellcheck source=cluster-transport.sh
source ./cluster-transport.sh

TIMEOUT="${FAULT_STEP_TIMEOUT:-$DEFAULT_TIMEOUT}"
REQUEST_TIMEOUT=5
CS_MAIN_UNIT="xroad-center.service"
ACTIONS="node-stop node-start service-stop service-start node-freeze node-thaw db-switchover db-promote"
STATE=""
WAIT_REASON=""

usage() {
  cat <<EOF
Usage: $0 [--dry-run] <action> <host> [<service>]

Actions:
  node-stop <host>               stop the node
  node-start <host>              start the node and wait for its services
  service-stop <host> <service>  stop one service on a running node
  service-start <host> <service> start one service on a running node
  node-freeze <host>             pause the node without stopping it
  node-thaw <host>               resume a frozen node
  db-switchover <replica-host>   planned handover of the database leadership to the streaming replica
  db-promote <replica-host>      forced failover of the database leadership to the replica

--dry-run prints the commands a real run performs and changes nothing.
Hosts are the ${NODE_NOUN}s in $INVENTORY. FAULT_STEP_TIMEOUT (seconds, default $DEFAULT_TIMEOUT) bounds each wait.
EOF
}

info() { echo "$*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }
usage_error() { echo "ERROR: $*" >&2; echo "Run '$0 --help' for usage." >&2; exit 2; }

run() {
  printf '+'
  printf ' %q' "$@"
  printf '\n'
  $DRY_RUN && return 0
  "$@"
}

wait_for() {
  local deadline=$((SECONDS + TIMEOUT))
  until "$@"; do
    ((SECONDS >= deadline)) && return 1
    sleep 2
  done
}

group_first_host() {
  jq -r --arg g "$1" '.[$g].hosts[0] // empty' <<<"$INVENTORY_JSON"
}

cluster_hosts() {
  jq -r '.cluster_containers.children[] as $g | .[$g].hosts[]?' <<<"$INVENTORY_JSON"
}

node_requires_services() {
  [[ "$(jq -r --arg h "$1" '._meta.hostvars[$h].xroad_cs_openbao_db_stop_expected // false' <<<"$INVENTORY_JSON")" != "true" ]]
}

probe_port() {
  [[ "$(host_group "$1")" == "cs_nodes" ]] || return 0
  jq -r --arg h "$1" '._meta.hostvars[$h].lb_tcp_ports // [] | map(select(. != 80)) | first // empty' <<<"$INVENTORY_JSON"
}

service_unit() {
  echo "${1%.service}.service"
}

read_state() {
  STATE=$(host_state "$1")
  [[ -n "$STATE" ]] || fail "$NODE_NOUN $1 does not exist"
}

run_on() {
  host_command "$@"
  run "${HOST_CMD[@]}"
}

unit_state() {
  host_exec "$1" systemctl is-active "$2" 2>/dev/null
  return 0
}

unit_state_is_one_of() {
  local host=$1 unit=$2 state wanted
  shift 2
  state=$(unit_state "$host" "$unit")
  for wanted in "$@"; do
    [[ "$state" == "$wanted" ]] && return 0
  done
  return 1
}

enabled_units() {
  local host=$1 pattern patterns=()
  while IFS= read -r pattern; do
    patterns+=("$pattern")
  done < <(role_unit_patterns "$host")
  host_exec "$host" systemctl list-unit-files --no-legend --state=enabled "${patterns[@]}" 2>/dev/null | awk '{print $1}'
}

node_ready() {
  local host=$1 state units unit port
  WAIT_REASON=""
  host_state_is "$host" RUNNING || { WAIT_REASON="$NODE_NOUN is not running"; return 1; }
  state=$(host_exec "$host" systemctl is-system-running 2>/dev/null)
  case "$state" in
    running | degraded) ;;
    *) WAIT_REASON="boot not finished (systemd state '${state:-unreachable}')"; return 1 ;;
  esac
  node_requires_services "$host" || return 0
  units=$(enabled_units "$host")
  [[ -n "$units" ]] || { WAIT_REASON="no enabled services found"; return 1; }
  if [[ "$(host_group "$host")" == "cs_nodes" ]] && ! grep -qx "$CS_MAIN_UNIT" <<<"$units"; then
    WAIT_REASON="$CS_MAIN_UNIT is not enabled"
    return 1
  fi
  for unit in $units; do
    unit_state_is_one_of "$host" "$unit" active || { WAIT_REASON="$unit is not active"; return 1; }
  done
  port=$(probe_port "$host")
  if [[ -n "$port" ]] && ! request "$host" "$host" "$port"; then
    WAIT_REASON="$host:$port does not answer yet"
    return 1
  fi
}

running_peer() {
  local peer
  for peer in $(cluster_hosts); do
    if [[ "$peer" != "$1" ]] && host_state_is "$peer" RUNNING; then
      echo "$peer"
      return 0
    fi
  done
  return 1
}

# Exit status of an HTTPS request from the peer to the node address: 0 answered, 28 timed out.
request() {
  host_exec "$1" curl -sk -m "$REQUEST_TIMEOUT" -o /dev/null "https://$(host_address "$2"):$3/"
}

request_answers() {
  request "$@"
}

lxd_node_stop() {
  local host=$1
  read_state "$host"
  if [[ "$STATE" == STOPPED ]]; then
    info "$host is already stopped"
    return 0
  fi
  if [[ "$STATE" == FROZEN ]]; then
    run lxc stop "$host" --force || fail "lxc stop $host failed"
  else
    run lxc stop "$host" --timeout "$TIMEOUT" || fail "lxc stop $host failed"
  fi
  $DRY_RUN && return 0
  wait_for host_state_is "$host" STOPPED || fail "$host did not reach STOPPED within ${TIMEOUT}s"
  info "$host is stopped"
}

await_node_ready() {
  local host=$1
  $DRY_RUN && return 0
  wait_for node_ready "$host" || fail "$host is not started after ${TIMEOUT}s: $WAIT_REASON"
  if node_requires_services "$host"; then
    info "$host is running, its services are active and its Issuer Service port answers"
  else
    info "$host is running; its install stops at OpenBao database creation, so no Central Server services are required"
  fi
}

lxd_node_start() {
  local host=$1
  read_state "$host"
  case "$STATE" in
    FROZEN) fail "$host is frozen; use node-thaw" ;;
    STOPPED)
      run lxc start "$host" || fail "lxc start $host failed"
      ;;
    RUNNING) info "$host is already running" ;;
    *) fail "$host is in state $STATE" ;;
  esac
  await_node_ready "$host"
}

require_service() {
  local host=$1 unit=$2
  read_state "$host"
  [[ "$STATE" == RUNNING ]] || fail "$host is $STATE; services can be changed on a running node only"
  host_exec "$host" systemctl cat "$unit" >/dev/null 2>&1 || usage_error "unknown service '$unit' on $host"
}

service_stop() {
  local host=$1 unit
  unit=$(service_unit "$2")
  require_service "$host" "$unit"
  if unit_state_is_one_of "$host" "$unit" inactive failed; then
    info "$unit on $host is already inactive"
    return 0
  fi
  run_on "$host" systemctl stop "$unit" || fail "systemctl stop $unit on $host failed"
  $DRY_RUN && return 0
  wait_for unit_state_is_one_of "$host" "$unit" inactive failed || fail "$unit on $host is still active after ${TIMEOUT}s"
  info "$unit on $host is inactive"
}

bound_units() {
  host_exec "$1" systemctl show --value -p BoundBy "$2" 2>/dev/null | tr ' ' '\n' | grep -v '^$'
}

service_start() {
  local host=$1 unit units bound active=true
  unit=$(service_unit "$2")
  require_service "$host" "$unit"
  units=("$unit")
  while IFS= read -r bound; do
    units+=("$bound")
  done < <(bound_units "$host" "$unit")
  for bound in "${units[@]}"; do
    unit_state_is_one_of "$host" "$bound" active || active=false
  done
  if $active; then
    info "${units[*]} on $host is already active"
    return 0
  fi
  run_on "$host" systemctl start "${units[@]}" || fail "systemctl start ${units[*]} on $host failed"
  $DRY_RUN && return 0
  for bound in "${units[@]}"; do
    wait_for unit_state_is_one_of "$host" "$bound" active || fail "$bound on $host is not active after ${TIMEOUT}s"
  done
  info "${units[*]} on $host is active"
}

lxd_pause() {
  run lxc pause "$1" || fail "lxc pause $1 failed"
}

lxd_resume() {
  run lxc start "$1" || fail "lxc start $1 (resume) failed"
}

node_freeze() {
  local host=$1 port peer rc serving=false
  read_state "$host"
  case "$STATE" in
    FROZEN)
      info "$host is already frozen"
      return 0
      ;;
    RUNNING) ;;
    *) fail "$host is $STATE; only a running node can be frozen" ;;
  esac
  port=$(probe_port "$host")
  peer=$(running_peer "$host")
  if [[ -n "$port" && -n "$peer" ]] && request "$peer" "$host" "$port"; then
    serving=true
  fi
  "${BACKEND}_pause" "$host"
  $DRY_RUN && return 0
  wait_for host_state_is "$host" FROZEN || fail "$host did not reach FROZEN within ${TIMEOUT}s"
  if $serving; then
    request "$peer" "$host" "$port"
    rc=$?
    [[ $rc -eq 28 ]] || fail "request to $host:$port from $peer did not time out (curl exit $rc)"
    info "$host is frozen; a request to $host:$port from $peer timed out"
  else
    info "$host is frozen; no request check, no request to a probe port was answered before the freeze"
  fi
}

node_thaw() {
  local host=$1 port peer
  read_state "$host"
  if [[ "$STATE" != FROZEN ]]; then
    info "$host is not frozen (state $STATE)"
    return 0
  fi
  "${BACKEND}_resume" "$host"
  $DRY_RUN && return 0
  wait_for host_state_is "$host" RUNNING || fail "$host did not reach RUNNING within ${TIMEOUT}s"
  port=$(probe_port "$host")
  peer=$(running_peer "$host")
  if [[ -n "$port" && -n "$peer" ]] && node_requires_services "$host"; then
    wait_for request_answers "$peer" "$host" "$port" || fail "$host:$port does not answer a request from $peer after ${TIMEOUT}s"
    info "$host is running; a request to $host:$port from $peer is answered"
  else
    info "$host is running; no request check for this node"
  fi
}

cluster_members() {
  local host hosts=()
  while IFS= read -r host; do
    hosts+=("$host")
  done < <(jq -r '.db_nodes.hosts[]' <<<"$INVENTORY_JSON")
  patroni_members "${hosts[@]}"
}

is_leader() {
  local members
  members=$(cluster_members) || return 1
  [[ "$(jq -r 'map(select(.Role == "Leader" and .State == "running") | .Member) | if length == 1 then .[0] else "" end' <<<"$members")" == "$1" ]]
}

write_lands_on() {
  local leader=$1 lb=$2 marker=$3 password
  password=$(inventory_var "$lb" database_admin_password)
  psql_through "$leader" "$(inventory_var "$lb" cluster_name)" "$(inventory_var "$lb" db_write_port)" "$password" \
    "CREATE TABLE IF NOT EXISTS cluster_probe (marker text PRIMARY KEY, written_at timestamptz DEFAULT now());
     INSERT INTO cluster_probe (marker) VALUES ('$marker') ON CONFLICT DO NOTHING" >/dev/null 2>&1 || return 1
  [[ "$(psql_on "$leader" "SELECT count(*) FROM cluster_probe WHERE marker = '$marker'" 2>/dev/null)" == 1 ]]
}

# Hands the leadership to a replica through Patroni. $1 is the Patroni command (switchover or failover),
# $2 the replica that becomes the leader.
db_handover() {
  local command=$1 candidate=$2 action=db-promote lb members leader candidate_state marker
  [[ "$command" == switchover ]] && action=db-switchover
  [[ "$(host_group "$candidate")" == db_nodes ]] || usage_error "$action needs a host of the db_nodes group, '$candidate' is not one"
  lb=$(group_first_host lb)
  [[ -n "$lb" ]] || fail "no lb host in $INVENTORY"
  read_state "$candidate"
  [[ "$STATE" == RUNNING ]] || fail "$candidate is $STATE; only a running replica can become the leader"
  read_state "$lb"
  [[ "$STATE" == RUNNING ]] || fail "$lb is $STATE; the write address cannot be checked"
  members=$(cluster_members) || fail "no Patroni member answers; the cluster state is unknown"
  leader=$(jq -r 'map(select(.Role == "Leader") | .Member) | first // empty' <<<"$members")
  [[ -n "$leader" ]] || fail "the cluster has no leader; Patroni elects one on its own"
  [[ "$leader" != "$candidate" ]] || fail "$candidate already leads; nothing changed"
  candidate_state=$(jq -r --arg h "$candidate" '.[] | select(.Member == $h) | "\(.Role) \(.State)"' <<<"$members")
  [[ "$candidate_state" == "Replica streaming" ]] || fail "$candidate is not a streaming replica (Patroni reports '${candidate_state:-no member}'); nothing changed"
  if [[ "$command" == switchover ]]; then
    run_on "$candidate" patronictl -c "$PATRONI_CONFIG" switchover --leader "$leader" --candidate "$candidate" --force \
      || fail "switchover to $candidate failed"
  else
    run_on "$candidate" patronictl -c "$PATRONI_CONFIG" failover --candidate "$candidate" --force \
      || fail "failover to $candidate failed"
  fi
  $DRY_RUN && return 0
  wait_for is_leader "$candidate" || fail "$candidate is not the only leader after ${TIMEOUT}s"
  marker="$command-$(date -u +%Y%m%dT%H%M%SZ)"
  wait_for write_lands_on "$candidate" "$lb" "$marker" || fail "a write through the write address did not land on $candidate within ${TIMEOUT}s"
  info "$candidate leads; a write through the write address of $lb landed on it"
}

db_switchover() {
  db_handover switchover "$1"
}

db_promote() {
  db_handover failover "$1"
}

lxd_service_stop() { service_stop "$@"; }
lxd_service_start() { service_start "$@"; }
lxd_node_freeze() { node_freeze "$@"; }
lxd_node_thaw() { node_thaw "$@"; }
lxd_db_switchover() { db_switchover "$@"; }
lxd_db_promote() { db_promote "$@"; }

main() {
  local arguments=() argument ACTION HOST SERVICE=""
  for argument in "$@"; do
    case "$argument" in
      -h | --help) usage; exit 0 ;;
      --dry-run) DRY_RUN=true ;;
      -*) usage_error "unknown option '$argument'" ;;
      *) arguments+=("$argument") ;;
    esac
  done
  ACTION="${arguments[0]:-}"
  HOST="${arguments[1]:-}"
  SERVICE="${arguments[2]:-}"
  [[ -n "$ACTION" ]] || { usage >&2; exit 2; }
  [[ " $ACTIONS " == *" $ACTION "* ]] || usage_error "unknown action '$ACTION'; actions: $ACTIONS"
  [[ -n "$HOST" ]] || usage_error "action $ACTION needs a host"
  case "$ACTION" in
    service-*)
      [[ -n "$SERVICE" ]] || usage_error "action $ACTION needs a service name"
      [[ "$SERVICE" =~ ^[A-Za-z0-9][A-Za-z0-9_.@-]*$ ]] || usage_error "invalid service name '$SERVICE'"
      [[ ${#arguments[@]} -le 3 ]] || usage_error "too many arguments"
      ;;
    *)
      [[ ${#arguments[@]} -le 2 ]] || usage_error "action $ACTION takes a host only"
      ;;
  esac
  if $DRY_RUN && declare -F "${BACKEND}_dry_run_setup" >/dev/null; then
    "${BACKEND}_dry_run_setup" "$ACTION" "$HOST"
  fi
  load_inventory
  cluster_hosts | grep -Fxq -- "$HOST" || usage_error "unknown host '$HOST'; hosts: $(cluster_hosts | tr '\n' ' ')"
  local action_function="${BACKEND}_${ACTION//-/_}"
  declare -F "$action_function" >/dev/null || usage_error "backend '$BACKEND' does not implement $ACTION"
  "$action_function" "$HOST" ${SERVICE:+"$SERVICE"}
  if $DRY_RUN; then
    info "dry-run: nothing changed"
  else
    info "OK: $ACTION $HOST${SERVICE:+ $SERVICE}"
  fi
}

main "$@"
