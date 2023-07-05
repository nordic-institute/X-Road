#!/bin/bash

if [ ! -n "${LIQUIBASE_HOME+x}" ]; then
  echo "Liquibase Home is not set."

  ## resolve links - $0 may be a symlink
  PRG="$0"
  while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
    else
    PRG=`dirname "$PRG"`"/$link"
    fi
  done


  LIQUIBASE_HOME=`dirname "$PRG"`

  # make it fully qualified
  LIQUIBASE_HOME=`cd "$LIQUIBASE_HOME" && pwd`
fi

CLASSPATH=/usr/share/xroad/db/liquibase-core.jar
# add any JVM options here
JAVA_OPTS="${JAVA_OPTS-}"

echo "Liquibase Home: $LIQUIBASE_HOME"
java -cp "$CLASSPATH" $JAVA_OPTS liquibase.integration.commandline.Main ${1+"$@"}


