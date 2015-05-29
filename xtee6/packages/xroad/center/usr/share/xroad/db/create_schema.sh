#!/bin/sh

echo "CREATE ROLE centerui LOGIN PASSWORD 'centerui';" | psql postgres
createdb -O centerui -E UTF-8 centerui_production
psql -d centerui_production -c "CREATE EXTENSION hstore;"



