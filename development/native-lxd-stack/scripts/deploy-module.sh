#!/bin/bash

INVENTORY_PATH="config/ansible_hosts.txt"

ansible-playbook -i $INVENTORY_PATH \
$XROAD_HOME/ansible/xroad_dev_partial.yml \
--skip-tags compile,build-packages \
-e selected_modules=$@ -vv