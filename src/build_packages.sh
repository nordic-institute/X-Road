#!/bin/bash
set -e
export XROAD=$(cd "$(dirname "$0")"; pwd)

if [[ ! " $* " =~ " --packageonly " ]]; then
  ./compile_code.sh "$@"
fi

if command -v docker &>/dev/null; then
    docker build -q -t xroad-deb-bionic "$XROAD/packages/docker/deb-bionic"
    docker build -q -t xroad-deb-focal "$XROAD/packages/docker/deb-focal"
    docker build -q -t xroad-rpm "$XROAD/packages/docker/rpm"
    docker build -q -t xroad-rpm-el8 "$XROAD/packages/docker/rpm-el8"

    OPTS=("--rm" "-v" "$XROAD/..:/workspace" "-u" "$(id -u):$(id -g)" "-e" "HOME=/workspace/src/packages")
    # check if running attached to terminal
    # makes it possible to stop build with Ctrl+C
    if [[ -t 1 ]]; then OPTS+=("-it"); fi

    docker run "${OPTS[@]}" xroad-deb-bionic /workspace/src/packages/build-deb.sh bionic
    docker run "${OPTS[@]}" xroad-deb-focal /workspace/src/packages/build-deb.sh focal
    docker run "${OPTS[@]}" xroad-rpm /workspace/src/packages/build-rpm.sh
    docker run "${OPTS[@]}" xroad-rpm-el8 /workspace/src/packages/build-rpm.sh
else
    echo "Docker not installed, building only .deb packages for this distribution"
    cd "$XROAD/packages"
    ./build-deb.sh "$(lsb_release -sc)"
fi
