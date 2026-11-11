#!/bin/bash
# Asserts the running cluster matches the cluster inventory:
#  - Patroni reports one leader and every other running database host as a streaming replica (waits for it, so a check
#    right after a failover or a rejoin converges), and etcd has quorum among its members;
#  - every Central Server database exists on the leader and no Central Server node runs a local PostgreSQL;
#  - a write through the write address lands on the leader and is visible on the replica; the read-only address
#    rejects writes and reaches a replica, or refuses connections while only the leader runs;
#  - an admin API change on the first node is read back and found in the Central Server database on the leader
#    (retried until a deadline, because the first call after a failover may use stale pooled connections);
#  - each node uses the database write address and carries its own HA node name, distinct from the others;
#  - a node that is not expected to stop reports its HA node name through the admin API,
#    a node that is expected to stop at OpenBao database creation did stop there;
#  - the cluster name resolves to the load balancer and, through it, the Issuer Service ports serve the
#    first node's certificate carrying the cluster name, and port 80 answers HTTP.
# CHECK_PATRONI_WAIT and CHECK_CS_WRITE_WAIT (seconds) bound the Patroni wait and the admin API retries.
# Exits non-zero when any check fails, 2 when the backend is unknown.
# Each run leaves a row in the cluster_probe table of the postgres database and sets the description of the
# member class CLUSTERPROBE of the Central Server; neither is removed.
#
# The backend (FAULT_STEP_BACKEND, CLUSTER_BACKEND_FILE) and the inventory path (CLUSTER_INVENTORY)
# are described in cluster-transport.sh. The admin password is XROAD_ADMIN_PASSWORD, else xroad_cs_admin_password
# of the inventory, else the development default.
set -uo pipefail
INVOKED_FROM=$PWD
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1
# shellcheck source=cluster-transport.sh
source ./cluster-transport.sh

if ! declare -F "${BACKEND}_host_command" >/dev/null; then
  echo "ERROR: unknown backend '$BACKEND'; built in: lxd, any other comes from CLUSTER_BACKEND_FILE" >&2
  exit 2
fi

DEFAULT_ADMIN_PASSWORD="secret123!"
ADMIN_USER="${XROAD_ADMIN_USER:-xrd}"
CENTRAL_SERVER_DATABASES=(centerui_production openbao)
ETCD_CLIENT_PORT=2379
PATRONI_WAIT="${CHECK_PATRONI_WAIT:-$DEFAULT_CHECK_WAIT}"
CS_WRITE_WAIT="${CHECK_CS_WRITE_WAIT:-$DEFAULT_CHECK_WAIT}"
FAILURES=0

pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*" >&2; FAILURES=$((FAILURES + 1)); }

PROBE_MEMBER_CLASS=CLUSTERPROBE

# The remote scripts read the admin password from the first line of their standard input, so it appears in no
# process argument list.
IFS= read -r -d '' ADMIN_API_STATUS_SCRIPT <<'EOF' || true
IFS= read -r password
jar=$(mktemp)
trap 'rm -f "$jar"' EXIT
printf '%s' "$password" | curl -sk -m 20 -c "$jar" -o /dev/null --data-urlencode "username=$XROAD_ADMIN_USER" --data-urlencode "password@-" https://localhost:4000/login
token=$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar")
curl -sk -m 20 -b "$jar" -H "X-XSRF-TOKEN: $token" https://localhost:4000/api/v1/system/status
EOF

IFS= read -r -d '' ADMIN_API_CHANGE_SCRIPT <<'EOF' || true
IFS= read -r password
jar=$(mktemp)
trap 'rm -f "$jar"' EXIT
api=https://localhost:4000/api/v1/member-classes
printf '%s' "$password" | curl -sk -m 20 -c "$jar" -o /dev/null --data-urlencode "username=$XROAD_ADMIN_USER" --data-urlencode "password@-" https://localhost:4000/login
token=$(awk '$6 == "XSRF-TOKEN" {print $7}' "$jar")
call() { curl -sk -m 20 -b "$jar" -H "X-XSRF-TOKEN: $token" -H 'Content-Type: application/json' -w '\n%{http_code}' "$@"; }
if call "$api" | grep -Eq "\"code\" *: *\"$1\""; then
  status=$(call -X PATCH -d "{\"description\":\"$2\"}" "$api/$1" | tail -n 1)
else
  status=$(call -X POST -d "{\"code\":\"$1\",\"description\":\"$2\"}" "$api" | tail -n 1)
fi
case "$status" in
  2??) ;;
  *) echo "the change was answered with HTTP $status"; exit 1 ;;
esac
call "$api" | grep -Eq "\"description\" *: *\"$2\"" || { echo "the new description is not read back"; exit 1; }
EOF

admin_api_status() {
  printf '%s\n' "$ADMIN_PASSWORD" | host_exec_env "$1" "XROAD_ADMIN_USER=$ADMIN_USER" -- bash -c "$ADMIN_API_STATUS_SCRIPT"
}

admin_api_change_member_class() {
  printf '%s\n' "$ADMIN_PASSWORD" | host_exec_env "$1" "XROAD_ADMIN_USER=$ADMIN_USER" -- bash -c "$ADMIN_API_CHANGE_SCRIPT" bash "$PROBE_MEMBER_CLASS" "$2"
}

certificate_fingerprint() {
  host_exec "$1" bash -c "echo | timeout 10 openssl s_client -connect '$2' -servername '$3' 2>/dev/null | openssl x509 -noout -fingerprint -sha256" 2>/dev/null
}

certificate_names() {
  host_exec "$1" bash -c "echo | timeout 10 openssl s_client -connect '$2' -servername '$3' 2>/dev/null | openssl x509 -noout -ext subjectAltName" 2>/dev/null
}

running_of() {
  local host
  for host in $1; do
    host_state_is "$host" RUNNING && echo "$host"
  done
}

load_inventory
db_host=$(inventory_value lb database_host)
write_port=$(inventory_value lb db_write_port)
read_port=$(inventory_value lb db_read_port)
admin_database_password=$(inventory_value lb database_admin_password)
ADMIN_PASSWORD="${XROAD_ADMIN_PASSWORD:-$(inventory_value "$(group_hosts cs_nodes | head -n 1)" "xroad_cs_admin_password | default('')")}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$DEFAULT_ADMIN_PASSWORD}"
db_nodes=$(group_hosts db_nodes)
etcd_nodes=$(group_hosts etcd_nodes)
lb_node=$(group_hosts lb | head -n 1)
cluster_name=$(inventory_value "$lb_node" cluster_name)
nodes=$(group_hosts cs_nodes)
first_node=$(head -n 1 <<<"$nodes")
if [[ -z "$db_host" || -z "$db_nodes" || -z "$etcd_nodes" || -z "$write_port" || -z "$read_port" || -z "$lb_node" || -z "$cluster_name" || -z "$first_node" ]]; then
  echo "FAIL: cannot read the database, etcd, load balancer, cluster name and nodes from $INVENTORY" >&2
  exit 1
fi

running_db=$(running_of "$db_nodes")
running_etcd=$(running_of "$etcd_nodes")
if [[ -z "$running_db" ]]; then
  echo "FAIL: no database host is running" >&2
  exit 1
fi
running_db_json=$(printf '%s\n' $running_db | jq -R . | jq -sc .)

# One running leader and every other running database host streaming; members on stopped hosts are ignored.
patroni_converged() {
  jq -e --argjson running "$running_db_json" '
    [.[] | select(.Member as $m | $running | index($m))] as $members
    | ([$members[] | select(.Role == "Leader" and .State == "running")] | length) == 1
      and ([$members[] | select(.Role != "Leader")] | length) == (($running | length) - 1)
      and all($members[] | select(.Role != "Leader"); .State == "streaming")' >/dev/null 2>&1 <<<"$1"
}

members=""
deadline=$((SECONDS + PATRONI_WAIT))
# shellcheck disable=SC2086
until members=$(patroni_members $running_db) && patroni_converged "$members"; do
  ((SECONDS >= deadline)) && break
  sleep 3
done
if [[ -n "$members" ]] && patroni_converged "$members"; then
  leader_node=$(jq -r --argjson running "$running_db_json" '.[] | select(.Role == "Leader" and (.Member as $m | $running | index($m))) | .Member' <<<"$members")
  replica_nodes=$(jq -r --argjson running "$running_db_json" '.[] | select(.Role != "Leader" and (.Member as $m | $running | index($m))) | .Member' <<<"$members")
  pass "Patroni leader is $leader_node"
  for replica in $replica_nodes; do
    pass "Patroni replica $replica is streaming, replay lag $(jq -r --arg h "$replica" '.[] | select(.Member == $h) | .["Replay Lag"]' <<<"$members")"
  done
  [[ -n "$replica_nodes" ]] || pass "no other database host is running; the leader runs alone"
else
  fail "Patroni did not converge to one leader with streaming replicas on $(tr '\n' ' ' <<<"$running_db")within ${PATRONI_WAIT}s: $(jq -c '.' <<<"$members" 2>/dev/null)"
  exit 1
fi

etcd_total=$(wc -w <<<"$etcd_nodes" | tr -d " ")
etcd_quorum=$((etcd_total / 2 + 1))
etcd_healthy=0
for member in $etcd_nodes; do
  if ! host_state_is "$member" RUNNING; then
    echo "INFO: etcd member $member is stopped"
    continue
  fi
  healthy=false
  for _ in $(seq 1 30); do
    if host_exec "$member" etcdctl --endpoints="http://127.0.0.1:$ETCD_CLIENT_PORT" endpoint health >/dev/null 2>&1; then
      healthy=true
      break
    fi
    sleep 2
  done
  if $healthy; then
    pass "etcd member $member is healthy"
    etcd_healthy=$((etcd_healthy + 1))
  else
    fail "etcd member $member is running but not healthy"
  fi
done
if [[ $etcd_healthy -ge $etcd_quorum ]]; then
  pass "etcd has quorum: $etcd_healthy of $etcd_total members healthy, $etcd_quorum needed"
else
  fail "etcd has no quorum: $etcd_healthy of $etcd_total members healthy, $etcd_quorum needed"
fi
member_count=$(host_exec "$(head -n 1 <<<"$running_etcd")" etcdctl --endpoints="http://127.0.0.1:$ETCD_CLIENT_PORT" member list 2>/dev/null | wc -l | tr -d " ")
if [[ "$member_count" -eq "$etcd_total" ]]; then
  pass "etcd has $member_count members, as the inventory lists"
else
  fail "etcd has $member_count members, the inventory lists $etcd_total"
fi

databases=$(psql_on "$leader_node" "SELECT datname FROM pg_database" 2>&1)
for database in "${CENTRAL_SERVER_DATABASES[@]}"; do
  if grep -qx "$database" <<<"$databases"; then
    pass "$database exists on the leader $leader_node"
  else
    fail "$database not found on the leader $leader_node"
  fi
done

pg_query_through() {
  psql_through "$leader_node" "$cluster_name" "$1" "$admin_database_password" "$2" 2>&1
}

marker="check-$(date -u +%Y%m%dT%H%M%SZ)-$$"
written=""
for _ in $(seq 1 30); do
  written=$(pg_query_through "$write_port" "CREATE TABLE IF NOT EXISTS cluster_probe (marker text PRIMARY KEY, written_at timestamptz DEFAULT now()); INSERT INTO cluster_probe (marker) VALUES ('$marker')")
  [[ "$written" == *"INSERT 0 1" ]] && break
  sleep 2
done
if [[ "$written" == *"INSERT 0 1" ]]; then
  pass "write through $cluster_name:$write_port succeeded"
else
  fail "write through $cluster_name:$write_port failed: $written"
fi
seen=$(psql_on "$leader_node" "SELECT count(*) FROM cluster_probe WHERE marker = '$marker'" 2>&1)
if [[ "$seen" == "1" ]]; then
  pass "marker written through $cluster_name:$write_port is on the leader $leader_node"
else
  fail "marker written through $cluster_name:$write_port is not on the leader $leader_node ('$seen')"
fi
for replica in $replica_nodes; do
  seen=""
  for _ in $(seq 1 15); do
    seen=$(psql_on "$replica" "SELECT count(*) FROM cluster_probe WHERE marker = '$marker'" 2>&1)
    [[ "$seen" == "1" ]] && break
    sleep 1
  done
  if [[ "$seen" == "1" ]]; then
    pass "marker is visible on the replica $replica"
  else
    fail "marker is not visible on the replica $replica ('$seen')"
  fi
done

if [[ -n "$replica_nodes" ]]; then
  rejected=""
  recovery=""
  for _ in $(seq 1 20); do
    rejected=$(pg_query_through "$read_port" "INSERT INTO cluster_probe (marker) VALUES ('$marker-read-only')")
    recovery=$(pg_query_through "$read_port" "SELECT pg_is_in_recovery()")
    grep -q "read-only transaction" <<<"$rejected" && [[ "$recovery" == "t" ]] && break
    sleep 2
  done
  if grep -q "read-only transaction" <<<"$rejected"; then
    pass "write through the read-only address $cluster_name:$read_port is rejected"
  else
    fail "write through the read-only address $cluster_name:$read_port was not rejected: $rejected"
  fi
  if [[ "$recovery" == "t" ]]; then
    pass "the read-only address $cluster_name:$read_port reaches a replica"
  else
    fail "the read-only address $cluster_name:$read_port did not reach a replica: $recovery"
  fi
else
  refused=false
  for _ in $(seq 1 20); do
    if ! pg_query_through "$read_port" "SELECT 1" >/dev/null; then
      refused=true
      break
    fi
    sleep 2
  done
  if $refused; then
    pass "the read-only address $cluster_name:$read_port refuses connections while only the leader runs"
  else
    fail "the read-only address $cluster_name:$read_port accepts connections while only the leader runs"
  fi
fi

node_names=()
for node in $nodes; do
  expected_name=$(inventory_value "$node" xroad_cs_ha_node_name)
  stop_expected=$(inventory_value "$node" xroad_cs_openbao_db_stop_expected)

  if host_state_is "$node" RUNNING; then
    local_postgres=$(host_exec "$node" bash -c 'if pgrep -x postgres >/dev/null || ss -ltn "sport = :5432" | grep -q LISTEN; then echo present; else echo absent; fi' 2>/dev/null)
  else
    local_postgres=""
  fi
  case "$local_postgres" in
    absent) pass "$node runs no local PostgreSQL" ;;
    present) fail "$node runs a local PostgreSQL" ;;
    *) fail "cannot tell whether $node runs a local PostgreSQL: the node is stopped or unreachable" ;;
  esac

  if [[ "$stop_expected" == "True" || "$stop_expected" == "true" ]]; then
    configured_host=$(host_exec "$node" bash -c "debconf-show xroad-center | sed -n 's/^\* xroad-common\/database-host: //p'" 2>&1)
  else
    url=$(host_exec "$node" crudini --get /etc/xroad/db.properties '' spring.datasource.url 2>&1)
    configured_host=$(sed -n 's#^jdbc:postgresql://\([^/]*\)/centerui_production.*#\1#p' <<<"$url")
  fi
  if [[ "$configured_host" == "$db_host" ]]; then
    pass "$node database host is the write address $db_host"
  else
    fail "$node database host is '$configured_host', expected the write address $db_host"
  fi

  configured_name=$(host_exec "$node" crudini --get /etc/xroad/conf.d/node.ini node name 2>/dev/null)
  node_names+=("$configured_name")
  if [[ -n "$expected_name" && "$configured_name" == "$expected_name" ]]; then
    pass "$node node.ini carries HA node name $configured_name"
  else
    fail "$node node.ini carries HA node name '$configured_name', expected '$expected_name'"
  fi

  if [[ "$stop_expected" == "True" || "$stop_expected" == "true" ]]; then
    if host_exec "$node" bash -c '! systemctl is-active --quiet xroad-center && ! dpkg-query -W -f="\${db:Status-Abbrev}" xroad-secret-store-local | grep -q "^ii"'; then
      pass "$node stopped at OpenBao database creation as expected"
    else
      fail "$node is expected to stop at OpenBao database creation but its Central Server is installed or running"
    fi
  else
    deadline=$((SECONDS + CS_WRITE_WAIT))
    while :; do
      status=$(admin_api_status "$node")
      ha_configured=$(jq -r '.high_availability_status.is_ha_configured' <<<"$status" 2>/dev/null)
      node_name=$(jq -r '.high_availability_status.node_name' <<<"$status" 2>/dev/null)
      [[ "$ha_configured" == "true" && "$node_name" == "$expected_name" ]] && break
      ((SECONDS >= deadline)) && break
      sleep 5
    done
    if [[ "$ha_configured" == "true" && "$node_name" == "$expected_name" ]]; then
      pass "$node admin API reports HA node name $node_name"
    else
      fail "$node admin API reports is_ha_configured=$ha_configured node_name=$node_name, expected node_name=$expected_name"
    fi
  fi
done

proof_description=""
proof_output=""
proof_landed=false
deadline=$((SECONDS + CS_WRITE_WAIT))
until $proof_landed; do
  proof_description="probe $(date -u +%Y%m%dT%H%M%SZ)"
  if proof_output=$(admin_api_change_member_class "$first_node" "$proof_description" 2>&1); then
    in_database=$(psql_on "$leader_node" \
      "SELECT description FROM centerui.member_classes WHERE code = '$PROBE_MEMBER_CLASS'" centerui_production 2>&1)
    [[ "$in_database" == "$proof_description" ]] && proof_landed=true
    proof_output="read back through the admin API; the database row on $leader_node is '$in_database'"
  fi
  $proof_landed && break
  ((SECONDS >= deadline)) && break
  sleep 5
done
if $proof_landed; then
  pass "admin API change on $first_node (member class $PROBE_MEMBER_CLASS) is read back and found in the database on the leader $leader_node"
else
  fail "admin API change on $first_node did not land on the leader $leader_node within ${CS_WRITE_WAIT}s: $proof_output"
fi

if [[ $(printf '%s\n' "${node_names[@]}" | sort -u | wc -l) -eq ${#node_names[@]} ]]; then
  pass "HA node names are distinct: ${node_names[*]}"
else
  fail "HA node names are not distinct: ${node_names[*]}"
fi

lb_address=$(host_ip "$first_node" "$lb_node")
resolved=$(host_exec "$first_node" getent hosts "$cluster_name" | awk '{print $1}')
if [[ -n "$lb_address" && "$resolved" == "$lb_address" ]]; then
  pass "$cluster_name resolves to $lb_node ($lb_address) on $first_node"
else
  fail "$cluster_name resolves to '$resolved' on $first_node, expected $lb_address"
fi

tcp_ports=$(inventory_list "$lb_node" lb_tcp_ports) || tcp_ports=""
if [[ -z "$tcp_ports" ]]; then
  fail "cannot read the list lb_tcp_ports of $lb_node from $INVENTORY"
elif ! grep -qvxF 80 <<<"$tcp_ports"; then
  fail "lb_tcp_ports of $lb_node lists no Issuer Service port besides 80"
fi
for port in $tcp_ports; do
  if [[ "$port" == "80" ]]; then
    code=$(host_exec "$first_node" curl -s -o /dev/null -m 10 -w '%{http_code}' "http://$cluster_name:$port/")
    if [[ "$code" =~ ^[1-5][0-9][0-9]$ ]]; then
      pass "http://$cluster_name:$port answers HTTP $code"
    else
      fail "http://$cluster_name:$port gave no HTTP response"
    fi
    continue
  fi
  through=$(certificate_fingerprint "$first_node" "$cluster_name:$port" "$cluster_name")
  direct=$(certificate_fingerprint "$first_node" "$(host_address "$first_node"):$port" "$cluster_name")
  names=$(certificate_names "$first_node" "$cluster_name:$port" "$cluster_name")
  if [[ -n "$through" && "$through" == "$direct" ]]; then
    pass "$cluster_name:$port serves the certificate of $first_node"
  else
    fail "$cluster_name:$port serves '$through', $first_node serves '$direct'"
  fi
  if grep -q "DNS:$cluster_name" <<<"$names"; then
    pass "certificate on $cluster_name:$port carries $cluster_name"
  else
    fail "certificate on $cluster_name:$port does not carry $cluster_name"
  fi
done

if [[ $FAILURES -gt 0 ]]; then
  echo "$FAILURES check(s) failed" >&2
  exit 1
fi
echo "All checks passed"
