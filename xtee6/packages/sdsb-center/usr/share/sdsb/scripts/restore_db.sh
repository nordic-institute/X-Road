#!/bin/sh

PGPASSWORD=centerui pg_restore -h 127.0.0.1 -U centerui -O -x -c -d centerui_production /var/lib/sdsb/dbdump.dat
