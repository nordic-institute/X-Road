#!/bin/bash
set -euo pipefail

usage() {
  [[ $# -gt 0 && -n "$1" ]] && echo -e "  $1\n"

  cat <<EOF >&2
  Usage:
  $0 [-f] <member identifier>

  Given a member identifier in format INSTANCE/MEMBERCLASS/MEMBERCODE (case sensitive, UTF-8),
  outputs corresponding message log archive encryption key file name.

  -f   Use relaxed format (allows / in identifier components)

  Example:
    $0 INSTANCE/MEMBERCLASS/MEMBERCODE
    c09e85dc0b46ca7a6d78b8e18fb421d5bb8e7e978abb5c7da450b71b8608e20e.pgp
EOF
  exit 1
}

format='^[^/]+/[^/]+/[^/]+$'

case $# in
1)
  id="$1"
  ;;
2)
  if [ "$1" = "-f" ]; then
    format='^.+/.+/.+$'
  else
    usage "Invalid flag $1"
  fi
  id="$2"
  ;;
*)
  usage
  ;;
esac

member=$(echo -En "$id" | iconv -f UTF-8 -t UTF-8) || true
if [[ "$member" != "$id" ]]; then
  usage "ERROR: '$id' is not UTF-8 encoded"
fi

if [[ ! "$member" =~ $format ]]; then
  usage "ERROR: Invalid member identifier '$id'"
fi

name=$(echo -En "$member" | sha256sum -b | cut -d' ' -f1)
echo "$name.pgp"
