#!/bin/bash

rm -rf ./files
mkdir -p ./files

cp ../../../Docker/centralserver/files/cs-entrypoint.sh ./files/.

# Remove test CA services from the configuration file
cat ../../../Docker/centralserver/files/cs-xroad.conf | sed '/\[program\:ocsp]/,/^$/d' | sed '/\[program\:tsa]/,/^$/d' | sed '/\[program\:sign]/,/^$/d' > ./files/cs-xroad.conf
