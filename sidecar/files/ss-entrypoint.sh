#!/bin/bash

# Explicit switch, never auto-detected. On success the regular start follows
# in the same container.
if [ "${XROAD_MIGRATION_MODE:-}" = "true" ]; then
  /usr/share/xroad/scripts/sidecar/migration/orchestrator.sh
  migration_status=$?
  if [ "$migration_status" -ne 0 ]; then
    exit "$migration_status"
  fi
fi

# Runs on every path that reaches provisioning, including the hand-over from
# a successful migration in this same container: its last step writes the
# X-Road 8 VERSION marker and leaves no PostgreSQL cluster but 18, so this
# does not refuse it. Placed before _entrypoint_common.sh is even sourced, so
# a refusal writes nothing to the mounted volumes.
regular_boot_guard() {
  local version_file=/etc/xroad/VERSION
  if [ -f "$version_file" ]; then
    local version
    version="$(tr -d '[:space:]' <"$version_file")"
    case "$version" in
      7.*)
        echo "Refusing to start: $version_file is X-Road $version. Run this image with XROAD_MIGRATION_MODE=true to migrate to X-Road 8 first." >&2
        exit 1
        ;;
    esac
  fi

  local pg_root=/var/lib/postgresql cluster_dir major

  # A cluster's data directory mounted directly at the volume root, with no
  # numbered subdirectory.
  if [ -s "$pg_root/PG_VERSION" ]; then
    major="$(tr -d '[:space:]' <"$pg_root/PG_VERSION")"
    if [ "$major" != "18" ]; then
      echo "Refusing to start: found a PostgreSQL $major cluster at $pg_root. Run this image with XROAD_MIGRATION_MODE=true to migrate to X-Road 8 first." >&2
      exit 1
    fi
  fi

  for cluster_dir in "$pg_root"/*/main; do
    [ -s "$cluster_dir/PG_VERSION" ] || continue
    major="${cluster_dir#"$pg_root"/}"
    major="${major%%/*}"
    if [ "$major" != "18" ]; then
      echo "Refusing to start: found a PostgreSQL $major cluster at $cluster_dir. Run this image with XROAD_MIGRATION_MODE=true to migrate to X-Road 8 first." >&2
      exit 1
    fi
  done
}

regular_boot_guard

source /root/_entrypoint_common.sh
# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
