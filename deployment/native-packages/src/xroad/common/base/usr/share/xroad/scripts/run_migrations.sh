#!/bin/bash
# X-Road migration runner
# Executes versioned migration scripts during package upgrades.
#
# Usage: run_migrations.sh <component> <old_version> <new_version>
#
# Migration scripts are located in /usr/share/xroad/migrations/<component>/<version>/<seq>_<description>.sh
# Applied migrations are tracked in /var/lib/xroad/migrations/.applied

set -euo pipefail

readonly MIGRATIONS_BASE_DIR="/usr/share/xroad/migrations"
readonly STATE_DIR="/var/lib/xroad/migrations"
readonly APPLIED_FILE="${STATE_DIR}/.applied"

. /usr/share/xroad/scripts/_migration_common.sh

# Compare two semver strings. Returns 0 if $1 > $2.
version_gt() {
    local IFS=.
    local -a v1=($1) v2=($2)
    local i
    for ((i = 0; i < 3; i++)); do
        local n1=${v1[i]:-0}
        local n2=${v2[i]:-0}
        if ((n1 > n2)); then
            return 0
        elif ((n1 < n2)); then
            return 1
        fi
    done
    return 1
}

# Compare two semver strings. Returns 0 if $1 <= $2.
version_le() {
    ! version_gt "$1" "$2"
}

# Check if a migration script has already been applied.
is_applied() {
    local script_id="$1"
    if [ -f "${APPLIED_FILE}" ]; then
        grep -qF "${script_id}" "${APPLIED_FILE}"
    else
        return 1
    fi
}

# Record a migration script as applied.
mark_applied() {
    local script_id="$1"
    echo "${script_id} $(date --utc -Iseconds)" >> "${APPLIED_FILE}"
}

# Normalize version string: extract major.minor.patch from package manager version strings
# e.g., "8.0.0.20260315~ubuntu22.04" -> "8.0.0", "8.0.0.beta1" -> "8.0.0"
normalize_version() {
    echo "$1" | grep -oP '^\d+\.\d+\.\d+' || echo "$1"
}

main() {
    if [ $# -ne 3 ]; then
        log_error "Usage: run_migrations.sh <component> <old_version> <new_version>"
        exit 1
    fi

    local component="$1"
    local old_version
    local new_version
    old_version=$(normalize_version "$2")
    new_version=$(normalize_version "$3")

    local migrations_dir="${MIGRATIONS_BASE_DIR}/${component}"

    if [ -z "${old_version}" ] || [ -z "${new_version}" ]; then
        log_error "Invalid version arguments: old='$2' new='$3'"
        exit 1
    fi

    if [ "${old_version}" = "${new_version}" ]; then
        log "[${component}] Old and new versions are the same (${old_version}), nothing to migrate"
        exit 0
    fi

    if ! [ -d "${migrations_dir}" ]; then
        log "[${component}] No migrations directory found at ${migrations_dir}, nothing to do"
        exit 0
    fi

    # Ensure state directory exists
    mkdir -p "${STATE_DIR}"
    chmod 0750 "${STATE_DIR}"

    # Collect applicable version directories
    local -a version_dirs=()
    for dir in "${migrations_dir}"/*/; do
        [ -d "${dir}" ] || continue
        local ver
        ver=$(basename "${dir}")
        # Include versions where: version > old AND version <= new
        if version_gt "${ver}" "${old_version}" && version_le "${ver}" "${new_version}"; then
            version_dirs+=("${ver}")
        fi
    done

    if [ ${#version_dirs[@]} -eq 0 ]; then
        log "[${component}] No applicable migrations for ${old_version} -> ${new_version}"
        exit 0
    fi

    # Sort version directories
    local -a sorted_versions
    readarray -t sorted_versions < <(printf '%s\n' "${version_dirs[@]}" | sort -V)

    log "[${component}] Running migrations for upgrade ${old_version} -> ${new_version}"

    local migration_count=0

    for ver in "${sorted_versions[@]}"; do
        local ver_dir="${migrations_dir}/${ver}"

        # Find and sort migration scripts within this version directory
        local -a scripts=()
        for script in "${ver_dir}"/*.sh; do
            [ -f "${script}" ] || continue
            scripts+=("${script}")
        done

        # Sort scripts by filename (sequence prefix ensures correct order)
        readarray -t scripts < <(printf '%s\n' "${scripts[@]}" | sort)

        for script in "${scripts[@]}"; do
            local script_name
            script_name=$(basename "${script}")
            local script_id="${component}/${ver}/${script_name}"

            if is_applied "${script_id}"; then
                log "Skipping already applied: ${script_id}"
                continue
            fi

            log "Executing migration: ${script_id}"

            local rc=0
            bash "${script}" || rc=$?

            if [ "${rc}" -eq 0 ]; then
                mark_applied "${script_id}"
                log "Successfully applied: ${script_id}"
                ((migration_count++)) || true
            elif [ "${rc}" -eq "${MIGRATION_SKIP}" ]; then
                # Prerequisites not met — skip but mark as applied
                mark_applied "${script_id}"
                log "Skipping (prerequisites not met): ${script_id}"
            else
                log_error "Migration failed (exit code ${rc}): ${script_id}"
                log_error "Aborting migration runner. Fix the issue and re-run the upgrade."
                exit 1
            fi
        done
    done

    log "[${component}] Migration complete: ${migration_count} script(s) applied for ${old_version} -> ${new_version}"
    exit 0
}

main "$@"
