#!/bin/bash

GPGHOME="$1"
if [ -d "$GPGHOME" ] ; then
  rm -rf "$GPGHOME"
fi

echo "GENERATING NEW KEYPAIR"
mkdir -p "$GPGHOME"
chmod 700 "$GPGHOME"

gpg --homedir "$GPGHOME" --batch --gen-key <<EOF
Key-Type: 1
Key-Length: 4096
Name-Real: $2
Expire-Date: 0
%no-protection
EOF


