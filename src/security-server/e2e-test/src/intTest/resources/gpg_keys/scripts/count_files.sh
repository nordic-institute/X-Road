#!/bin/bash

# This script counts the number of .zip files in the specified folder.

if [[ $# -lt 1 || ! -d "$1" ]]; then
    echo "Usage: $0 <folder>"
    exit 1
fi

find "$1" -maxdepth 1 -type f -name '*.zip' | wc -l
