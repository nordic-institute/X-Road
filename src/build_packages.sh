#!/bin/bash
set -e
export XROAD=$(cd "$(dirname "$0")"; pwd)

HAS_DOCKER=""

errorExit() {
    echo "*** $*" 1>&2
    exit 1
}

usage () {
    echo "Usage: $0 [option for $0...] [other options]"
    echo "Options for $0:"
    echo " -p, --package-only    Skip compilation, just build packages"
    echo " -d, --docker-compile  Compile in docker container instead of native gradle build"
    echo " -h, --help            This help text."
    echo "The option for $0, if present, must come fist, before other options."
    echo "Other options are passed on to compile_code.sh"
    test -z "$1" || exit "$1"
}

buildInDocker() {
    test -n "$HAS_DOCKER" || errorExit "Error, docker is not installed/running."
    echo "Building in docker..."
    # check if running attached to terminal
    # makes it possible to stop build with Ctrl+C
    if [ -t 1 ]; then OPT="-it"; fi

    docker build -q -t xroad-build --build-arg uid=$(id -u) --build-arg gid=$(id -g) $XROAD/packages/docker-compile || errorExit "Error building build image."
    docker run --rm -v $XROAD/..:/workspace -w /workspace/src -u builder ${OPT} xroad-build  bash -c "./compile_code.sh -nodaemon" || errorExit "Error running build of binaries."
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
    --package-only|-p) shift;;
    --docker-compile|-d) shift; buildInDocker "$@";;
    --help|-h) usage 0;;
    *) buildLocally "$@";;
esac

if [ -n "$HAS_DOCKER" ]; then
  echo "IN docker"
    docker build -q -t xroad-deb-focal "$XROAD/packages/docker/deb-focal" || errorExit "Error building deb-focal image."
    docker build -q -t xroad-deb-jammy "$XROAD/packages/docker/deb-jammy" || errorExit "Error building deb-jammy image."
    docker build -q -t xroad-rpm "$XROAD/packages/docker/rpm" || errorExit "Error building rpm image."
    docker build -q -t xroad-rpm-el8 "$XROAD/packages/docker/rpm-el8" || errorExit "Error building rpm-el8 image."

    OPTS=("--rm" "-v" "$XROAD/..:/workspace" "-u" "$(id -u):$(id -g)" "-e" "HOME=/workspace/src/packages")
    # check if running attached to terminal
    # makes it possible to stop build with Ctrl+C
    if [[ -t 1 ]]; then OPTS+=("-it"); fi

    docker run "${OPTS[@]}" xroad-deb-focal /workspace/src/packages/build-deb.sh focal -release || errorExit "Error building deb-focal packages."
    docker run "${OPTS[@]}" xroad-deb-jammy /workspace/src/packages/build-deb.sh jammy -release || errorExit "Error building deb-jammy packages."
    docker run "${OPTS[@]}" xroad-rpm /workspace/src/packages/build-rpm.sh -release || errorExit "Error building rpm packages."
    docker run "${OPTS[@]}" xroad-rpm-el8 /workspace/src/packages/build-rpm.sh -release || errorExit "Error building rpm-el8 packages."
else
    echo "Docker not installed, building only .deb packages for this distribution"
    cd "$XROAD/packages"
    ./build-deb.sh "$(lsb_release -sc)" -release || errorExit "Error building deb packages."
fi
