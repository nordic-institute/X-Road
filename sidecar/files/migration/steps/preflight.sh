#!/bin/bash
#
# Migration step 1: refuses to start unless the 7.8 configuration volume is
# present and on 7.8.3 (with any package suffix, e.g. 7.8.3-1.ubuntu24.04),
# the target configuration volume is unused, the country variant matches and
# XROAD_MIGRATION_SOFTTOKEN_PIN is given. Writes nothing.

migration_preflight_check_source_version() {
  if [[ ! -d "$SOURCE_CONFIG_DIR" || -z "$(ls -A "$SOURCE_CONFIG_DIR" 2>/dev/null)" ]]; then
    STEP_REASON="7.8 configuration volume is not mounted: $SOURCE_CONFIG_DIR is missing or empty"
    return 1
  fi

  local version_file="$SOURCE_CONFIG_DIR/VERSION"
  if [[ ! -f "$version_file" ]]; then
    STEP_REASON="source configuration version not found: $version_file is missing"
    return 1
  fi

  local version
  version="$(tr -d '[:space:]' <"$version_file")"

  if [[ -z "$version" ]]; then
    STEP_REASON="source configuration version not found: $version_file is empty"
    return 1
  fi

  if [[ ! "$version" =~ ^7\.8\.3(-.+)?$ ]]; then
    STEP_REASON="source configuration version is $version, not 7.8.3: upgrade to 7.8.3 first"
    return 1
  fi

  return 0
}

# Docker seeds a new volume with the image's /etc/xroad, which has no
# db.properties; provisioning and step 2 both create one.
migration_preflight_check_target_config_empty() {
  [[ -f "$TARGET_CONFIG_DIR/db.properties" ]] || return 0

  STEP_REASON="target volume $TARGET_CONFIG_DIR is not empty: recreate the target configuration and database volumes before retrying"
  return 1
}

# The target variant comes from the image's packaged configuration: only a
# Docker named volume is seeded with it, a bind mount or PVC starts empty.
migration_preflight_detect_variant() {
  local f
  f="$(compgen -G "$1/conf.d/override-securityserver-*.ini" | head -1)"
  [[ -n "$f" ]] || return 1
  f="${f##*/override-securityserver-}"
  echo "${f%.ini}"
}

migration_preflight_check_country_variant() {
  local source_variant target_variant
  source_variant="$(migration_preflight_detect_variant "$SOURCE_CONFIG_DIR")" || source_variant=""
  target_variant="$(migration_preflight_detect_variant "$PACKAGED_CONFIG_DIR")" || target_variant=""

  if [[ "$source_variant" != "$target_variant" ]]; then
    STEP_REASON="country variant mismatch: the source is ${source_variant:-the regular (non-variant) image} and the target is ${target_variant:-the regular (non-variant) image}"
    return 1
  fi
  return 0
}

migration_preflight_check_softtoken_pin() {
  [[ -n "${XROAD_MIGRATION_SOFTTOKEN_PIN:-}" ]] && return 0

  STEP_REASON="XROAD_MIGRATION_SOFTTOKEN_PIN is not set"
  return 1
}

step_preflight() {
  migration_preflight_check_source_version || return 1
  migration_preflight_check_target_config_empty || return 1
  migration_preflight_check_country_variant || return 1
  migration_preflight_check_softtoken_pin || return 1
  return 0
}
