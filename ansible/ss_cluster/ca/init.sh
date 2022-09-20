#!/bin/bash
umask 0077
SUBJ=${1:-/O=cluster/CN=cluster-ca}
echo $SUBJ
if [ ! -f ca.key ]; then
    openssl req -new -x509 -days 7305 -sha512 -out ca.crt -keyout ca.key -subj "$SUBJ"
    chmod 400 ca.key ca.crt
else
    echo "ca.key already exists"
fi

