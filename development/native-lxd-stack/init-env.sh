#!/bin/bash

RECREATE=false

function parse_arguments() {
  while [[ "$#" -gt 0 ]]; do
    case $1 in
    --recreate) RECREATE=true ;;
    *)
      echo "Unknown parameter: $1"
      exit 1
      ;;
    esac
    shift
  done
}

function main() {
  parse_arguments "$@"

  hurl ../hurl/setup.hurl \
    --var-file=.env.local \
    --very-verbose \
    --retry 12 \
    --retry-interval 8000
}

main "$@"
