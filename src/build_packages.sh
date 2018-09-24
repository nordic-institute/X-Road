#!/bin/bash

set -e

XROAD=$(cd "$(dirname "$0")"; pwd)

#./compile_code.sh "$@"

if command -v docker &>/dev/null; then
    docker build -q -t xroad-deb-bionic $XROAD/packages/docker/deb-bionic
    docker build -q -t xroad-deb-trusty $XROAD/packages/docker/deb-trusty
    docker build -q -t xroad-rpm $XROAD/packages/docker/rpm

    docker run --rm -v $XROAD/..:/workspace -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-deb-bionic /workspace/src/packages/build-deb.sh bionic
    docker run --rm -v $XROAD/..:/workspace -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-deb-bionic /workspace/src/packages/build-deb.sh trusty
    docker run --rm -v $XROAD/..:/workspace -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -u $(id -u):$(id -g) -e HOME=/workspace/src/packages docker-rpmbuild /workspace/src/packages/build-rpm.sh
else
    echo "Docker not installed, building only .deb packages for this distribution"
    cd $XROAD/packages
    build-deb.sh $(lsb_release -sc)
fi
