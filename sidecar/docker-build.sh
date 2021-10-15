#!/bin/bash
set -euo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" >&/dev/null && pwd)"
version="${1:-6.26.0}"
tag="${2:-xroad-security-server-sidecar}"
build() {
  echo "BUILDING $tag:$version$2 using ${1#$dir/}"
  docker build -f "$1" --build-arg "VERSION=$version" --build-arg "TAG=$tag" -t "$tag:$version$2" "$dir"
}

build "$dir/slim/Dockerfile" "-slim"
build "$dir/slim/fi/Dockerfile" "-slim-fi"
build "$dir/slim/fo/Dockerfile" "-slim-fo"
build "$dir/Dockerfile" ""
build "$dir/fi/Dockerfile" "-fi"
build "$dir/ee/Dockerfile" "-ee"
build "$dir/fo/Dockerfile" "-fo"

build "$dir/kubernetesBalancer/slim/primary/Dockerfile" "-slim-primary"
build "$dir/kubernetesBalancer/slim/secondary/Dockerfile" "-slim-secondary"
build "$dir/kubernetesBalancer/primary/Dockerfile" "-primary"
build "$dir/kubernetesBalancer/secondary/Dockerfile" "-secondary"

build "$dir/kubernetesBalancer/slim/fi/primary/Dockerfile" "-slim-primary-fi"
build "$dir/kubernetesBalancer/slim/fi/secondary/Dockerfile" "-slim-secondary-fi"
build "$dir/kubernetesBalancer/fi/primary/Dockerfile" "-primary-fi"
build "$dir/kubernetesBalancer/fi/secondary/Dockerfile" "-secondary-fi"

build "$dir/kubernetesBalancer/ee/primary/Dockerfile" "-primary-ee"
build "$dir/kubernetesBalancer/ee/secondary/Dockerfile" "-secondary-ee"
