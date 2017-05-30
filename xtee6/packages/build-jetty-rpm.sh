#!/bin/sh
set -e

md5 () {
    md5sum $1 | cut -f1 -d ' '
}

DIR=$(cd "$(dirname $0)" && pwd)
cd $DIR
set -e
JETTY=$(head -1 xroad-jetty9/jetty.url)
RELEASE=1
DATE=$(date --utc --date @$(git show -s --format=%ct || date +%s) +'%Y%m%d%H%M%S')
HASH=$(git show -s --format=git%h || echo 'local')
SNAPSHOT=$DATE$HASH
ROOT=${DIR}/xroad-jetty9/redhat
mkdir -p $ROOT/SOURCES
cd $ROOT/SOURCES
md5a=$(cat logging.mod.md5 || echo X)
md5b=$(md5 logging.mod || echo Y)
if [[ ! -f logging.mod || "$md5a" != "$md5b" ]]; then
    curl -sLO "https://raw.githubusercontent.com/jetty-project/logging-modules/master/logback/logging.mod"
    md5 logging.mod > logging.mod.md5
fi
md5a=$(cat jetty.md5 || echo X)
md5b=$(md5 $(basename $JETTY) || echo Y)
if [[ ! -f $(basename $JETTY) || "$md5a" != "$md5b" ]]; then
    curl -sLO $JETTY
    md5 $(basename $JETTY) > jetty.md5
fi
cd ..
rpmbuild \
    --define "xroad_version 6.7.13" \
    --define "jetty $JETTY" \
    --define "rel $RELEASE" \
    --define "snapshot .$SNAPSHOT" \
    --define "_topdir $ROOT" \
    -bb SPECS/xroad-jetty9.spec

