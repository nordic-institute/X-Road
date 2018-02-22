#!/bin/sh
set -e

md5 () {
    md5sum $1 | cut -f1 -d ' '
}

if [[ $1 == "-release" ]] ; then
  RELEASE=1
else
  RELEASE=0
  DATE=$(date --utc --date @$(git show -s --format=%ct || date +%s) +'%Y%m%d%H%M%S')
  HASH=$(git show -s --format=git%h || echo 'local')
  SNAPSHOT=.$DATE$HASH
fi

DIR=$(cd "$(dirname $0)" && pwd)
cd $DIR
set -e
JETTY=$(head -1 xroad-jetty9/jetty.url)

ROOT=${DIR}/xroad-jetty9/redhat
mkdir -p $ROOT/SOURCES
cd $ROOT/SOURCES
md5a=$(cat jetty.md5 || echo X)
md5b=$(md5 $(basename $JETTY) || echo Y)
if [[ ! -f $(basename $JETTY) || "$md5a" != "$md5b" ]]; then
    curl -sLO $JETTY
    md5 $(basename $JETTY) > jetty.md5
fi
cd ..
rpmbuild \
    --define "xroad_version 6.18.0" \
    --define "jetty $JETTY" \
    --define "rel $RELEASE" \
    --define "snapshot $SNAPSHOT" \
    --define "_topdir $ROOT" \
    -bb SPECS/xroad-jetty9.spec
