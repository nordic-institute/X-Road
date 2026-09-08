#!/bin/bash
# Thin wrapper for X-Road Liquibase executor fat jar.
# The fat jar handles: logging setup, analytics disabling, JUL-to-SLF4J bridging.
exec java -jar /usr/share/xroad/db/liquibase-executor.jar "$@"
