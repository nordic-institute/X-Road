#!/bin/bash
# Linux-only: configure systemd-resolved so the host resolves *.lxd names
# (e.g. xrd-cs.lxd) via LXD's dnsmasq on the lxdbr0 bridge (10.10.10.1).
# Linux equivalent of setup-mac-net.sh — but with no route plumbing, since
# the Linux host already sits directly on lxdbr0. Idempotent.
#
# Usage:
#   setup-linux-net.sh apply     # configure per-link DNS (default)
#   setup-linux-net.sh cleanup   # revert per-link DNS
#   setup-linux-net.sh status    # show current state, no changes
set -e

if [[ -f "${BASH_SOURCE%/*}/../../../.scripts/base-script.sh" ]]; then
  source "${BASH_SOURCE%/*}/../../../.scripts/base-script.sh"
else
  log_info()  { echo "[INFO] $*"; }
  log_error() { echo "[ERROR] $*" >&2; }
fi

ACTION="${1:-apply}"
BRIDGE=lxdbr0
LXD_DNS_IP=10.10.10.1
LXD_DOMAIN=lxd

function bridgePresent() {
  [[ -d "/sys/class/net/$BRIDGE" ]]
}

function hasResolvectl() {
  command -v resolvectl >/dev/null 2>&1
}

function currentLinkDns() {
  resolvectl dns "$BRIDGE" 2>/dev/null | awk -F': ' '/Link [0-9]+/ {print $2}'
}

function currentLinkDomain() {
  resolvectl domain "$BRIDGE" 2>/dev/null | awk -F': ' '/Link [0-9]+/ {print $2}'
}

function smokeTest() {
  # Direct UDP/53 query against dnsmasq, bypassing the system resolver — same
  # idea as setup-mac-net.sh smokeTest. Confirms the bridge dnsmasq itself is
  # answering; system-resolver config is verified separately below.
  local out smoke
  if command -v dig >/dev/null 2>&1; then
    out=$(dig +short +time=2 +tries=1 @"$LXD_DNS_IP" "xrd-cs.$LXD_DOMAIN" 2>&1 || true)
    smoke=$(echo "$out" | awk '/^10\.10\.10\./ {print; exit}')
  else
    out=$(nslookup -timeout=2 -retry=0 "xrd-cs.$LXD_DOMAIN" "$LXD_DNS_IP" 2>&1 || true)
    smoke=$(echo "$out" | awk '/^Address: 10\.10\.10\./ {print $2; exit}')
  fi
  if [[ "$smoke" =~ ^10\.10\.10\. ]]; then
    log_info "smoke: xrd-cs.$LXD_DOMAIN -> $smoke (direct via $LXD_DNS_IP)"
  else
    log_info "smoke: WARNING xrd-cs.$LXD_DOMAIN did not resolve via $LXD_DNS_IP (env may not be up yet)"
  fi

  # System resolver path — relies on the resolvectl config we just applied.
  if getent hosts "xrd-cs.$LXD_DOMAIN" >/dev/null 2>&1; then
    log_info "smoke: xrd-cs.$LXD_DOMAIN resolves via system resolver"
  else
    log_info "smoke: NOTE xrd-cs.$LXD_DOMAIN not yet resolvable via system resolver"
  fi
}

function applyLinuxNet() {
  if ! bridgePresent; then
    log_info "apply: $BRIDGE not present; LXD may not be installed/started — skipping"
    return 0
  fi
  if ! hasResolvectl; then
    log_info "apply: resolvectl not found (no systemd-resolved). Add manually:"
    log_info "  echo 'nameserver $LXD_DNS_IP' | sudo tee /etc/resolv.conf.d/lxd"
    log_info "  or your distro's equivalent"
    return 0
  fi

  local cur_dns cur_domain need_dns=0 need_domain=0
  cur_dns=$(currentLinkDns)
  cur_domain=$(currentLinkDomain)

  [[ "$cur_dns" != *"$LXD_DNS_IP"* ]] && need_dns=1
  [[ "$cur_domain" != *"~$LXD_DOMAIN"* ]] && need_domain=1

  if (( need_dns == 0 && need_domain == 0 )); then
    log_info "apply: $BRIDGE DNS=$LXD_DNS_IP, domain=~$LXD_DOMAIN already current"
  else
    log_info "apply: changes needed (dns=$need_dns domain=$need_domain); prompting for sudo"
    sudo -v
    if (( need_dns )); then
      log_info "apply: resolvectl dns $BRIDGE $LXD_DNS_IP"
      sudo resolvectl dns "$BRIDGE" "$LXD_DNS_IP"
    fi
    if (( need_domain )); then
      log_info "apply: resolvectl domain $BRIDGE ~$LXD_DOMAIN"
      sudo resolvectl domain "$BRIDGE" "~$LXD_DOMAIN"
    fi
    sudo resolvectl flush-caches 2>/dev/null || true
  fi

  smokeTest || true
}

function cleanupLinuxNet() {
  if ! hasResolvectl; then
    log_info "cleanup: resolvectl not available; nothing to do"
    return 0
  fi
  if ! bridgePresent; then
    log_info "cleanup: $BRIDGE not present; per-link config already gone"
    return 0
  fi

  local cur_dns cur_domain
  cur_dns=$(currentLinkDns)
  cur_domain=$(currentLinkDomain)
  if [[ -z "$cur_dns" && -z "$cur_domain" ]]; then
    log_info "cleanup: $BRIDGE has no per-link DNS config; nothing to do"
    return 0
  fi

  log_info "cleanup: prompting for sudo to revert $BRIDGE DNS"
  sudo -v
  sudo resolvectl revert "$BRIDGE" 2>/dev/null || true
  sudo resolvectl flush-caches 2>/dev/null || true
}

function statusLinuxNet() {
  log_info "bridge $BRIDGE          : $(bridgePresent && echo present || echo missing)"
  log_info "resolvectl available    : $(hasResolvectl && echo yes || echo no)"
  if hasResolvectl && bridgePresent; then
    log_info "$BRIDGE DNS             : $(currentLinkDns)"
    log_info "$BRIDGE domain          : $(currentLinkDomain)"
  fi
  smokeTest || true
}

if [[ $(uname) != "Linux" ]]; then
  log_info "setup-linux-net.sh: not on Linux, nothing to do"
  exit 0
fi

case "$ACTION" in
  apply)   applyLinuxNet ;;
  cleanup) cleanupLinuxNet ;;
  status)  statusLinuxNet ;;
  *)
    log_error "Unknown action: $ACTION (expected apply|cleanup|status)"
    exit 1
    ;;
esac
