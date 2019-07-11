#!/bin/sh
filename="$1"

if [ "$filename" = "src/test/resources/error.wsdl" -o "$filename" = "file:src/test/resources/error.wsdl" ]; then
    echo "ERROR: this is not fine" >&2
    exit 1
else
    exit 0
fi
