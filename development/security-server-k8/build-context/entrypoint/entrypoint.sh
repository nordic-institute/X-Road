#!/bin/bash

JVM_OPTS="-Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n"

exec java $JVM_OPTS -jar /opt/app/application.jar