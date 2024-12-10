#!/bin/bash

# List and delete containers starting with xrd-
for container in $(lxc list -f csv -c n | grep ^xrd-); do
    echo "Stopping container: $container"
    lxc stop -f "$container"
done

echo "Cleanup complete"
