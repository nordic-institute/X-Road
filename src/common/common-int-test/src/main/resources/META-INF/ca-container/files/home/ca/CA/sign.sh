#!/bin/bash
cd /home/ca/CA
if [ "$1" = "" ]
then
    echo "Usage $0 [certificate request file]" >&2
    exit 1
fi

if [[ "$(basename "$1")" =~ ^sign.* ]]
then
    EXT=sign_ext
else
    EXT=auth_ext
fi

if grep -q -- '--BEGIN CERTIFICATE REQUEST--' "$1"; then
    INFORM=PEM
else
    INFORM=DER
fi

if mkdir lock &>/dev/null; then
  trap 'status=$?; rm -rf "lock"; exit $status' INT TERM EXIT
else
  echo "CA database locked" >&2
  exit 1
fi

set -e
SER=$(cat serial)
openssl req -in "$1" -inform $INFORM -out "csr/${SER}.csr"
openssl ca -batch -config CA.cnf -extensions "$EXT" -days 7300 -notext -md sha256 -in "csr/${SER}.csr"
set +e
exit 0

