#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [entrypoint] $*" >&2; }

if [ ! -d /home/ca/certs ]; then
  mkdir -p /home/ca/certs
fi

cp /home/ca/CA/certs/ca.cert.pem /home/ca/certs/ca.pem
cp /home/ca/CA/certs/ocsp.cert.pem /home/ca/certs/ocsp.pem
cp /home/ca/CA/certs/tsa.cert.pem /home/ca/certs/tsa.pem

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
