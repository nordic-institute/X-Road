#!/bin/bash

clear
ansible-playbook -i $XROAD_HOME/ansible/hosts/lxd_hosts.txt \
$XROAD_HOME/ansible/xroad_dev_partial.yml \
--skip-tags compile,build-packages \
-e selected_modules=$@ -vvv