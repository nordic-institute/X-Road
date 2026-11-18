#!/bin/bash
# Renames the unprefixed X-Road 7 op-monitor.hibernate.* keys of db.properties to xroad.db.op-monitor.hibernate.*.
# Usage: migrate_opmonitor_db_properties.sh [properties-file]
set -euo pipefail

readonly DB_PROPS="${1:-/etc/xroad/db.properties}"
readonly PREFIX='xroad.db.'
readonly OLD_PATTERN='^([[:space:]]*)(op-monitor\.hibernate\.[^=[:space:]]*)(.*)$'
readonly NEW_PATTERN='^[[:space:]]*(xroad\.db\.op-monitor\.hibernate\.[^=[:space:]]*)'

[[ -f "$DB_PROPS" ]] || exit 0

tmp=$(mktemp)
staging=''
trap 'rm -f "$tmp" ${staging:+"$staging"}' EXIT

existing_keys='|'
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ $NEW_PATTERN ]]; then
    existing_keys+="${BASH_REMATCH[1]}|"
  fi
done < "$DB_PROPS"

renamed=0
removed=0
while :; do
  if IFS= read -r line; then
    eol=$'\n'
  else
    eol=''
    [[ -n "$line" ]] || break
  fi
  if [[ "$line" =~ $OLD_PATTERN ]]; then
    key="${BASH_REMATCH[2]}"
    if [[ "$existing_keys" == *"|$PREFIX$key|"* ]]; then
      removed=$((removed + 1))
      continue
    fi
    line="${BASH_REMATCH[1]}$PREFIX$key${BASH_REMATCH[3]}"
    renamed=$((renamed + 1))
  fi
  printf '%s%s' "$line" "$eol"
done < "$DB_PROPS" > "$tmp"

if [[ $((renamed + removed)) -eq 0 ]]; then
  exit 0
fi

# Hard-linking a private copy creates the final name atomically and fails when a file or dangling symlink is there.
# ln links into a directory or a symlink to one, so such a name is skipped, and a link that still lands inside is removed.
link_backup() {
  local candidate="$1"
  if [[ -e "$candidate" || -L "$candidate" ]]; then
    return 1
  fi
  ln "$staging" "$candidate" 2>/dev/null || return 1
  if [[ -f "$candidate" && ! -L "$candidate" && "$candidate" -ef "$staging" ]]; then
    return 0
  fi
  local stray="$candidate/${staging##*/}"
  if [[ "$stray" -ef "$staging" ]]; then
    rm -f "$stray"
  fi
  return 1
}

staging=$(mktemp "$DB_PROPS.staging.XXXXXX")
cp -p "$DB_PROPS" "$staging"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup="$DB_PROPS.$stamp.bak"
attempt=0
until link_backup "$backup"; do
  attempt=$((attempt + 1))
  if [[ "$attempt" -gt 100 ]]; then
    echo "Cannot create a backup of $DB_PROPS" >&2
    exit 1
  fi
  backup="$DB_PROPS.$stamp-$attempt.bak"
done
cat "$tmp" > "$DB_PROPS"
echo "Migrated op-monitor database keys in $DB_PROPS: $renamed renamed, $removed removed as duplicates (backup: $backup)"
