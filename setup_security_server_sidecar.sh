#!/bin/bash

usage="\n
To create a sidecar security server instance you need to provide the seven arguments described here below.

#1 Name for the sidecar security server container
#2 Local port number to bind the sidecar security server admin UI
#3 Software token PIN code for autologin service
#4 Username for sidecar security server admin UI
#5 Password for sidecar security server admin UI
#6 Username for sidecar security server configuration database
#7 Password for sidecar security server configuration database
"

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    printf "$usage";
    exit;
fi

if [[ ! ( $2 =~ ^-?[0-9]+$ ) || ($2 -lt 1024) ]] ; then
    printf "Illegal port number parameter"
    exit 0;
fi

httpport=$(($2 + 1))

# Create xroad-network to provide container-to-container communication
docker network inspect xroad-network >/dev/null 2>&1 || docker network create -d bridge xroad-network

echo "=====> Build sidecar image"
docker build -f sidecar/Dockerfile --build-arg XROAD_ADMIN_USER=$4 --build-arg XROAD_ADMIN_PASSWORD=$5  --build-arg XROAD_DB_USER=$6  --build-arg XROAD_DB_PASSWD=$7 -t xroad-sidecar-security-server-image sidecar/
echo "=====> Run container"
docker run --detach -p $2:4000 -p $httpport:80 -p 5588:5588 -e XROAD_TOKEN_PIN=$3 --network xroad-network --name $1 xroad-sidecar-security-server-image

printf "\n
Sidecar security server software token PIN is set to $3
Sidecar security server admin UI should be accessible shortly in https://localhost:$2
$1-container port 80 is mapped to $httpport
"
