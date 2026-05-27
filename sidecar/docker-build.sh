#!/bin/bash
set -euo pipefail
no_cache=""
no_mirror=""
n=1
args_to_keep=()
for i in "$@" ; do
    if [[ $i == "--no-cache" ]] ; then
        no_cache="--no-cache"
    elif [[ $i == "--no-mirror" ]] ; then
        no_mirror="true"
    else
        args_to_keep+=("$i")
    fi
done
set -- "${args_to_keep[@]}"

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" >&/dev/null && pwd)"
version="${1:-8.0.0}"
tag="${2:-xroad-security-server-sidecar}"
repo="${3-}"
dist="${4-}"
repo_key="${5-}"

# Prepare mirror build args (unless --no-mirror flag is set)
MIRROR_BUILD_ARGS=(--build-context "mirror-scripts=$dir/../deployment/.scripts")

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
  docker build --progress=plain -f "$1" "${build_args[@]}" "${MIRROR_BUILD_ARGS[@]}" -t "$tag:$version$2" "$dir"
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

# Ensure latest ubuntu image is used as base
if [[ -n "${XROAD_MIRROR_DOCKER_URL:-}" ]]; then
  docker pull "${XROAD_MIRROR_DOCKER_URL}ubuntu:26.04"
else
  docker pull ubuntu:26.04
fi

build "$dir/slim/Dockerfile" "-slim"
build_variant "-slim" "fi"
build_variant "-slim" "fo"
build_variant "-slim" "is"

build "$dir/Dockerfile" ""
build_variant "" "fi"
build_variant "" "ee"
build_variant "" "fo"
build_variant "" "is"

build "$dir/kubernetesBalancer/slim/primary/Dockerfile" "-slim-primary"
build "$dir/kubernetesBalancer/slim/secondary/Dockerfile" "-slim-secondary"
build "$dir/kubernetesBalancer/primary/Dockerfile" "-primary"
build "$dir/kubernetesBalancer/secondary/Dockerfile" "-secondary"

build_variant "-slim-primary" "fi"
build_variant "-slim-secondary" "fi"
build_variant "-slim-primary" "is"
build_variant "-slim-secondary" "is"

build_variant "-primary" "fi"
build_variant "-secondary" "fi"
build_variant "-primary" "is"
build_variant "-secondary" "is"
build_variant "-primary" "ee"
build_variant "-secondary" "ee"
