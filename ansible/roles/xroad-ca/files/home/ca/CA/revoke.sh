#!/bin/sh
sudo -u ca openssl ca -config CA.cnf -revoke $1

