#!/bin/bash
# Mac-only: route + /etc/resolver/lxd so the host can reach LXD containers
# directly via socket_vmnet -> lima -> lxdbr0. Replaces the old per-port LXD
# proxy device shenanigans. Idempotent. Probes state without sudo first;
# only prompts (sudo -v) when something actually needs writing.
#
# Usage:
#   setup-mac-net.sh apply     # add route + write /etc/resolver/lxd (default)
#   setup-mac-net.sh cleanup   # remove both
#   setup-mac-net.sh status    # show current state, no changes
set -e

# base-script.sh provides log_info / log_error if available; otherwise stub.
if [[ -f "${BASH_SOURCE%/*}/../../../scripts/lib/base-script.sh" ]]; then
  source "${BASH_SOURCE%/*}/../../../scripts/lib/base-script.sh"
else
  log_info()  { echo "[INFO] $*"; }
  log_error() { echo "[ERROR] $*" >&2; }
fi

ACTION="${1:-apply}"
RESOLVER=/etc/resolver/lxd
SUBNET=10.10.10.0/24
LIMA_INSTANCE="${LIMA_INSTANCE:-xroad-lxd}"
LXD_DNS_IP=10.10.10.1
# socket_vmnet network — packets from the mac arrive at the lima VM with
# this saddr range. Used to punch a hole through LXD's nftables drops.
LIMA_HOST_NET=192.168.105.0/24
NFT_TABLE="inet lxd"
NFT_CHAIN="in.lxdbr0"
NFT_COMMENT="xroad-mac-direct"

function discoverLimaIp() {
  limactl shell "$LIMA_INSTANCE" ip -4 -br addr show lima0 2>/dev/null \
    | awk '{for (i=1;i<=NF;i++) if ($i ~ /\//) {split($i,a,"/"); print a[1]; exit}}'
}

function currentRouteGw() {
  netstat -rn -f inet | awk '$1 ~ /^10[.]10[.]10[\/.]/ {print $2; exit}'
}

function applyNftException() {
  # LXD's `in.lxdbr0` chain drops DNS to 10.10.10.1 unless it arrives via
  # lxdbr0 or lo. Mac queries arrive via the VM's external iface, so they
  # hit the drop. Insert accept rules at the top of the chain (idempotent
  # via comment match).
  if ! limactl shell "$LIMA_INSTANCE" sudo nft list chain $NFT_TABLE $NFT_CHAIN 2>/dev/null \
       | grep -q "$NFT_COMMENT"; then
    log_info "apply: inserting nft accept rules in $NFT_CHAIN for $LIMA_HOST_NET -> $LXD_DNS_IP:53"
    limactl shell "$LIMA_INSTANCE" sudo nft insert rule $NFT_TABLE $NFT_CHAIN \
      ip saddr $LIMA_HOST_NET ip daddr $LXD_DNS_IP tcp dport 53 accept comment '"'"$NFT_COMMENT"'"'
    limactl shell "$LIMA_INSTANCE" sudo nft insert rule $NFT_TABLE $NFT_CHAIN \
      ip saddr $LIMA_HOST_NET ip daddr $LXD_DNS_IP udp dport 53 accept comment '"'"$NFT_COMMENT"'"'
  else
    log_info "apply: nft accept rules already present in $NFT_CHAIN"
  fi
}

function cleanupNftException() {
  # Best-effort: delete every rule tagged with our comment.
  local handles
  handles=$(limactl shell "$LIMA_INSTANCE" sudo nft -a list chain $NFT_TABLE $NFT_CHAIN 2>/dev/null \
            | awk -v c="$NFT_COMMENT" '$0 ~ c {for (i=1;i<=NF;i++) if ($i=="handle") print $(i+1)}')
  if [[ -z "$handles" ]]; then
    return 0
  fi
  log_info "cleanup: removing nft accept rules in $NFT_CHAIN (handles: $handles)"
  for h in $handles; do
    limactl shell "$LIMA_INSTANCE" sudo nft delete rule $NFT_TABLE $NFT_CHAIN handle "$h" 2>/dev/null || true
  done
}

function smokeTest() {
  # Direct UDP/53 query against dnsmasq, bypassing macOS resolver framework.
  # nslookup -timeout=2 -retry=0 -> bounded ~2s if dnsmasq is unreachable.
  local out smoke
  out=$(nslookup -timeout=2 -retry=0 xrd-cs.lxd "$LXD_DNS_IP" 2>&1 || true)
  smoke=$(echo "$out" | awk '/^Address: 10\.10\.10\./ {print $2; exit}')
  if [[ "$smoke" =~ ^10\.10\.10\. ]]; then
    log_info "smoke: xrd-cs.lxd -> $smoke (via $LXD_DNS_IP)"
    return 0
  fi
  log_info "smoke: WARNING xrd-cs.lxd did not resolve via $LXD_DNS_IP"
  log_info "  raw: $(echo "$out" | tr '\n' ' ' | head -c 200)"
  log_info "  Debug: nslookup xrd-cs.lxd $LXD_DNS_IP   /   limactl shell $LIMA_INSTANCE sudo nft list ruleset"
  return 1
}

function applyMacNet() {
  local lima_ip
  lima_ip=$(discoverLimaIp)
  if [[ -z "$lima_ip" ]]; then
    log_info "apply: could not discover Lima socket_vmnet IP; skipping"
    return 0
  fi

  local current_gw need_route=0 need_resolver=0
  current_gw=$(currentRouteGw)
  [[ "$current_gw" != "$lima_ip" ]] && need_route=1

  local desired_resolver
  desired_resolver=$(printf '# Managed by setup-mac-net.sh; routes *.lxd to LXD dnsmasq.\nnameserver %s\ntimeout 1\n' "$LXD_DNS_IP")
  if [[ ! -f "$RESOLVER" ]] || [[ "$(cat "$RESOLVER" 2>/dev/null)" != "$desired_resolver" ]]; then
    need_resolver=1
  fi

  if (( need_route == 0 && need_resolver == 0 )); then
    log_info "apply: route $SUBNET -> $lima_ip and $RESOLVER already current"
  else
    log_info "apply: changes needed (route=$need_route resolver=$need_resolver); prompting for sudo"
    sudo -v

    if (( need_route )); then
      if [[ -n "$current_gw" ]]; then
        log_info "apply: replacing route ($current_gw -> $lima_ip)"
        sudo route -n delete -net "$SUBNET" >/dev/null || true
      else
        log_info "apply: adding route $SUBNET -> $lima_ip"
      fi
      sudo route -n add -net "$SUBNET" "$lima_ip" >/dev/null
    fi

    if (( need_resolver )); then
      log_info "apply: mkdir -p $(dirname "$RESOLVER")"
      sudo mkdir -p "$(dirname "$RESOLVER")"
      log_info "apply: tee -> $RESOLVER"
      printf '%s' "$desired_resolver" | sudo tee "$RESOLVER" >/dev/null
      log_info "apply: flushing DNS cache (backgrounded)"
      ( sudo dscacheutil -flushcache 2>/dev/null & disown ) || true
    fi
  fi

  applyNftException || log_info "apply: nft exception failed (non-fatal)"

  smokeTest || true
}

function cleanupMacNet() {
  cleanupNftException || true

  local need_resolver=0 need_route=0
  [[ -f "$RESOLVER" ]] && need_resolver=1
  netstat -rn -f inet | awk '$1 ~ /^10[.]10[.]10[\/.]/ {found=1} END{exit !found}' && need_route=1

  if (( need_resolver == 0 && need_route == 0 )); then
    log_info "cleanup: nothing to remove"
    return 0
  fi

  log_info "cleanup: prompting for sudo (resolver=$need_resolver route=$need_route)"
  sudo -v

  if (( need_resolver )); then
    log_info "cleanup: removing $RESOLVER"
    sudo rm -f "$RESOLVER"
    sudo dscacheutil -flushcache 2>/dev/null || true
  fi
  if (( need_route )); then
    log_info "cleanup: deleting route $SUBNET"
    sudo route -n delete -net "$SUBNET" >/dev/null || true
  fi
}

function statusMacNet() {
  local lima_ip current_gw
  lima_ip=$(discoverLimaIp || true)
  current_gw=$(currentRouteGw)
  log_info "lima socket_vmnet IP : ${lima_ip:-<not discovered>}"
  log_info "route $SUBNET gw    : ${current_gw:-<none>}"
  if [[ -f "$RESOLVER" ]]; then
    log_info "$RESOLVER          : present"
  else
    log_info "$RESOLVER          : missing"
  fi
  smokeTest || true
}

if [[ $(uname) != "Darwin" ]]; then
  log_info "setup-mac-net.sh: not on macOS, nothing to do"
  exit 0
fi

case "$ACTION" in
  apply)   applyMacNet ;;
  cleanup) cleanupMacNet ;;
  status)  statusMacNet ;;
  *)
    log_error "Unknown action: $ACTION (expected apply|cleanup|status)"
    exit 1
    ;;
esac
