#!/bin/bash
set -e

VERSION=7.5.0
LAST_SUPPORTED_VERSION=7.3.0

if [[ $1 == "-release" ]] ; then
  RELEASE=1
  FILES="xroad-*.spec"
  CMD="bb"
else
  RELEASE=0

  # version was not given, use empty
  if [ -z "$1" ]; then
    DATE="$(date --utc --date @"$(git show -s --format=%ct || date +%s)" +'%Y%m%d%H%M%S')"
    HASH="$(git show -s --format=git%h --abbrev=7 || echo 'local')"
    SNAPSHOT=.$DATE$HASH
    FILES=${1-'xroad-*.spec'}
  else
    SNAPSHOT=."$1"
    FILES='xroad-*.spec'
  fi

  CMD=${2-bb}
fi

echo "using packageVersion $SNAPSHOT"

DIR=$(cd "$(dirname "$0")" && pwd)
cd "$DIR"

mkdir -p build/xroad/redhat
cp -a src/xroad/redhat build/xroad

if [[ -z "$SNAPSHOT" ]]; then
  macro_snapshot=()
  compress=w9.gzdio
else
  macro_snapshot=(--define "snapshot $SNAPSHOT")
  compress=w1.gzdio
fi

ROOT=${DIR}/build/xroad/redhat
rpmbuild \
    --define "last_supported_version $LAST_SUPPORTED_VERSION" \
    --define "xroad_version $VERSION" \
    --define "rel $RELEASE" \
    "${macro_snapshot[@]}" \
    --define "_topdir $ROOT" \
    --define "srcdir $DIR/src/xroad" \
    --define "_rpmdir ${DIR}/build/rhel/%{rhel}" \
    --define "_binary_payload $compress" \
    -"${CMD}" "${ROOT}/SPECS/"${FILES}

