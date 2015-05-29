#!/bin/sh

sudo -u ui /usr/share/xroad/scripts/serviceimporter.sh -export $@ || exit $?
sudo -u ui /usr/share/xroad/scripts/reload_producer_proxy.sh

