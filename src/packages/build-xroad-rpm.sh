#!/bin/sh

if [[ $1 == "-release" ]] ; then
  RELEASE=1
  FILES="xroad-*.spec"
  CMD="bb"
else
  RELEASE=0
  DATE=$(date --utc --date @$(git show -s --format=%ct || date +%s) +'%Y%m%d%H%M%S')
  HASH=$(git show -s --format=git%h || echo 'local')
  SNAPSHOT=.$DATE$HASH
  FILES=${1-'xroad-*.spec'}
  CMD=${2-bb}
fi

DIR=$(cd "$(dirname $0)" && pwd)
cd $DIR
ROOT=${DIR}/xroad/redhat
rm -rf ${ROOT}/RPMS/*

rpmbuild \
    --define "xroad_version 6.18.0" \
    --define "rel $RELEASE" \
    --define "snapshot $SNAPSHOT" \
    --define "_topdir $ROOT" \
    -${CMD} ${ROOT}/SPECS/${FILES}
