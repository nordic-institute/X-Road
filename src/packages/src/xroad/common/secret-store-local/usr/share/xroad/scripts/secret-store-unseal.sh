#!/bin/bash

usage() {
  echo "Usage: $0 <unseal-keys-file> <number-of-keys>"
  exit 1
}

# Input validation
if [[ $# -ne 2 ]]; then
  echo "Error: Missing arguments."
  usage
fi

unseal_keys_file="$1"
key_threshold="$2"

if [[ ! -f "$unseal_keys_file" ]]; then
  echo "Error: File '$unseal_keys_file' not found."
  exit 1
fi

if ! [[ "$key_threshold" =~ ^[0-9]+$ ]]; then
  echo "Error: '$key_threshold' is not a valid number."
  exit 1
fi

total_keys=$(wc -l < "$unseal_keys_file")
if (( key_threshold > total_keys )); then
  echo "Error: Requested $key_threshold keys, but only $total_keys are available in '$unseal_keys_file'."
  exit 1
fi

echo "Unsealing OpenBao..."
head -n "$key_threshold" "$unseal_keys_file" | while IFS= read -r key; do
  bao operator unseal "$key"
done
