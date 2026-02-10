#!/bin/bash
set -e

VERSION=8.0.0.beta1
LAST_SUPPORTED_VERSION=7.8.0

# Global variable to determine if text coloring is enabled
isTextColoringEnabled=$(command -v tput >/dev/null && tput setaf 1 &>/dev/null && echo true || echo false)

warn() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 3)*** $*$(tput sgr0)"
  else
    echo "*** $*"
  fi
}

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

warn "using packageVersion $SNAPSHOT"

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

HOST_ARCH="$(uname -m)"
if [[ "$HOST_ARCH" == "x86_64" ]]; then
    CROSS_TARGET="aarch64"
else
    CROSS_TARGET="x86_64"
fi

# Pass 1: build all packages for host arch (noarch + signer for host arch)
warn "Pass 1: building all packages for $HOST_ARCH..."
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
warn "Pass 1: $HOST_ARCH build finished."

# Pass 2: build xroad-signer for the other architecture
warn "Pass 2: cross-building xroad-signer for $CROSS_TARGET..."
rpmbuild \
    --define "last_supported_version $LAST_SUPPORTED_VERSION" \
    --define "xroad_version $VERSION" \
    --define "rel $RELEASE" \
    "${macro_snapshot[@]}" \
    --define "_topdir $ROOT" \
    --define "srcdir $DIR/src/xroad" \
    --define "_rpmdir ${DIR}/build/rhel/%{rhel}" \
    --define "_binary_payload $compress" \
    --target "$CROSS_TARGET" \
    -bb "${ROOT}/SPECS/xroad-signer.spec"
warn "Pass 2: $CROSS_TARGET cross-build finished."

