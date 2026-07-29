#!/bin/bash
# Deploy module(s) by building and reinstalling packages via apt/dnf
# Usage: ./deploy-package.sh <inventory_path> <module1> [module2] ...

SCRIPT_DIR=$(dirname "$(realpath "${BASH_SOURCE[0]}")")
XROAD_HOME=$(realpath "$SCRIPT_DIR/../../../")

INVENTORY=$1
shift
MODULES=$@

if [ -z "$INVENTORY" ] || [ -z "$MODULES" ]; then
  echo "Usage: $0 <inventory_path> <module1> [module2] ..."
  exit 1
fi

ansible-playbook -i "$INVENTORY" \
  "$XROAD_HOME/development/ansible/xroad_dev_partial_package.yml" \
  -e selected_modules="$MODULES" -vv
