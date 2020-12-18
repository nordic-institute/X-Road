#!/bin/bash
set -e
export XROAD=$(cd "$(dirname "$0")"; pwd)

HAS_DOCKER=""

errorExit() {
	echo "*** $*" 1>&2
	exit 1
}

buildInDocker() {
    test -n "$HAS_DOCKER" || errorExit "Error, docker is not installed/running."
    echo "Building in docker..."
    docker build -q -t xroad-build --build-arg uid=$(id -u) --build-arg gid=$(id -g) $XROAD/packages/docker-compile || errorExit "Error building build image."
    docker run --rm -v $XROAD/..:/workspace -w /workspace/src -u builder xroad-build  bash -c "./update_ruby_dependencies.sh && ./compile_code.sh -nodaemon" || errorExit "Error running build of binaries."
}

buildLocally() {
    echo "Building locally..."
    cd $XROAD || errorExit "Error 'cd $XROAD'."
    ./compile_code.sh "$@" || errorExit "Error running build of binaries."
}

if command -v docker &>/dev/null; then
    HAS_DOCKER=true
fi

case "$1" in
    --packageonly|-p) shift;;
    --docker-build|-d) shift; buildInDocker "$@";;
    *) buildLocally "$@";;
esac


if [ -n "$HAS_DOCKER" ]; then
    docker build -q -t xroad-deb-bionic "$XROAD/packages/docker/deb-bionic" || errorExit "Error building deb-bionic image."
    docker build -q -t xroad-deb-focal "$XROAD/packages/docker/deb-focal" || errorExit "Error building deb-focal image."
    docker build -q -t xroad-rpm "$XROAD/packages/docker/rpm" || errorExit "Error building rpm image."
    docker build -q -t xroad-rpm-el8 "$XROAD/packages/docker/rpm-el8" || errorExit "Error building rpm-el8 image."

    OPTS=("--rm" "-v" "$XROAD/..:/workspace" "-u" "$(id -u):$(id -g)" "-e" "HOME=/workspace/src/packages")
    # check if running attached to terminal
    # makes it possible to stop build with Ctrl+C
    if [[ -t 1 ]]; then OPTS+=("-it"); fi

    docker run "${OPTS[@]}" xroad-deb-bionic /workspace/src/packages/build-deb.sh bionic || errorExit "Error building deb-bionic packages."
    docker run "${OPTS[@]}" xroad-deb-focal /workspace/src/packages/build-deb.sh focal || errorExit "Error building deb-focal packages."
    docker run "${OPTS[@]}" xroad-rpm /workspace/src/packages/build-rpm.sh || errorExit "Error building rpm packages."
    docker run "${OPTS[@]}" xroad-rpm-el8 /workspace/src/packages/build-rpm.sh || errorExit "Error building rpm-el8 packages."
else
    echo "Docker not installed, building only .deb packages for this distribution"
    cd "$XROAD/packages"
    ./build-deb.sh "$(lsb_release -sc)" || errorExit "Error building deb packages."
fi
