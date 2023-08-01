#!/bin/bash

if [[ -n $(find /etc/xroad/ssl -type f -regextype posix-extended -regex '.*(internal|external)-conf\.crt' -mmin -6) ]]
then
    sudo service nginx reload
fi