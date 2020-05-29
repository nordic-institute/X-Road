#!/bin/bash

usage="
  To create a sidecar security server instance you need to provide the five arguments below:

    #1 Name for the sidecar security server container
    #2 Local port number to bind the sidecar security server admin UI
    #3 Software token PIN code for autologin service
    #4 Username for sidecar security server admin UI
    #5 Password for sidecar security server admin UI

  Optionally, to configure the connection to an external postgresql database server instead of the default local one, you need to provide the additional two arguments:

    #6 Hostname to connect to the external postgresql database (default 127.0.0.1)
    #7 Port number to connect to the external postgresql database (default 5432)

    If you provide the two parameters above, you must also set the environment variable XROAD_DB_PASSWORD with the remote database administrator master password.
"

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ] || [ -z "$5" ]; then
    printf "$usage";
    exit;
fi

if [[ ! ( $2 =~ ^-?[0-9]+$ ) || ($2 -lt 1024) ]] || [[ (! -z "$7") && (! ( $7 =~ ^-?[0-9]+$ ) || ($7 -lt 1024) ) ]] ; then
    printf "Illegal port number parameter"
    exit 0;
fi

if [[ ! -z "$6" && -z "$XROAD_DB_PASSWORD" ]];
then
  echo "
  To configure an external postgresql database, you must also set the environment variable XROAD_DB_PASSWORD with the remote database administrator master password.
  "
  exit;
fi

httpport=$(($2 + 1))
postgresqlhost=${6:-127.0.0.1}
postgresqlport=${7:-5432}

# Create xroad-network to provide container-to-container communication
docker network inspect xroad-network >/dev/null 2>&1 || docker network create -d bridge xroad-network
echo "=====> Build sidecar image"
docker build -f sidecar/Dockerfile -t xroad-sidecar-security-server-image sidecar/
echo "=====> Run container"
docker run --detach -p $2:4000 -p $httpport:80 -p 5588:5588 --network xroad-network -e XROAD_TOKEN_PIN=$3 -e XROAD_ADMIN_USER=$4 -e XROAD_ADMIN_PASSWORD=$5 -e XROAD_DB_HOST=$postgresqlhost -e XROAD_DB_PORT=$postgresqlport -e XROAD_DB_PWD=$XROAD_DB_PASSWORD  -e XROAD_LOG_LEVEL=$XROAD_LOG_LEVEL --name $1 xroad-sidecar-security-server-image

printf "\n
Sidecar security server software token PIN is set to $3
Sidecar security server admin UI should be accessible shortly in https://localhost:$2
$1-container port 80 is mapped to $httpport
Sidecar security server database is located at $postgresqlhost:$postgresqlport
"
