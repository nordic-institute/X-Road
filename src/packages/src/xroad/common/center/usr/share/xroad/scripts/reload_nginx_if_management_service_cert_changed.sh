#!/bin/bash

if [[ -n $(find /etc/xroad/ssl -type f -regextype posix-extended -regex '.*management-service.crt' -mmin -5) ]]
then
    sudo service nginx reload
fi
