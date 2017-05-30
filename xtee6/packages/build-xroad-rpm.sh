#!/bin/sh
DIR=$(cd "$(dirname $0)" && pwd)
cd $DIR
ROOT=${DIR}/xroad/redhat
RELEASE=1
DATE=$(date --utc --date @$(git show -s --format=%ct || date +%s) +'%Y%m%d%H%M%S')
HASH=$(git show -s --format=git%h || echo 'local')
SNAPSHOT=$DATE$HASH
FILES=${1-'xroad-*.spec'}
CMD=${2-bb}
rm -rf ${ROOT}/RPMS/*

rpmbuild \
    --define "xroad_version 6.7.13" \
    --define "rel $RELEASE" \
    --define "snapshot .$SNAPSHOT" \
    --define "_topdir $ROOT" \
    -${CMD} ${ROOT}/SPECS/${FILES}

