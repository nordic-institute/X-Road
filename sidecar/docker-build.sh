#!/bin/bash
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" >&/dev/null && pwd)"
version="${1:-6.25.0}"
tag="${2:-xroad-security-server-sidecar}"
build() { 
  echo "BUILDING $tag:$version$2 using ${1#$dir/}"
  docker build -f "$1" --build-arg "VERSION=$version" --build-arg "TAG=$tag" -t "$tag:$version$2" "$dir"
}

build "$dir/slim/Dockerfile" "-slim"
build "$dir/slim/fi/Dockerfile" "-slim-fi"
build "$dir/Dockerfile" ""
build "$dir/fi/Dockerfile" "-fi"

build "$dir/kubernetesBalancer/slim/primary/Dockerfile" "-primary-slim"
build "$dir/kubernetesBalancer/slim/secondary/Dockerfile" "-secondary-slim"
build "$dir/kubernetesBalancer/primary/Dockerfile" "-primary"
build "$dir/kubernetesBalancer/secondary/Dockerfile" "-secondary"
