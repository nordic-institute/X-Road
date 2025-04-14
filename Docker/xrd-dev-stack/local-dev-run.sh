#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

ENV_FILE=".env"
COMPOSE_FILE_ARGS="-f compose.yaml -f compose.dev.yaml"

for i in "$@"; do
  case "$i" in
  "--initialize")
    INITIALIZE=1
    ;;
  "--local")
    ENV_FILE=".env.local"
    ;;
     "--perftest")
       PERFTEST=1
       COMPOSE_FILE_ARGS="$COMPOSE_FILE_ARGS -f compose.perftest.yaml"
       ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "Available args:"
  echo "--initialize: Initialize the environment"
  echo "--local: Use .env.local file"
fi

docker compose $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" up -d --build hurl

if [[ -n "$INITIALIZE" ]]; then
  docker compose $COMPOSE_FILE_ARGS \
    --env-file "$ENV_FILE" \
    run \
    --rm \
    hurl \
    --insecure \
    --variables-file /hurl-src/vars.env \
    --file-root /hurl-files /hurl-src/setup.hurl \
    --very-verbose \
    --retry 12 \
    --retry-interval 10000
fi

if [[ -n "$PERFTEST" && -n "$INITIALIZE" ]]; then
  docker compose $COMPOSE_FILE_ARGS \
    --env-file "$ENV_FILE" \
    run \
    --rm \
    hurl \
    --insecure \
    --variables-file /hurl-src/vars.env \
    --file-root /hurl-files /hurl-src/perftest-ss0.hurl \
    --very-verbose \
    --retry 12 \
    --retry-interval 4000

  #disable the messagelog body logging
  docker compose $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" \
    exec ss0 sh -c "sed -i 's/message-body-logging=true/message-body-logging=false/' /etc/xroad/conf.d/addons/message-log.ini"
  docker compose $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" \
    exec ss0 sh -c "supervisorctl restart xroad-proxy"

  docker compose $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" \
    exec ss1 sh -c "sed -i 's/message-body-logging=true/message-body-logging=false/' /etc/xroad/conf.d/addons/message-log.ini"
  docker compose $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" \
    exec ss1 sh -c "supervisorctl restart xroad-proxy"
fi
