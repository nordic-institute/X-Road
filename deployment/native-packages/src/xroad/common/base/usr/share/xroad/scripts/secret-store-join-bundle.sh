#!/bin/bash
#
# Moves the files a new node needs to join the OpenBao cluster: the OpenBao environment file,
# the unseal keys file and the root token file. The archive holds secrets; keep it root-only
# and delete it after the import.
#
# Usage: secret-store-join-bundle.sh export <file>
#        secret-store-join-bundle.sh import <file>
#        secret-store-join-bundle.sh check
set -euo pipefail
umask 077

BUNDLE_DIR=/etc/openbao
BUNDLE_NAMES=(openbao.env unseal-keys root-token)
extract_dir=

die() {
  echo >&2 "$@"
  exit 1
}

usage() {
  die "Usage: $0 export <file> | import <file> | check"
}

# Prints each file that is missing or empty and fails when there is one.
check_files() {
  local name missing=0
  for name in "${BUNDLE_NAMES[@]}"; do
    if [ ! -s "${BUNDLE_DIR}/${name}" ]; then
      echo >&2 "The file ${BUNDLE_DIR}/${name} is missing or empty"
      missing=1
    fi
  done
  return "$missing"
}

export_bundle() {
  local target="$1" tmp
  check_files || die "Run the export on a node of the cluster"
  [ ! -e "$target" ] || die "Refusing to overwrite ${target}"
  tmp=$(mktemp "${target}.XXXXXX")
  if ! tar -cf "$tmp" -C "$BUNDLE_DIR" "${BUNDLE_NAMES[@]}"; then
    rm -f "$tmp"
    die "Writing the join bundle failed"
  fi
  mv -f "$tmp" "$target"
  echo "Wrote the join bundle ${target}; it holds secrets, delete it after the import"
}

import_bundle() {
  local source="$1" name existing=0 copy
  [ -f "$source" ] || die "The join bundle ${source} does not exist"
  extract_dir=$(mktemp -d)
  trap 'rm -rf "$extract_dir"' EXIT
  copy="${extract_dir}/bundle.tar"
  cp -- "$source" "$copy"
  [ "$(tar -tf "$copy" | sort)" = "$(printf '%s\n' "${BUNDLE_NAMES[@]}" | sort)" ] ||
    die "The join bundle ${source} must hold exactly: ${BUNDLE_NAMES[*]}"
  for name in "${BUNDLE_NAMES[@]}"; do
    if [ -e "${BUNDLE_DIR}/${name}" ] || [ -L "${BUNDLE_DIR}/${name}" ]; then
      echo >&2 "Refusing to overwrite ${BUNDLE_DIR}/${name}"
      existing=1
    fi
  done
  [ "$existing" -eq 0 ] || die "Nothing was imported"

  mkdir "${extract_dir}/files"
  tar -xf "$copy" -C "${extract_dir}/files"
  for name in "${BUNDLE_NAMES[@]}"; do
    if [ ! -f "${extract_dir}/files/${name}" ] || [ -L "${extract_dir}/files/${name}" ] || [ ! -s "${extract_dir}/files/${name}" ]; then
      die "The join bundle entry ${name} is not a regular, non-empty file; nothing was imported"
    fi
  done
  [ -d "$BUNDLE_DIR" ] || install -d -m 0755 -o root -g root "$BUNDLE_DIR"
  for name in "${BUNDLE_NAMES[@]}"; do
    if ! install -m 0600 -o root -g root "${extract_dir}/files/${name}" "${BUNDLE_DIR}/${name}"; then
      (cd "$BUNDLE_DIR" && rm -f -- "${BUNDLE_NAMES[@]}")
      die "Installing ${BUNDLE_DIR}/${name} failed; nothing was imported"
    fi
  done
  echo "Imported the join bundle into ${BUNDLE_DIR}"
}

case "${1:-}" in
  export)
    [ $# -eq 2 ] || usage
    export_bundle "$2"
    ;;
  import)
    [ $# -eq 2 ] || usage
    import_bundle "$2"
    ;;
  check)
    [ $# -eq 1 ] || usage
    check_files
    ;;
  *)
    usage
    ;;
esac
