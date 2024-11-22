#!/bin/bash

# List and delete containers starting with xroad-lxd
for container in $(lxc list -f csv -c n | grep ^xroad-lxd); do
    echo "Deleting container: $container"
    lxc delete -f "$container"
done

echo "Cleanup complete"
