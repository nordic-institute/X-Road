#!/bin/bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage $0 <gpghome> <user id>" >&2
  exit 1
fi

GPGHOME="$1"
USERID="$2"

if [[ -f "$GPGHOME/pubring.kbx" || -f "$GPGHOME/pubring.gpg" ]] && gpg --homedir "$GPGHOME" --quiet --batch --list-secret-keys "=$USERID"; then
  echo "GPG key for '$USERID' already exists in '$GPGHOME'" >&2
  exit 0
fi

echo "Generating new GPG keypair for '$USERID'"
mkdir -p -m 0700 "$GPGHOME"

# skip actual key creation for E2E

