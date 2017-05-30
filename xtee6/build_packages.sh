#!/bin/bash
set -e

JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
PATH=$JAVA_HOME/bin:$PATH
XROAD=`pwd`

export GRADLE_HOME PATH JAVA_HOME

source $HOME/.rvm/scripts/rvm
rvm use jruby-1.7.25

if [[ -n $1 ]] && [[ $1 == "sonar" ]]; then
    ./gradlew --stacktrace buildAll runProxyTest dependencyCheck sonarqube
else
    ./gradlew --stacktrace buildAll runProxyTest
fi

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi

cd $XROAD/packages/xroad/
dpkg-buildpackage -tc -b -us -uc
cd $XROAD/packages/xroad-jetty9/
dpkg-buildpackage -tc -b -us -uc

