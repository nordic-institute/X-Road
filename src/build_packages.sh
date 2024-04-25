#!/bin/bash
set -e
export XROAD=$(
  cd "$(dirname "$0")"
  pwd
)

HAS_DOCKER=""

errorExit() {
  echo "*** $*" 1>&2
  exit 1
}

usage() {
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
  docker run --rm -v $XROAD/..:/workspace -w /workspace/src -u builder ${OPT} xroad-build bash -c "./compile_code.sh -nodaemon" || errorExit "Error running build of binaries."
}

buildPasswordStoreInDocker() {
  test -n "$HAS_DOCKER" || errorExit "Error, docker is not installed/running."
  echo "Building passwordstore in docker..."
  # check if running attached to terminal
  # makes it possible to stop build with Ctrl+C
  if [ -t 1 ]; then OPT="-it"; fi

  docker build -q -t xroad-build --build-arg uid=$(id -u) --build-arg gid=$(id -g) $XROAD/packages/docker-compile || errorExit "Error building build image."
  docker run --rm -v $XROAD/..:/workspace -w /workspace/src -u builder ${OPT} xroad-build bash -c "./gradlew make -p signer-protocol" || errorExit "Error running build of binaries."
}

buildLocally() {
  echo "Building locally..."
  cd $XROAD || errorExit "Error 'cd $XROAD'."
  ./compile_code.sh "$@" || errorExit "Error running build of binaries."

  if [ "$(uname)" == "Darwin" ]; then
    echo "MacOS does not support passwordstore compilation. Compiling in docker..."
    buildPasswordStoreInDocker
  fi
}

buildBuilderImage() {
  local release="$1"
  test -n "$release" || errorExit "Error, release not specified."
  echo "Preparing $release image..."
  docker build -q -t "xroad-$release" "$XROAD/packages/docker/deb-jammy" || errorExit "Error building $release image."
}

runInBuilderImage() {
  local release="$1"
  shift;
  test -n "$release" || errorExit "Error, release not specified."
  local image="xroad-$release"

  OPTS=("--rm" "-v" "$XROAD/..:/workspace" "-u" "$(id -u):$(id -g)" "-e" "HOME=/workspace/src/packages")
  # check if running attached to terminal
  # makes it possible to stop build with Ctrl+C
  if [[ -t 1 ]]; then OPTS+=("-it"); fi

  docker run "${OPTS[@]}" "$image" "$@"
}

if command -v docker &>/dev/null; then
  HAS_DOCKER=true
fi

case "$1" in
  --package-only | -p) shift ;;
  --docker-compile | -d)
    shift
    buildInDocker "$@"
    ;;
  --help | -h) usage 0 ;;
  *) buildLocally "$@" ;;
esac

if [ -n "$HAS_DOCKER" ]; then
  PACKAGE_VERSION="$(date -u -r $(git show -s --format=%ct) +'%Y%m%d%H%M%S')$(git show -s --format=git%h --abbrev=7)"
  echo "Will build in docker. Package version: $PACKAGE_VERSION"

  if [ "$(uname)" != "Darwin" ]; then
    echo "Preparing deb-focal image..."
    docker build -q -t xroad-deb-focal "$XROAD/packages/docker/deb-focal" || errorExit "Error building deb-focal image."
  fi

  buildBuilderImage deb-jammy
  buildBuilderImage deb-noble

  if [ "$(uname)" != "Darwin" ]; then
    buildBuilderImage rpm
    buildBuilderImage rpm-el8
    buildBuilderImage rpm-el9
  fi

#  runInBuilderImage deb-jammy /workspace/src/packages/build-deb.sh jammy "$PACKAGE_VERSION" || errorExit "Error building deb-jammy packages."
  runInBuilderImage deb-noble /workspace/src/packages/build-deb.sh noble "$PACKAGE_VERSION" || errorExit "Error building deb-jammy packages."

  if [ "$(uname)" != "Darwin" ]; then
    runInBuilderImage deb-focal /workspace/src/packages/build-deb.sh focal "$PACKAGE_VERSION" || errorExit "Error building deb-focal packages."
    runInBuilderImage rpm /workspace/src/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building rpm packages."
    runInBuilderImage rpm-el8 /workspace/src/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building rpm-el8 packages."
    runInBuilderImage rpm-el9 /workspace/src/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building rpm-el9 packages."
  else
    echo "debian focal, rhel7,rhel8,rhel9 packages cannot be built under MacOS. Skipping.."
  fi

else
  echo "Docker not installed, building only .deb packages for this distribution"
  cd "$XROAD/packages"
  ./build-deb.sh "$(lsb_release -sc)" || errorExit "Error building deb packages."
fi
