#!/bin/bash

#./delete-env.sh
clear && ansible-playbook -i $XROAD_HOME/ansible/hosts/lxd_hosts.txt \
$XROAD_HOME/ansible/xroad_dev.yml \
--skip-tags compile,build-packages