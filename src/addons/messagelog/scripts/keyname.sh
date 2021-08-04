#!/bin/bash
set -euo pipefail

usage() {
  cat <<EOF
  Given a member name in format INSTANCE/MEMBERCLASS/MEMBERCODE (case sensitive, UTF-8),
  outputs corresponding message log archive encryption key file name.

  Example:
    $0 INSTANCE/MEMBERCLASS/MEMBERCODE
    c09e85dc0b46ca7a6d78b8e18fb421d5bb8e7e978abb5c7da450b71b8608e20e.pgp
EOF
  exit 1
}

if [[ $# -ne 1 ]]; then
  usage
fi

member=$(echo -En "$1" | iconv -f UTF-8 -t UTF-8) || true
if [[ "$member" != "$1" ]]; then
  echo "ERROR: Expected $1 to be UTF-8 encoded"
  exit 2
fi

name=$(echo -En "$member" | sha256sum -b | cut -d' ' -f1)
echo "$name.pgp"
