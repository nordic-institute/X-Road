#!/bin/bash

set -e


GRADLE_HOME="$HOME/gradle-2.1/"
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64/
PATH=$GRADLE_HOME/bin:$JRUBY_HOME/bin:$JAVA_HOME/bin:$PATH

XROAD=`pwd`

export GRADLE_HOME PATH JAVA_HOME

source $HOME/.rvm/scripts/rvm
rvm use jruby

gradle buildAll
cd $XROAD/packages/xroad/
dpkg-buildpackage -tc -b -us -uc
cd $XROAD/packages/xroad-jetty9/ 
dpkg-buildpackage -tc -b -us -uc
