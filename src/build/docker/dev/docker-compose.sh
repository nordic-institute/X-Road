#!/usr/bin/env bash

base_path=$(dirname "$BASH_SOURCE")

docker_compose_yml='docker-compose.yml'
xrd_db_path="$base_path/$docker_compose_yml"
xrd_cs_path="$base_path/../../../centralserver/admin-service/docker/$docker_compose_yml"

args=(
  "-f" "$base_path/../common/$docker_compose_yml" # Database
  "-f" "$base_path/../../../centralserver/admin-service/build/docker/$docker_compose_yml" # Central Server
  #"-f" "$base_path/../../../centralserver/admin-service/build/docker/$docker_compose_yml" # Security Server #1
  #"-f" "$base_path/../../../centralserver/admin-service/build/docker/$docker_compose_yml" # Security Server #2
  "${@}"
)

docker-compose "${args[@]}"
