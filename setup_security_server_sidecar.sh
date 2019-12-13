#!/bin/bash

usage="\n
To create a sidecar security server instance you need to provide the first two arguments described here below.

#1 Name for the container
#2 Local port number to bind the UI
"

if [ -z "$1" ] || [ -z "$2" ]; then
    printf "$usage";
    exit;
fi

if [[ ! ( $2 =~ ^-?[0-9]+$ ) || ($2 -lt 1024) ]] ; then
    printf "Illegal port number parameter"
    exit 0;
fi

httpport=$(($2 + 1))
postgresqlport=$(($2 + 2))
ideadebuggerport=$(($2 + 3))

# Create xroad-network to provide container-to-container communication
docker network create -d bridge xroad-network

echo "=====> Build sidecar image"
docker build -f sidecar/Dockerfile.local -t xroad-sidecar-security-server-image sidecar/
printf "=====> Run container"
docker run --detach -p $2:4000 -p $httpport:80 -p $postgresqlport:5432 -p $ideadebuggerport:9999 --network xroad-network --name $1 xroad-sidecar-security-server-image


printf "\n
Sidecar security server admin UI should be accessible shortly in https://localhost:$2
$1-container port 80 is mapped to $httpport
Postgresql (port 5432) in $1 is mapped to localhost:$postgresqlport
"
