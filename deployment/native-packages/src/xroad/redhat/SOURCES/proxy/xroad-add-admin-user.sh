#!/bin/bash
set -e

if [ "$1x" = "x" ]
then
    cat << EOF
Usage: $0 <username>
Create local X-Road admin user <username> and add the user to the appropriate groups based on node type.
EOF
  exit 1
fi

#
# proxy-ui uses PAM password (pam_unix) authentication by default
# make /etc/shadow readable by group 'shadow'
#
if ! getent group shadow >/dev/null; then
    groupadd --system shadow
fi

if ! sudo -u xroad test -r /etc/shadow; then
    echo "Note. Making /etc/shadow readable by the group 'shadow'"
    chgrp shadow /etc/shadow
    chmod g+r /etc/shadow
    usermod -a -G shadow xroad || true
fi

if getent passwd "$1" >/dev/null
then
    echo "User $1 exists, just adding to the xroad groups"
else
    echo "Adding user $1"
    useradd "$1" -c "X-Road admin user"
    passwd "$1"
fi

# Use the shared setup_xroad_admin_user.sh script to add user to groups
. /usr/share/xroad/scripts/setup_xroad_admin_user.sh
setup_xroad_admin_user "$1"

