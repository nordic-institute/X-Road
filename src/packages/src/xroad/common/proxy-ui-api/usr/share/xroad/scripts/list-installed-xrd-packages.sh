#!/bin/bash

if command -v dpkg-query >/dev/null 2>&1; then
    dpkg-query -W -f '${Package}##${Version}\n' | grep xroad-*
else
    rpm -qa --qf '%{NAME}##%{VERSION}\n' | grep xroad-*
fi
