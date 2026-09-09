#!/bin/bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: docker-build.sh [--target=full] [--no-cache] [--no-mirror]
                        [--packages-path=DIR] [version] [tag] [repo] [dist] [repo_key]

  --target=full        Build only the main image. With no --target, all
                       images (main, country variants, kubernetesBalancer)
                       are built, unchanged from before.
  --no-cache           Pass --no-cache to every docker build.
  --no-mirror          Skip the package-mirror build args even if the
                       XROAD_MIRROR_* environment variables are set.
  --packages-path=DIR  Build the main image from a local directory of
                       tree-built Ubuntu DEBs instead of an X-Road apt
                       repository (PACKAGE_SOURCE=internal, DIR bind-mounted
                       as the "packages" build context). Country variants and
                       kubernetesBalancer images, which do not install X-Road
                       packages of their own, are unaffected. DIR must exist
                       and contain at least one file.
  -h, --help           Show this help.
USAGE
}

no_cache=""
no_mirror=""
target=""
packages_path=""
args_to_keep=()
for i in "$@" ; do
    if [[ $i == "--no-cache" ]] ; then
        no_cache="--no-cache"
    elif [[ $i == "--no-mirror" ]] ; then
        no_mirror="true"
    elif [[ $i == "--target="* ]] ; then
        target="${i#--target=}"
    elif [[ $i == "--packages-path="* ]] ; then
        packages_path="${i#--packages-path=}"
    elif [[ $i == "--help" || $i == "-h" ]] ; then
        usage
        exit 0
    else
        args_to_keep+=("$i")
    fi
done
set -- "${args_to_keep[@]+"${args_to_keep[@]}"}"

case "$target" in
    ""|full) ;;
    *)
        echo "Unknown --target: $target (expected 'full')" >&2
        usage >&2
        exit 1
        ;;
esac
build_all=true
[[ -n "$target" ]] && build_all=false

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" >&/dev/null && pwd)"
version="${1:-8.0.0}"
tag="${2:-xroad-security-server-sidecar}"
repo="${3-}"
dist="${4-}"
repo_key="${5-}"

# Prepare internal-package build args. The Dockerfile bind-mounts the "packages"
# build context unconditionally, so external builds get an empty stand-in directory.
if [[ -n "$packages_path" ]]; then
  if [[ ! -d "$packages_path" ]] || [[ -z "$(ls -A "$packages_path" 2>/dev/null)" ]]; then
    echo "Packages path '$packages_path' does not exist or is empty." >&2
    echo "Build tree-built Ubuntu DEBs first, e.g. scripts/packages/build-deb.sh resolute." >&2
    exit 1
  fi
  PACKAGE_BUILD_ARGS=(--build-arg "PACKAGE_SOURCE=internal" --build-context "packages=$packages_path")
else
  empty_packages_dir="$(mktemp -d)"
  trap 'rm -rf "$empty_packages_dir"' EXIT
  PACKAGE_BUILD_ARGS=(--build-context "packages=$empty_packages_dir")
fi

# Prepare mirror build args (unless --no-mirror flag is set)
MIRROR_BUILD_ARGS=(--build-context "mirror-scripts=$dir/../deployment/.scripts")

# Scripts shared with the native packages, kept out of the sidecar build context
SHARED_SCRIPT_BUILD_ARGS=(
  --build-context "secret-store-scripts=$dir/../deployment/native-packages/src/xroad/common/secret-store-local/usr/share/xroad/scripts"
)

# Add Docker Hub mirror build arg if configured
if [[ "$no_mirror" != "true" ]] && [[ -n "${XROAD_MIRROR_DOCKER_URL:-}" ]]; then
  MIRROR_BUILD_ARGS+=(--build-arg "DOCKER_REGISTRY=${XROAD_MIRROR_DOCKER_URL}")
fi

if [[ "$no_mirror" != "true" ]] && [[ -n "${XROAD_MIRROR_UBUNTU_URL-}" ]] && [[ -n "${XROAD_MIRROR_USERNAME-}" ]] && [[ -n "${XROAD_MIRROR_TOKEN-}" ]]; then
  MIRROR_BUILD_ARGS+=(
    --build-arg XROAD_MIRROR_URL="$XROAD_MIRROR_UBUNTU_URL"
    --build-arg XROAD_MIRROR_USER="$XROAD_MIRROR_USERNAME"
    --secret "id=mirror_token,env=XROAD_MIRROR_TOKEN"
  )
fi

build() {
  echo "BUILDING $tag:$version$2 using ${1#$dir/}"
  local build_args=($no_cache --build-arg "VERSION=$version" --build-arg "TAG=$tag")
  [[ -n $repo ]] && build_args+=(--build-arg "REPO=$repo")
  [[ -n $repo_key ]] && build_args+=(--build-arg "REPO_KEY=$repo_key")
  [[ -n $dist ]] && build_args+=(--build-arg "DIST=$dist")
  [[ -n ${LABEL-} ]] && build_args+=(--label "$LABEL")
  local package_args=()
  [[ "${3:-}" == "true" ]] && package_args=("${PACKAGE_BUILD_ARGS[@]+"${PACKAGE_BUILD_ARGS[@]}"}")
  docker build --progress=plain -f "$1" "${build_args[@]}" "${package_args[@]+"${package_args[@]}"}" "${MIRROR_BUILD_ARGS[@]}" "${SHARED_SCRIPT_BUILD_ARGS[@]}" -t "$tag:$version$2" "$dir"
}

copy_variant_conf() {
  local variant=$1
  local build_conf_dir="$dir/build/etc/xroad/conf.d"
  rm -rf build
  mkdir -p "$build_conf_dir"
  cp "$dir/../deployment/native-packages/src/xroad/default-configuration/override-securityserver-$variant.ini" "$build_conf_dir"
}


build_variant() {
  echo "BUILDING variant $tag:$version$1-$2"
  copy_variant_conf "$2"
  docker build --progress=plain -f "$dir/Dockerfile-variant" \
    --build-arg "VERSION=$version" \
    --build-arg "FROM=$tag:$version$1" \
    --build-arg "VARIANT=$2" \
    -t "$tag:$version$1-$2" "$dir"
}

# Ensure the base image is warmed
if [[ -n "${XROAD_MIRROR_DOCKER_URL:-}" ]]; then
  docker pull "${XROAD_MIRROR_DOCKER_URL}ubuntu:26.04"
else
  docker pull ubuntu:26.04
fi

if $build_all || [[ "$target" == "full" ]]; then
  build "$dir/Dockerfile" "" true
fi

if $build_all; then
  build_variant "" "fi"
  build_variant "" "ee"
  build_variant "" "fo"
  build_variant "" "is"

  build "$dir/kubernetesBalancer/primary/Dockerfile" "-primary"
  build "$dir/kubernetesBalancer/secondary/Dockerfile" "-secondary"

  build_variant "-primary" "fi"
  build_variant "-secondary" "fi"
  build_variant "-primary" "is"
  build_variant "-secondary" "is"
  build_variant "-primary" "ee"
  build_variant "-secondary" "ee"
fi
