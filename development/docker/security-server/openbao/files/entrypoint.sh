#!/bin/sh

# Drops to the unprivileged openbao user for the network-reachable server
# process; secret-store-init.sh below still needs root for the chown calls it
# makes on the generated token/key files.
su -s /bin/sh -c 'exec bao server -dev -dev-root-token-id="$BAO_TOKEN" -dev-listen-address="${BAO_DEV_LISTEN_ADDRESS:-0.0.0.0:8200}"' openbao &

./usr/share/xroad/scripts/secret-store-init.sh

wait
