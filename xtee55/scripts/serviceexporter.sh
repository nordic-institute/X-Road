#!/bin/sh

sudo -u ui /usr/share/sdsb/scripts/serviceimporter.sh -export $@
sudo -u ui /usr/share/sdsb/scripts/reload_producer_proxy.sh

