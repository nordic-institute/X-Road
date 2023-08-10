#!/bin/bash -e

KEYS_DIR=/etc/xroad/backup-keys

import_key() {
  local file=$1
  gpg --homedir /etc/xroad/gpghome --batch --import < $KEYS_DIR/"$file"
}

trust_key() {
  local keyId=$1
  echo -e "trust\n5\ny\n" | gpg --homedir /etc/xroad/gpghome --batch --no-tty --command-file /dev/stdin --edit-key "$keyId"
}

mkdir -p -m 0700 /etc/xroad/gpghome

import_key backup.key1@example.org.asc
import_key backup.key2@example.org.asc
import_key backup.key3@example.org.asc

trust_key backup.key1@example.org
trust_key backup.key2@example.org
trust_key backup.key3@example.org

crudini --set /etc/xroad/conf.d/local.ini proxy backup-encryption-enabled true
crudini --set /etc/xroad/conf.d/local.ini proxy backup-encryption-keyids "backup.key1@example.org, backup.key2@example.org, backup.key3@example.org"
