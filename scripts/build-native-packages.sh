#!/bin/bash
set -e

# Determine XROAD location
export XROAD=$(
  cd "$(dirname "$0")"
  pwd
)

# Source base script for common utilities and logging functions
source "${XROAD}/lib/base-script.sh"

HAS_DOCKER=""
PACKAGE_ONLY=false
BUILD_LOCALLY=true
BUILD_PACKAGES_FOR_RELEASES=()

usage() {
  echo "Usage: [options] [additional arguments]"
  echo ""
  echo "Options:"
  echo " -p, --package-only     Skip compilation and build only the packages."
  echo " -d, --docker-compile   Compile inside a Docker container instead of native Gradle build."
  echo " -h, --help             Display this help message and exit."
  echo " -r release-name        Specify one or more releases to build packages for. Supported values:"
  echo "                          - resolute, noble   (Debian packages)"
  echo "                          - rpm-el9, rpm-el10 (Red Hat packages)"
  echo "                        Example: -r resolute -r rpm-el9"
  echo ""
  echo "Options can be used individually or in combination."
  echo "If provided, options must precede any additional arguments."
  echo "Additional arguments are passed on to compile-all.sh"
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
    if [ ${#BUILD_PACKAGES_FOR_RELEASES[@]} -eq 0 ]; then
      log_info "  No specific release(s) provided -> Building all supported packages"
      BUILD_PACKAGES_FOR_RELEASES+=("resolute" "noble" "rpm-el9" "rpm-el10")
    fi
    log_kv "  Building packages" "${BUILD_PACKAGES_FOR_RELEASES[*]}" 3 5
  fi
  echo ""
}

buildLocally() {
  log_info "Building locally..."
  "${XROAD}/compile-all.sh" "$@" || errorExit "Error running build of binaries."
}

buildBuilderImage() {
  local release="$1"
  test -n "$release" || errorExit "Error, release not specified."

  "${XROAD}/images/build-builder.sh" "$release" || errorExit "Error preparing $release image."
}

runInBuilderImage() {
  local release="$1"
  shift
  test -n "$release" || errorExit "Error, release not specified."

  # Use same image name as build-builder.sh
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
    if [[ "$release" == "resolute" || "$release" == "noble" ]]; then
      buildBuilderImage "deb-$release"
    fi
  done
}

prepareRedhatPackagesBuilderImages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "rpm-el9" || "$release" == "rpm-el10" ]]; then
      buildBuilderImage "$release"
    fi
  done
}

buildDebianPackages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "resolute" || "$release" == "noble" ]]; then
      runInBuilderImage "deb-$release" /workspace/scripts/packages/build-deb.sh "$release" "$PACKAGE_VERSION" || errorExit "Error building deb-$release packages."
    fi
  done
}

buildRedhatPackages() {
  for release in "${BUILD_PACKAGES_FOR_RELEASES[@]}"; do
    if [[ "$release" == "rpm-el9" || "$release" == "rpm-el10" ]]; then
      runInBuilderImage "$release" /workspace/scripts/packages/build-rpm.sh "$PACKAGE_VERSION" || errorExit "Error building $release packages."
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
    ;;
  --docker-compile | -d)
    shift
    ;;
  --help | -h) usage 0 ;;
  -r)
    case $2 in
    resolute | noble) BUILD_PACKAGES_FOR_RELEASES+=("$2") ;;
    rpm-el9 | rpm-el10) BUILD_PACKAGES_FOR_RELEASES+=("$2") ;;
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

if [ -n "$HAS_DOCKER" ]; then
  PACKAGE_VERSION="$(date -u -r $(git show -s --format=%ct) +'%Y%m%d%H%M%S')$(git show -s --format=git%h --abbrev=7)"
  log_info "Building packages in docker"
  log_kv "  Package version" "$PACKAGE_VERSION" 3 5

  ensure_local_registry
  prepareDebianPackagesBuilderImages
  prepareRedhatPackagesBuilderImages
  buildDebianPackages
  buildRedhatPackages

else
  log_warn "Docker not installed, building only .deb packages for this distribution"
  "${XROAD}/packages/build-deb.sh" "$(lsb_release -sc)" || errorExit "Error building deb packages."
fi
