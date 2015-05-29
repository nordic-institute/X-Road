#!/bin/sh

PGPASSWORD=serverconf pg_dump -F t -h 127.0.0.1 -U serverconf -f /var/lib/xroad/dbdump.dat serverconf
