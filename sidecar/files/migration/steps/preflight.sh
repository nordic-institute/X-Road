#!/bin/bash
#
# Preflight checks for X-Road sidecar migration mode. Confirms the mounted
# source configuration is a 7.8.x release and that the target configuration
# volume is still empty. Nothing is written by this step; it only inspects
# the mounted volumes named by SOURCE_CONFIG_DIR and TARGET_CONFIG_DIR (set
# by orchestrator.sh).

migration_preflight_check_source_version() {
  local version_file="$SOURCE_CONFIG_DIR/VERSION"

  if [[ ! -f "$version_file" ]]; then
    STEP_REASON="source configuration version not found: $version_file is missing"
    return 1
  fi

  local version
  version="$(tr -d '[:space:]' <"$version_file")"

  if [[ ! "$version" =~ ^7\.8\. ]]; then
    STEP_REASON="source configuration version is not 7.8.x (found: ${version:-empty})"
    return 1
  fi

  return 0
}

# A packaged, never-booted target configuration volume still carries the
# image's own default /etc/xroad tree (a Docker-managed named volume is
# seeded from the image on first mount), so "empty" here means "never
# provisioned" rather than "no files". The regular entrypoint tells a fresh
# volume from a configured one by the presence of db.properties alone (see
# _entrypoint_common.sh); this check also treats TLS material under ssl/ as
# a sign of prior configuration.
migration_preflight_check_target_config_empty() {
  local dir="$TARGET_CONFIG_DIR"

  if [[ ! -d "$dir" ]]; then
    STEP_REASON="target volume $dir does not exist"
    return 1
  fi

  if [[ -f "$dir/db.properties" ]]; then
    STEP_REASON="target volume $dir is not empty: db.properties already exists"
    return 1
  fi

  if compgen -G "$dir/ssl/*.crt" >/dev/null \
    || compgen -G "$dir/ssl/*.key" >/dev/null \
    || compgen -G "$dir/ssl/*.p12" >/dev/null; then
    STEP_REASON="target volume $dir is not empty: TLS material already exists under $dir/ssl"
    return 1
  fi

  return 0
}

step_preflight() {
  migration_preflight_check_source_version || return 1
  migration_preflight_check_target_config_empty || return 1
  return 0
}
