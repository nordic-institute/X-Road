#!/bin/bash
set -e
export XROAD=$(
  cd "$(dirname "$0")"
  pwd
)

HAS_DOCKER=""
PACKAGE_ONLY=false
BUILD_LOCALLY=true
BUILD_IN_DOCKER=false
BUILD_ALL_PACKAGES=true
BUILD_PACKAGES_FOR_RELEASE=""
# Global variable to determine if text coloring is enabled
isTextColoringEnabled=$(command -v tput >/dev/null && tput setaf 1 &>/dev/null && echo true || echo false)

errorExit() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 1)*** $*(tput sgr0)" 1>&2
  else
    echo "*** $*" 1>&2
  fi
  exit 1
}

warn() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 3)*** $*$(tput sgr0)"
  else
    echo "*** $*"
  fi
}

usage() {
  echo "Usage: $0 [option for $0...] [other options]"
  echo "Options for $0:"
  echo " -p, --package-only    Skip compilation, just build packages"
  echo " -d, --docker-compile  Compile in docker container instead of native gradle build"
  echo " -h, --help            This help text."
  echo " -r release-name       Builds packages of given release only. Supported values are:"
  echo "                          noble, jammy, or focal for debian packages"
  echo "                          rpm-el9, rpm-el8, or rpm for redhat packages"
  echo "                       For example, -r jammy"
  echo "The option for $0, if present, must come fist, before other options."
  echo "Other options are passed on to compile_code.sh"
  test -z "$1" || exit "$1"
}

currentBuildPlan() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 2)Current build plan is:"
  else
    echo "Current build plan is:"
  fi
  if ! $HAS_DOCKER; then
    echo "-- Docker not installed. Building only .deb packages for $(lsb_release -sc) distribution"
  else
    if $BUILD_LOCALLY; then
      echo "-- Compile/build locally"
    fi
    if $BUILD_IN_DOCKER; then
      echo "-- Compile/build in Docker"
    fi
    if [ -n "$BUILD_PACKAGES_FOR_RELEASE" ]; then
      echo "-- Building $BUILD_PACKAGES_FOR_RELEASE packages only"
    else
      echo "-- Building all supported packages"
    fi
  fi
  echo ""
  if $isTextColoringEnabled; then
    echo "$(tput sgr0)"
  fi
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
  warn "Preparing $release image..."
  docker build -q -t "xroad-$release" "$XROAD/packages/docker/$release" || errorExit "Error building $release image."
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

prepareDebianPackagesBuilderImages() {
  if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "noble" ]; then
    buildBuilderImage deb-noble
  fi
  if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "jammy" ]; then
    buildBuilderImage deb-jammy
  fi
  if [ "$(uname)" != "Darwin" ] && { $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "focal" ]; }; then
    buildBuilderImage deb-focal
  fi
}

prepareRedhatPackagesBuilderImages() {
  if [ "$(uname)" != "Darwin" ]; then
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "rpm-el9" ]; then
      buildBuilderImage rpm-el9
    fi
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "rpm-el8" ]; then
      buildBuilderImage rpm-el8
    fi
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "rpm" ]; then
      buildBuilderImage rpm
    fi
  fi
}

buildDebianPackages() {
  if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "noble" ]; then
    runInBuilderImage deb-noble /workspace/src/packages/build-deb.sh noble "$PACKAGE_VERSION" || errorExit "Error building deb-noble packages."
  fi
  if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "jammy" ]; then
    runInBuilderImage deb-jammy /workspace/src/packages/build-deb.sh jammy "$PACKAGE_VERSION" || errorExit "Error building deb-jammy packages."
  fi
  if [ "$(uname)" != "Darwin" ] ; then
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "focal" ]; then
      runInBuilderImage deb-focal /workspace/src/packages/build-deb.sh focal "$PACKAGE_VERSION" || errorExit "Error building deb-focal packages."
    fi
  else
    warn "deb-focal packages cannot be built under MacOS. Skipping.."
  fi
}

buildRedhatPackages() {
  if [ "$(uname)" != "Darwin" ]; then
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "rpm-el9" ]; then
      runInBuilderImage rpm-el9 /workspace/src/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building rpm-el9 packages."
    fi
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "rpm-el8" ]; then
      runInBuilderImage rpm-el8 /workspace/src/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building rpm-el8 packages."
    fi
    if $BUILD_ALL_PACKAGES || [ "$BUILD_PACKAGES_FOR_RELEASE" == "rpm" ]; then
      runInBuilderImage rpm /workspace/src/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building rpm packages."
    fi
  else
    warn "rhel7, rhel8, and rhel9 packages cannot be built under MacOS. Skipping.."
  fi
}

if command -v docker &>/dev/null; then
  HAS_DOCKER=true
fi

for i in "$@"; do
  case "$i" in
      --package-only|-p) shift; PACKAGE_ONLY=true; BUILD_LOCALLY=false; BUILD_IN_DOCKER=false;;
      --docker-compile|-d) shift; PACKAGE_ONLY=false; BUILD_LOCALLY=false; BUILD_IN_DOCKER=true;;
      --help|-h) usage 0;;
      -r) case "$2" in
        noble|jammy|focal) BUILD_ALL_PACKAGES=false; BUILD_PACKAGES_FOR_RELEASE="$2";;
        rpm-el9|rpm-el8|rpm) BUILD_ALL_PACKAGES=false; BUILD_PACKAGES_FOR_RELEASE="$2";;
        *) errorExit "Unknown/unsupported release $2. Exiting..."
        esac;
        shift 2;;
      *) break;;
  esac
done

currentBuildPlan

if $BUILD_LOCALLY; then
  buildLocally "$@"
fi
if $BUILD_IN_DOCKER; then
  buildInDocker "$@"
fi

if [ -n "$HAS_DOCKER" ]; then
  PACKAGE_VERSION="$(date -u -r $(git show -s --format=%ct) +'%Y%m%d%H%M%S')$(git show -s --format=git%h --abbrev=7)"
  echo "Will build packages in docker. Package version: $PACKAGE_VERSION"

  prepareDebianPackagesBuilderImages
  prepareRedhatPackagesBuilderImages
  buildDebianPackages
  buildRedhatPackages

else
  echo "Docker not installed, building only .deb packages for this distribution"
  cd "$XROAD/packages"
  ./build-deb.sh "$(lsb_release -sc)" || errorExit "Error building deb packages."
fi
