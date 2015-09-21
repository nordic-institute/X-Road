#!/bin/bash

. /etc/xroad/services/global.conf

DIR=/tmp/$$
mkdir $DIR
unzip -q /usr/share/xroad/jlib/webapps/center-ui.war -d $DIR
cd $DIR/WEB-INF
cp -r vendor/engines/center-common/db/ .
GEM_HOME=$DIR/WEB-INF/gems/ RAILS_ENV=production java -cp "lib/*" org.jruby.Main -S rake $*
ret=$?

rm -rf $DIR

if [[ $ret -ne 0 ]]
 then
 exit $ret
fi

