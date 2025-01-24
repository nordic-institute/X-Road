#!/bin/bash

# List and delete containers starting with xrd-
for container in $(lxc list -f csv -c n | grep ^xrd-); do
    echo "Starting container: $container"
    lxc start "$container"
done

echo "Startup complete"
