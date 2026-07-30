#!/bin/bash
source "${BASH_SOURCE%/*}/../../../scripts/lib/base-script.sh"

INVENTORY=$1
shift
MODULES=$@

ansible-playbook -i $INVENTORY \
"${BASH_SOURCE%/*}/../../../development/ansible/xroad_dev_partial.yml" \
--skip-tags compile,build-packages \
-e selected_modules=$MODULES -vv