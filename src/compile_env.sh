#!/bin/bash
set -e

if [ -z "$XROAD_HOME" ]
then
    XROAD_HOME=$(cd "$(dirname "$0")"; pwd)
fi

JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
PATH="$JAVA_HOME/bin:$PATH"

export PATH JAVA_HOME XROAD_HOME

