#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

ENV_FILE=".env.local"
COMPOSE_FILE_ARGS="-f compose.yaml -f compose.dev.yaml"

for i in "$@"; do
case "$i" in
    "--initialize")
        INITIALIZE=1
        ;;
esac
done

if [[ $# -eq 0 ]]; then
echo "Available args:"
echo "--initialize: Initialize the environment"

fi
docker compose $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" up -d

if [[ -n "$INITIALIZE" ]]; then
    docker compose $COMPOSE_FILE_ARGS \
    --env-file "$ENV_FILE" \
    run hurl \
    --insecure \
    --variables-file /hurl-src/vars.env \
    --file-root /hurl-files /hurl-src/setup.hurl \
    --very-verbose \
    --retry 12 \
    --retry-interval 10000
fi