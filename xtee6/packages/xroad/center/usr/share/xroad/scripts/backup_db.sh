#!/bin/sh

PGPASSWORD=centerui pg_dump -a -n public --disable-triggers -T schema_migrations -F p -h 127.0.0.1 -U centerui -f /var/lib/xroad/dbdump.dat centerui_production
