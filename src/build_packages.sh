#!/bin/bash
set -e

echo "build_packages.sh parameters: $@"


export XROAD=$(cd "$(dirname "$0")"; pwd)

HAS_DOCKER=""

errorExit() {
	echo "*** $*" 1>&2
	exit 1
}

buildInDocker() {
    exit 1
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

usage () {
  echo >&2 "$@"
  echo
  echo "Usage: $0 [options for $0...] [other options]"
  echo "Options for $0:"
  echo " -p, --packageonly     Skip compilation, just build packages'"
  echo " -d, --docker-compile  Compile in docker container instead of native gradle build"
  echo " -h, --help            This help text."
  echo "Other options are passed on to compile_code.sh"

  exit 2
}

if command -v docker &>/dev/null; then
    HAS_DOCKER=true
fi

for i in "$@"; do
shift
echo "...checking parameter $i"
case "$i" in
    "--packageonly"|"-p")
        PACKAGE_ONLY=1
        echo "set package_only"
        continue
        ;;
    "--docker-compile"|"-d")
        DOCKER_BUILD=1
        echo "set docker build"
        continue
        ;;
    "--help"|"-h")
         usage
        ;;
    *) set -- "$@" "$i";;
esac
echo "params now $@"
echo "PACKAGE_ONLY=$PACKAGE_ONLY DOCKER_BUILD=$DOCKER_BUILD"
done

echo "buildmode detection done, PACKAGE_ONLY=$PACKAGE_ONLY DOCKER_BUILD=$DOCKER_BUILD"
echo "build_packages.sh parameters now: $@"

if [[ -n "$PACKAGE_ONLY" && -n "$DOCKER_BUILD" ]]; then
    echo "Can't use both packageonly and docker-compile options"
    usage
fi

echo "checking parameter $1"

case "$1" in
    --packageonly|-p) echo "packageonly";;
    --docker-build|-d) echo "buildInDocker" && echo "second";;
    *) echo "local-build";;
esac


echo "build_packages.sh parameters after buildmode: $@"
exit 1

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
