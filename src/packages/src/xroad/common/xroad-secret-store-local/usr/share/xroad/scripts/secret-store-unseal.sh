#!/bin/bash

KEYS_FILE="/etc/xroad/secret-store-unseal-keys.json"

if [ ! -f "$KEYS_FILE" ]; then
  echo "No unseal keys found"
  exit 1
fi

# Read first two keys for unsealing
KEY1=$(jq -r '.[0]' "$KEYS_FILE")
KEY2=$(jq -r '.[1]' "$KEYS_FILE")

# Unseal with two keys
BAO_ADDR="$BAO_HOST" bao operator unseal "$KEY1"
BAO_ADDR="$BAO_HOST" bao operator unseal "$KEY2"
