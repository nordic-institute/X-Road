#!/bin/bash

die() {
  echo "ERROR: $1" >&2
  echo "Program will exit." >&2
  exit 1
}

warn() {
  echo "WARNING: $1" >&2
}

if ! command -v jruby >/dev/null; then
  warn "JRuby is not installed"
fi

# Install/upgrade Rubocop if necessary
rubocop_version="$(rubocop -v)"
pat="^0\.81\.[0-9]+"
if [ -n "$rubocop_version" ] && [[ ! "$rubocop_version" =~ $pat ]]; then
  echo "Uninstall old rubocop $rubocop_version"
  jgem uninstall rubocop --silent || warn "Failed to uninstall Ruby gem 'rubocop'."
fi
if [[ ! "$rubocop_version" =~ $pat ]]; then
  echo "Installing rubocop"
  jgem install parallel:1.19 rubocop:0.81 || warn "Failed to install Ruby gem 'rubocop'."
fi

if [ "$#" -eq 0 ]; then
  die "Script must have subproject path as an argument!"
fi

SUBPROJECT_DIR=$1
BUILD_DIR=$SUBPROJECT_DIR/build
REPORT_FILE=$BUILD_DIR/rubocop-result.json

# Create build dir if it does not yet exist.
mkdir -p "$BUILD_DIR"

echo "Source inspection report will be generated into '$REPORT_FILE'"

if ! rubocop --format json --fail-level error "$SUBPROJECT_DIR/app/" "$SUBPROJECT_DIR/lib/" >"$REPORT_FILE"; then
  warn "There were Ruby syntax errors in subproject '$SUBPROJECT_DIR'. Check '$REPORT_FILE' for details."
  exit 1
fi
