#!/bin/bash
# Supervisor event listener: unseals OpenBao every time the openbao program reaches RUNNING,
# the supervisord counterpart of ExecStartPost in the openbao systemd drop-in.
# Stdout carries the supervisor event protocol, so the unseal output goes to stderr.

UNSEAL_SCRIPT=/usr/share/xroad/scripts/secret-store-unseal.sh

while true; do
  echo "READY"
  read -r header || exit 0
  length=${header##*len:}
  read -r -N "$length" payload
  case "$payload" in
    "processname:openbao "*) "$UNSEAL_SCRIPT" >&2 || true ;;
  esac
  printf 'RESULT 2\nOK'
done
