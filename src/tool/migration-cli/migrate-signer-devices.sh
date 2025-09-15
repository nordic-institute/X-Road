#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <full path of devices.ini> <output file>"
    exit 1
fi

java \
-cp $XROAD_HOME/src/tool/migration-cli/build/libs/migration-cli-1.0.jar \
org.niis.xroad.configuration.migration.LegacySignerDevicesMigrationCLI $@
