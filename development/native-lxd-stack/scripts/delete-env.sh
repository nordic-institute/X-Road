#!/bin/bash

# List and delete containers starting with xrd-
for container in $(lxc list -f csv -c n | grep ^xrd-); do
    echo "Deleting container: $container"
    lxc delete -f "$container"
done

echo "Cleanup complete"
