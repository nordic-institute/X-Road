#!/bin/bash
set -e

VERSION=6.25.2

if [[ $1 == "-release" ]] ; then
  RELEASE=1
  FILES="xroad-*.spec"
  CMD="bb"
else
  RELEASE=0
  DATE="$(date --utc --date @$(git show -s --format=%ct || date +%s) +'%Y%m%d%H%M%S')"
  HASH="$(git show -s --format=git%h --abbrev=7 || echo 'local')"
  SNAPSHOT=.$DATE$HASH
  FILES=${1-'xroad-*.spec'}
  CMD=${2-bb}
fi

DIR=$(cd "$(dirname $0)" && pwd)
cd "$DIR"

mkdir -p build/xroad/redhat
cp -a src/xroad/redhat build/xroad
mkdir -p build/xroad-jetty9
cp -a src/xroad-jetty9/redhat build/xroad-jetty9/

if [[ -z "$SNAPSHOT" ]]; then
  macro_snapshot=()
else
  macro_snapshot=(--define "snapshot $SNAPSHOT")
fi

ROOT=${DIR}/build/xroad/redhat
rpmbuild \
    --define "xroad_version $VERSION" \
    --define "rel $RELEASE" \
    "${macro_snapshot[@]}" \
    --define "_topdir $ROOT" \
    --define "srcdir $DIR/src/xroad" \
    --define "_rpmdir ${DIR}/build/rhel/%{rhel}" \
    -${CMD} "${ROOT}/SPECS/"${FILES}

# build jetty rpms
ROOT="${DIR}/build/xroad-jetty9/redhat"
./download_jetty.sh
rpmbuild \
    --define "xroad_version $VERSION" \
    --define "jetty $DIR/build/jetty9" \
    --define "rel $RELEASE" \
    "${macro_snapshot[@]}" \
    --define "_topdir $ROOT" \
    --define "srcdir $DIR/src/xroad-jetty9" \
    --define "_rpmdir ${DIR}/build/rhel/%{rhel}" \
    -bb "$ROOT/SPECS/"${FILES}
