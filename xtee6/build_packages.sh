#!/bin/bash

set -e

GRADLE_HOME="$HOME/gradle-2.4/"
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
PATH=$GRADLE_HOME/bin:$JRUBY_HOME/bin:$JAVA_HOME/bin:$PATH

XROAD=`pwd`

export GRADLE_HOME PATH JAVA_HOME

source $HOME/.rvm/scripts/rvm
rvm use jruby-1.7.22

if [[ -n $1 ]] && [[ $1 == "sonar" ]]; then
    gradle --stacktrace buildAll sonarRunner
else
    gradle --stacktrace buildAll
fi

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi

cd $XROAD/packages/xroad/
dpkg-buildpackage -tc -b -us -uc
cd $XROAD/packages/xroad-jetty9/
dpkg-buildpackage -tc -b -us -uc

