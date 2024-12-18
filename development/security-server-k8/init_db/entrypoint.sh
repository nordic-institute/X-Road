#!/bin/bash

JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"


exec liquibase \
  --changelog-file="changelog/${CHANGELOG_FILE}" \
  --url="${JDBC_URL}" \
  --username="${DB_USERNAME}" \
  --password="${DB_PASSWORD}" \
  --log-level=debug \
  update
