#!/bin/bash
#
# Unseals OpenBao with the unseal keys file. Runs after every start of openbao.service.
# Always exits 0, so that it never fails the OpenBao unit; when OpenBao cannot be unsealed
# the reason is logged and the next start retries.
#
# With --join (a node joining an existing cluster) the join bundle files must be present and
# OpenBao must already be initialised; otherwise it stops with a non-zero exit code.
#
# Usage: secret-store-unseal.sh [--join]
umask 077

# The whole run stays well under the unit's TimeoutStartSec, even when OpenBao accepts a
# connection and never answers.
TOTAL_TIMEOUT_SECONDS=50
if [ -z "$XROAD_UNSEAL_BOUNDED" ]; then
  XROAD_UNSEAL_BOUNDED=1 timeout -k 2 "$TOTAL_TIMEOUT_SECONDS" bash "$0" "$@"
  rc=$?
  case "$rc" in
    124|137|143)
      echo "Unseal did not complete within ${TOTAL_TIMEOUT_SECONDS}s; leaving OpenBao as it is, the next start retries" >&2
      exit 0 ;;
    *) exit "$rc" ;;
  esac
fi

. /usr/share/xroad/scripts/_openbao.sh

BAO_ADDR=${BAO_ADDR:-https://127.0.0.1:8200}
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"
export BAO_API_MAX_TIME=5

give_up() {
  echo "$*" >&2
  exit 0
}

JOIN=false
case "${1:-}" in
  --join) JOIN=true ;;
  "") ;;
  *)
    echo "Usage: $0 [--join]" >&2
    exit 1
    ;;
esac

if [ "$JOIN" = true ]; then
  require_join_files
fi

wait_until_ready "$BAO_ADDR" || give_up "OpenBao is not reachable at $BAO_ADDR; leaving it as it is, the next start retries"

if [ "$JOIN" = true ] && ! is_initialized "$BAO_ADDR"; then
  fail_join_not_initialised
fi

if is_sealed "$BAO_ADDR"; then sealed_rc=0; else sealed_rc=$?; fi
case "$sealed_rc" in
  0) ;;
  2) give_up "Cannot determine the OpenBao seal status; leaving it as it is, the next start retries" ;;
  *) echo "OpenBao is already unsealed"; exit 0 ;;
esac

[ -s "$UNSEAL_KEYS_FILE" ] || give_up "OpenBao is sealed and the unseal keys file $UNSEAL_KEYS_FILE is missing or empty; not unsealing"

echo "Unsealing OpenBao..."
while IFS= read -r key || [ -n "$key" ]; do
  unseal "$BAO_ADDR" "$key" || give_up "Failed to apply an unseal key; OpenBao stays sealed"

  if is_sealed "$BAO_ADDR"; then sealed_rc=0; else sealed_rc=$?; fi
  case "$sealed_rc" in
    0) ;;
    2) give_up "Cannot verify the OpenBao seal status after unsealing; leaving it as it is" ;;
    *) echo "Successfully unsealed OpenBao"; exit 0 ;;
  esac
done <"$UNSEAL_KEYS_FILE"

give_up "The unseal keys in $UNSEAL_KEYS_FILE did not unseal OpenBao"
