#!/bin/bash

# Development support script for copying files to running lxd security server running on local machine.
#exit on errors
set -e

LXDHOST=$1
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
TARGET=$LXDHOST/usr/share/xroad/scripts/

BASESCRIPTDIR=$DIR/src/xroad/common/base/usr/share/xroad/scripts
PROXYSCRIPTDIR=$DIR/src/xroad/common/proxy/usr/share/xroad/scripts

# maybe move this to common script?
lxc file push $BASESCRIPTDIR/_backup_restore_common.sh $TARGET
lxc file push $BASESCRIPTDIR/_backup_xroad.sh $TARGET
lxc file push $BASESCRIPTDIR/_restore_xroad.sh $TARGET
lxc file push $BASESCRIPTDIR/_setup_db.sh $TARGET
lxc file push $BASESCRIPTDIR/generate_certificate.sh $TARGET
lxc file push $BASESCRIPTDIR/generate_gpg_keypair.sh $TARGET
lxc file push $BASESCRIPTDIR/xroad-base.sh $TARGET

# proxy specific part
lxc file push $PROXYSCRIPTDIR/autobackup_xroad_proxy_configuration.sh $TARGET
lxc file push $PROXYSCRIPTDIR/backup_db.sh $TARGET
lxc file push $PROXYSCRIPTDIR/backup_xroad_proxy_configuration.sh $TARGET
lxc file push $PROXYSCRIPTDIR/get_security_server_id.sh $TARGET
lxc file push $PROXYSCRIPTDIR/read_db_properties.sh $TARGET
lxc file push $PROXYSCRIPTDIR/restore_db.sh $TARGET
lxc file push $PROXYSCRIPTDIR/restore_xroad_proxy_configuration.sh $TARGET
lxc file push $PROXYSCRIPTDIR/setup_serverconf_db.sh $TARGET
lxc file push $PROXYSCRIPTDIR/verify_internal_configuration.sh $TARGET


