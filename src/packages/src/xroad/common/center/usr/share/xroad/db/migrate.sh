#!/bin/bash
. /etc/xroad/services/global.conf

DIR=$(mktemp -d) || exit 1
trap 'rc=$?; rm --preserve-root -rf $DIR; exit $rc' EXIT

unzip -q /usr/share/xroad/jlib/webapps/center-ui.war -d $DIR
cd $DIR/WEB-INF
mkdir -p db/migrate
cp -r vendor/engines/center-common/db/migrate/* db/migrate/
GEM_HOME=$DIR/WEB-INF/gems/ RAILS_ENV=production java -Dlogback.configurationFile=/usr/share/xroad/db/logback.xml -cp "lib/*" org.jruby.Main -S rake $*
