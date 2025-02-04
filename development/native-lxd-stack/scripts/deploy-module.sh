#!/bin/bash

ansible-playbook -i $XROAD_HOME/development/native-lxd-stack/config/ansible_hosts.txt \
$XROAD_HOME/ansible/xroad_dev_partial.yml \
--skip-tags compile,build-packages \
-e selected_modules=$@ -vv