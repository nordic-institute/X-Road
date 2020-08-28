#!/bin/bash
set -e
export XROAD=$(cd "$(dirname "$0")"; pwd)

./compile_code.sh "$@"

if command -v docker &>/dev/null; then
    docker build -q -t xroad-deb-bionic $XROAD/packages/docker/deb-bionic
    docker build -q -t xroad-rpm $XROAD/packages/docker/rpm
    docker build -q -t xroad-rpm-el8 $XROAD/packages/docker/rpm-el8

    docker run --rm -v $XROAD/..:/workspace -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-deb-bionic /workspace/src/packages/build-deb.sh bionic
    docker run --rm -v $XROAD/..:/workspace -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-rpm /workspace/src/packages/build-rpm.sh
    docker run --rm -v $XROAD/..:/workspace -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-rpm-el8 /workspace/src/packages/build-rpm.sh
else
    echo "Docker not installed, building only .deb packages for this distribution"
    cd $XROAD/packages
    ./build-deb.sh $(lsb_release -sc)
fi
