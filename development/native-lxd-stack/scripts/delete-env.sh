#!/bin/bash

case "$(uname)" in
  Darwin)
    "${BASH_SOURCE%/*}/setup-mac-net.sh" cleanup || echo "setup-mac-net.sh cleanup failed (continuing)"
    ;;
  Linux)
    "${BASH_SOURCE%/*}/setup-linux-net.sh" cleanup || echo "setup-linux-net.sh cleanup failed (continuing)"
    ;;
esac

# List and delete containers starting with xrd-
for container in $(lxc list -f csv -c n | grep ^xrd-); do
    echo "Deleting container: $container"
    lxc delete -f "$container"
done

echo "Cleanup complete"
