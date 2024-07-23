#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

ENV_FILE=".env"
COMPOSE_FILE_ARGS="-f compose.yaml -f compose.dev.yaml -f compose.edc.yaml"

for i in "$@"; do
  case "$i" in
  "--initialize")
    INITIALIZE=1
    ;;
  "--local")
    ENV_FILE=".env.local"
    ;;
  "---init-ss2")
    INIT_SS2=1
    ;;
  "--perftest")
    PERFTEST=1
    ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "Available args:"
  echo "--initialize: Initialize the environment"
  echo "--local: Use .env.local file"
  echo "--init-ss2: Will initialize SS2"
  echo "--perftest: Will initialize perftest"
fi

COMPOSE_EXTRA_ARGS=""
if [[ -n "$INIT_SS2" ]]; then
  COMPOSE_EXTRA_ARGS="--profile xrd7"
fi
if [[ -n "$PERFTEST" ]]; then
  COMPOSE_EXTRA_ARGS="--profile perftest"
fi

docker compose $COMPOSE_EXTRA_ARGS $COMPOSE_FILE_ARGS --env-file "$ENV_FILE" up -d

if [[ -n "$INITIALIZE" ]]; then
  docker compose $COMPOSE_FILE_ARGS \
    --env-file "$ENV_FILE" \
    run hurl \
    --insecure \
    --variables-file /hurl-src/vars.env \
    --file-root /hurl-files /hurl-src/setup.hurl \
    --very-verbose \
    --retry 12 \
    --retry-interval 8000
fi

if [[ -n "$INIT_SS2" ]]; then
  docker compose $COMPOSE_FILE_ARGS \
    --env-file "$ENV_FILE" \
    run hurl \
    --insecure \
    --variables-file /hurl-src/vars.env \
    --file-root /hurl-files /hurl-src/xrd7-ss2.hurl \
    --very-verbose \
    --retry 12 \
    --retry-interval 8000
fi

if [[ -n "$PERFTEST" && -n "$INITIALIZE" ]]; then
  docker compose $COMPOSE_FILE_ARGS \
    --env-file "$ENV_FILE" \
    run hurl \
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