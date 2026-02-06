#!/bin/bash
set -e

# Determine XROAD location
export XROAD=$(
  cd "$(dirname "$0")"
  pwd
)

# Source base script for common utilities and logging functions
source "${XROAD}/../.scripts/base-script.sh"

HAS_DOCKER=""
PACKAGE_ONLY=false
BUILD_LOCALLY=true
BUILD_IN_DOCKER=false
BUILD_PACKAGES_FOR_RELEASES=()

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
  log_info "Current build plan:"
  if ! $HAS_DOCKER; then
    log_kv "  Docker" "Not installed - building only .deb packages for $(lsb_release -sc)" 3 5
  else
    if $BUILD_LOCALLY; then
      log_kv "  Compile/build" "locally" 3 5
    fi
    if $BUILD_IN_DOCKER; then
      log_kv "  Compile/build" "in Docker" 3 5
    fi
    if [ ${#BUILD_PACKAGES_FOR_RELEASES[@]} -eq 0 ]; then
      log_info "  No specific release(s) provided -> Building all supported packages"
      BUILD_PACKAGES_FOR_RELEASES+=("noble" "jammy" "rpm-el9" "rpm-el8")
    fi
    log_kv "  Building packages" "${BUILD_PACKAGES_FOR_RELEASES[*]}" 3 5
  fi
  echo ""
}

buildInDocker() {
  test -n "$HAS_DOCKER" || errorExit "Error, docker is not installed/running."
  log_info "Building in docker..."
  # check if running attached to terminal
  # makes it possible to stop build with Ctrl+C
  if [ -t 1 ]; then OPT="-it"; fi

  docker build -q -t xroad-build --build-arg uid=$(id -u) --build-arg gid=$(id -g) $XROAD/packages/docker-compile || errorExit "Error building build image."
  docker run --rm -v $XROAD/..:/workspace -w /workspace/src -u builder ${OPT} xroad-build bash -c "./compile_code.sh -nodaemon" || errorExit "Error running build of binaries."
}

buildLocally() {
  log_info "Building locally..."
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

prepareLocalRegistry() {
  local container_name="xrd-registry"
  
  # Check if container is already running
  if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
    log_info "Container ${container_name} is already running"
    return 0
  fi
  
  # Check if container exists but is stopped
  if docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
    log_info "Starting existing container ${container_name}"
    docker start "${container_name}"
  else
    log_info "Creating and starting new container ${container_name}"
    docker run -d -p 5555:5000 --name "${container_name}" registry:2
  fi
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
  --package-only | -p)
    shift
    PACKAGE_ONLY=true
    BUILD_LOCALLY=false
    BUILD_IN_DOCKER=false
    ;;
  --docker-compile | -d)
    shift
    PACKAGE_ONLY=false
    BUILD_LOCALLY=false
    BUILD_IN_DOCKER=true
    ;;
  --help | -h) usage 0 ;;
  -r)
    case $2 in
    noble | jammy) BUILD_PACKAGES_FOR_RELEASES+=("$2") ;;
    rpm-el9 | rpm-el8) BUILD_PACKAGES_FOR_RELEASES+=("$2") ;;
    *) errorExit "Unknown/unsupported release $2. Exiting..." ;;
    esac
    shift 2
    ;;
  *) break ;;
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
  log_info "Building packages in docker"
  log_kv "  Package version" "$PACKAGE_VERSION" 3 5

  prepareLocalRegistry
  prepareDebianPackagesBuilderImages
  prepareRedhatPackagesBuilderImages
  buildDebianPackages
  buildRedhatPackages

else
  log_warn "Docker not installed, building only .deb packages for this distribution"
  cd "$XROAD/../deployment/native-packages"
  ./build-deb.sh "$(lsb_release -sc)" || errorExit "Error building deb packages."
fi
