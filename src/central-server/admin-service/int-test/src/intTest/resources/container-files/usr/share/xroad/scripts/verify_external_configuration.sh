#!/bin/bash

# noop script.

die () {
    echo >&2 "$@"
    exit 1
}

[ "$#" -eq 1 ] || die "1 filename argument required, $# provided"

echo "$@"
