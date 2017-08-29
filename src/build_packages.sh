#!/bin/bash

set -e

XROAD=$(cd "$(dirname "$0")"; pwd)

./compile_code.sh "$@"

if command -v docker &>/dev/null; then
    docker build -q -t docker-debbuild $XROAD/packages/docker-debbuild
    docker build -q -t docker-rpmbuild $XROAD/packages/docker-rpmbuild
    docker run --rm -v $XROAD/..:/workspace -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -u $(id -u):$(id -g) -e HOME=/workspace/src/packages docker-debbuild /workspace/src/deb-docker.sh
    docker run --rm -v $XROAD/..:/workspace -v /etc/passwd:/etc/passwd:ro -v /etc/group:/etc/group:ro -u $(id -u):$(id -g) -e HOME=/workspace/src/packages docker-rpmbuild /workspace/src/rpm-docker.sh
else
    echo "Docker not installed, building only .deb packages"
    cd $XROAD/packages/xroad/
    dpkg-buildpackage -tc -b -us -uc
    cd $XROAD/packages/xroad-jetty9/
    dpkg-buildpackage -tc -b -us -uc
fi
