#!/bin/bash

#java  -cp /usr/share/xroad/jlib/migration-cli-1.0.jar  org.niis.xroad.configuration.migration.LegacyConfigMigrationCLI $@
java \
-cp /Users/ricardasb/work/xroad/repo/x-road/src/security-server/configuration-service/migration-cli/build/libs/migration-cli-1.0.jar \
org.niis.xroad.configuration.migration.LegacyConfigMigrationCLI $@
