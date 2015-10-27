#!/bin/sh

# FIXME: error handling
DUMP_FILE=$1

PGPASSWORD=serverconf pg_dump -F t -h 127.0.0.1 -U serverconf -f ${DUMP_FILE} serverconf
