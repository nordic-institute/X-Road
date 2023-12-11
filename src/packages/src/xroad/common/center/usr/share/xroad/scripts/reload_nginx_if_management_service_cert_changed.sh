#!/bin/bash

if [[ -n $(find /etc/xroad/ssl/management-service.crt -type f -mmin -6) ]]
then
    sudo service nginx reload
fi
