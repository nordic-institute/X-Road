#!/bin/bash
if [ $# -eq 0 ]; then
    cat <<EOF
  Usage: $0 <nodename> [basedn]
  Creates a TLS key and certificate. The key is encrypted by ansible-vault.

  <nodename>  Name of the cluster node (required).
  basedn      Optional base dn for the certificate subject. Defaults to "/O=cluster"
              See openssl req documentation for the subject syntax.

  Note. One needs to have the CA key passphrase and the ansible vault password available.
EOF
    exit 1
fi

NODE=$1
DAYS=3652
BASEDN=${2:-/O=cluster}

umask 0077
if [ ! -e "$NODE/server.key" ]; then
    mkdir $NODE
    PW=$(openssl rand -hex 32)
    echo $PW | openssl req -new -passout stdin -keyout $NODE/server.key.tmp -out $NODE/server.csr -subj "$BASEDN/CN=$NODE"
    echo $PW | openssl rsa -passin stdin -in "$NODE/server.key.tmp" | ansible-vault encrypt --output $NODE/server.key
    rm "$NODE/server.key.tmp"
    openssl x509 -req -in $NODE/server.csr -CAcreateserial -CA ca.crt -CAkey ca.key -days $DAYS -sha256 -out $NODE/server.crt
fi

