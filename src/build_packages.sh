#!/bin/bash
set -e
export XROAD=$(cd "$(dirname "$0")"; pwd)

errorExit() {
	echo "*** $*" 1>&2
	exit 1
}

if command -v docker &>/dev/null; then
    docker build -q -t xroad-build --build-arg uid=$(id -u) --build-arg gid=$(id -g) $XROAD/packages/docker-compile || errorExit "Error building build image."
    docker build -q -t xroad-deb-bionic $XROAD/packages/docker/deb-bionic || errorExit "Error building pkg deb image."
    docker build -q -t xroad-rpm $XROAD/packages/docker/rpm || errorExit "Error building pkg rpm image."
    docker build -q -t xroad-rpm-el8 $XROAD/packages/docker/rpm-el8 || errorExit "Error building pkg el8-rpm image."

    docker run --rm -v $XROAD/..:/workspace -w /workspace/src -u builder xroad-build  bash -c "./update_ruby_dependencies.sh && ./compile_code.sh -nodaemon" || errorExit "Error running build of binaries."
    docker run --rm -v $XROAD/..:/workspace -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-deb-bionic /workspace/src/packages/build-deb.sh bionic || errorExit "Error building deb"
    docker run --rm -v $XROAD/..:/workspace -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-rpm /workspace/src/packages/build-rpm.sh || errorExit "Error building rpm"
    docker run --rm -v $XROAD/..:/workspace -u $(id -u):$(id -g) -e HOME=/workspace/src/packages xroad-rpm-el8 /workspace/src/packages/build-rpm.sh || errorExit "Error building el8-rpm"
elif command -v dpkg-buildpackage && [ -r ~/.rvm ]; then
    echo "Docker not installed, building only .deb packages for this distribution"
    cd $XROAD || errorExit "Error 'cd $XROAD'."
    ./compile_code.sh "$@" || errorExit "Error running build of binaries."
    cd $XROAD/packages || errorExit "Error 'cd $XROAD/packages'."
    ./build-deb.sh $(lsb_release -sc) || errorExit "Error building '.deb' packages."
else
    errorExit "I cannot build (yet) on your platform. Please consult the file 'BUILD.md' for build requirements."
fi
