#!/bin/bash

INVENTORY=$1
shift
MODULES=$@

ansible-playbook -i $INVENTORY \
$XROAD_HOME/ansible/xroad_dev_partial.yml \
--skip-tags compile,build-packages \
-e selected_modules=$MODULES -vv