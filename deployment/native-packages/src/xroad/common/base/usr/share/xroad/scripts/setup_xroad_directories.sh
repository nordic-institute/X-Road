#!/bin/bash

setup_directories() {
  # ensure home directory ownership
  mkdir -p /var/lib/xroad
  chown xroad:xroad /var/lib/xroad
  chmod 0755 /var/lib/xroad
  chmod -R go-w /var/lib/xroad

  # config folder permissions
  chown xroad:xroad /etc/xroad
  chmod 0751 /etc/xroad

  # nicer log directory permissions
  mkdir -p /var/log/xroad
  chmod -R go-w /var/log/xroad
  chmod 1770 /var/log/xroad
  chown xroad:adm /var/log/xroad

  #tmp folder
  mkdir -p /var/tmp/xroad
  chmod 1750 /var/tmp/xroad
  chown xroad:xroad /var/tmp/xroad
}

# Run setup_directories function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  setup_directories
fi