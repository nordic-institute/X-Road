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

  echo "Execution plan:"
  echo "Recreate containers: $RECREATE"
}

function main() {
  parse_arguments "$@"

  if [ "$RECREATE" = true ]; then
    ./delete-env.sh
  fi

  ansible-playbook -i config/ansible_hosts.txt \
    ../../ansible/xroad_dev.yml \
    --forks 10 \
    --skip-tags compile,build-packages
}

main "$@"
