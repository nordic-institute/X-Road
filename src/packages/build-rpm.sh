#!/bin/bash
##########################################################################################
# This build script takes 3 parameters.
# The first parameter which is the release version 7 or 8 is mandatory.
# The second parameter (optional) is -release or the name of the spec file to build.
# The third parameter (optional) is the command to execute in the rpmbuild, default is bb
##########################################################################################
set -e

VERSION=6.24.0

# First parameter
case "$1" in
    7)
        REDHAT_VERSION=7
    ;;
    8)
        REDHAT_VERSION=8
    ;;
    *)
        echo "Unknown distribution $1"
    ;;
esac

# Second Parameter
if [[ $2 == "-release" ]] ; then
    RELEASE=1
    FILES="xroad-*.spec"
    CMD="bb"
else
    RELEASE=0
    DATE="$(date --utc --date @$(git show -s --format=%ct || date +%s) +'%Y%m%d%H%M%S')"
    HASH="$(git show -s --format=git%h --abbrev=7 || echo 'local')"
    SNAPSHOT=.$DATE$HASH
    FILES=${2-'xroad-*.spec'}
    # Third parameter
    CMD=${3-bb}
fi

########################################
# build process begins
########################################
DIR=$(cd "$(dirname $0)" && pwd)
cd "$DIR"

# Copy the SPEC and SOURCES to the build folder
mkdir -p build/xroad/redhat/${REDHAT_VERSION}
cp -a src/xroad/redhat build/xroad
mkdir -p build/xroad-jetty9
cp -a src/xroad-jetty9/redhat build/xroad-jetty9/
# Clean out rpm packages before building
find build/ -name "*.el${REDHAT_VERSION}*.rpm" | xargs rm -f

#############
# build xroad rpms
#############
ROOT="${DIR}/build/xroad/redhat/${REDHAT_VERSION}"
rpmbuild \
    --define "xroad_version $VERSION" \
    --define "rel $RELEASE" \
    --define "snapshot $SNAPSHOT" \
    --define "_topdir $ROOT" \
    --define "srcdir $DIR/src/xroad" \
    -${CMD} "${ROOT}/SPECS/"${FILES}

#############
# build xroad-jetty rpms
#############
ROOT="${DIR}/build/xroad-jetty9/redhat/${REDHAT_VERSION}"
./download_jetty.sh
rpmbuild \
    --define "xroad_version $VERSION" \
    --define "jetty $DIR/build/jetty9" \
    --define "rel $RELEASE" \
    --define "snapshot $SNAPSHOT" \
    --define "_topdir $ROOT" \
    --define "srcdir $DIR/src/xroad-jetty9" \
    -bb "$ROOT/SPECS/"${FILES}
