#!/bin/bash
set -e
XG="xroad-security-officer,xroad-registration-officer,xroad-service-administrator,xroad-system-administrator,xroad-system-auditor,xroad-users-administrator"

if [ "$1x" = "x" ]
then
    cat << EOF
Usage: $0 <username>
Create X-Road admin user <username> and add the user to the following groups:
EOF
    echo " ${XG//,/$'\n' }"
    exit 1
fi

if getent passwd "$1" >/dev/null
then
    echo "User $1 exists, just adding to the xroad groups"
    usermod -a -G $XG "$1"
else
    echo "Adding user $1"
    useradd "$1" -c "X-Road admin user" -G $XG
    passwd "$1"
fi

