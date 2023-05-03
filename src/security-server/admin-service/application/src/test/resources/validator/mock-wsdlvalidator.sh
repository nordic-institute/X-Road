#!/bin/bash
filename="$1"

if [ "$filename" = "src/test/resources/wsdl/error.wsdl" -o "$filename" = "file:src/test/resources/wsdl/error.wsdl" ]; then
    echo "ERROR: this is not fine" >&2
    exit 1
elif [ "$filename" = "src/test/resources/wsdl/warning.wsdl" -o "$filename" = "file:src/test/resources/wsdl/warning.wsdl" ]; then
    echo "WARNING: this can be ignored" >&2
    exit 0
else
    exit 0
fi
