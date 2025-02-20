#!/bin/bash
set -e

if [ -z "$XROAD_HOME" ]
then
    XROAD_HOME=$(cd "$(dirname "$0")"; pwd)
fi

if [ -z "$JAVA_HOME" ]
then
  JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which javac)")")")
  PATH="$JAVA_HOME/bin:$PATH"
fi

export PATH JAVA_HOME XROAD_HOME
