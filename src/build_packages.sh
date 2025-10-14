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
BUILD_PACKAGES_FOR_RELEASES=()
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
  echo "Usage: [options] [additional arguments]"
  echo ""
  echo "Options:"
  echo " -p, --package-only     Skip compilation and build only the packages."
  echo " -d, --docker-compile   Compile inside a Docker container instead of native Gradle build."
  echo " -h, --help             Display this help message and exit."
  echo " -r release-name        Specify one or more releases to build packages for. Supported values:"
  echo "                          - noble, jammy   (Debian packages)"
  echo "                          - rpm-el9, rpm-el8 (Red Hat packages)"
  echo "                        Example: -r noble -r rpm-el9"
  echo ""
  echo "Options can be used individually or in combination."
  echo "If provided, options must precede any additional arguments."
  echo "Additional arguments are passed on to compile_code.sh"
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
    if [ ${#BUILD_PACKAGES_FOR_RELEASES[@]} -eq 0 ]; then
      echo "-- No specific release(s) provided -> Building all supported packages"
      BUILD_PACKAGES_FOR_RELEASES+=("noble" "jammy" "rpm-el9" "rpm-el8")
    fi
    echo "-- Building following packages: ${BUILD_PACKAGES_FOR_RELEASES[*]}"
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

buildLocally() {
  echo "Building locally..."
  cd $XROAD || errorExit "Error 'cd $XROAD'."
  ./compile_code.sh "$@" || errorExit "Error running build of binaries."
}

buildBuilderImage() {
  local release="$1"
  test -n "$release" || errorExit "Error, release not specified."

  "$XROAD/../deployment/native-packages/docker/prepare-builder-image.sh" "$release" || errorExit "Error preparing $release image."
}

runInBuilderImage() {
  local release="$1"
  shift
  test -n "$release" || errorExit "Error, release not specified."

  # Use same image name as prepare-builder-image.sh
  local registry="${IMAGE_REGISTRY:-localhost:5555}"
  local tag="${IMAGE_TAG:-latest}"
  local image="${registry}/package-builder-${release}:${tag}"

  OPTS=("--rm" "-v" "$XROAD/..:/workspace" "-u" "$(id -u):$(id -g)" "-e" "HOME=/workspace/deployment/native-packages")
  # check if running attached to terminal
  # makes it possible to stop build with Ctrl+C
  if [[ -t 1 ]]; then OPTS+=("-it"); fi

  docker run "${OPTS[@]}" "$image" "$@"
}

prepareDebianPackagesBuilderImages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "noble" || "$release" == "jammy" ]]; then
      buildBuilderImage "deb-$release"
    fi
  done
}

prepareRedhatPackagesBuilderImages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "rpm-el9" || "$release" == "rpm-el8" ]]; then
      buildBuilderImage "$release"
    fi
  done
}

buildDebianPackages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "noble" || "$release" == "jammy" ]]; then
      runInBuilderImage "deb-$release" /workspace/deployment/native-packages/build-deb.sh "$release" "$PACKAGE_VERSION" || errorExit "Error building deb-$release packages."
    fi
  done
}

buildRedhatPackages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "rpm-el9" || "$release" == "rpm-el8" ]]; then
      runInBuilderImage "$release" /workspace/deployment/native-packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building $release packages."
    fi
  done
}

if command -v docker &>/dev/null; then
  HAS_DOCKER=true
fi

while [[ $# -gt 0 ]]; do
  case $1 in
      --package-only|-p) shift; PACKAGE_ONLY=true; BUILD_LOCALLY=false; BUILD_IN_DOCKER=false;;
      --docker-compile|-d) shift; PACKAGE_ONLY=false; BUILD_LOCALLY=false; BUILD_IN_DOCKER=true;;
      --help|-h) usage 0;;
      -r) case $2 in
        noble|jammy) BUILD_PACKAGES_FOR_RELEASES+=("$2");;
        rpm-el9|rpm-el8) BUILD_PACKAGES_FOR_RELEASES+=("$2");;
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
