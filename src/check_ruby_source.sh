#!/bin/sh

die() {
  echo "ERROR: $1" >&2
  echo "Program will exit." >&2
  exit 1
}

warn() {
  echo "WARNING: $1" >&2
}

jruby_version=$(jruby -v)
if [ $? != 0 ]; then
  warn "JRuby is not installed"
fi

# Install/upgrade Rubocop if necessary
rubocop_version=$(rubocop -v)
if [ -n "$rubocop_version" ] && [ "$rubocop_version" != "0.52.1" ]; then
  echo "Uninstall old rubocop $rubocop_version"
  jgem uninstall rubocop --silent || warn "Failed to uninstall Ruby gem 'rubocop'."
fi
if [ "$rubocop_version" != "0.52.1" ]; then
  echo "Installing rubocop"
  jgem install rubocop -v 0.52.1 || warn "Failed to install Ruby gem 'rubocop'."
fi

if [ "$#" -eq 0 ]; then
  die "Script must have subproject path as an argument!"
fi

SUBPROJECT_DIR=$1
BUILD_DIR=$SUBPROJECT_DIR/build
REPORT_FILE=$BUILD_DIR/rubocop_report.txt

# Create build dir if it does not yet exist.
mkdir -p $BUILD_DIR

echo "Source inspection report will be generated into '$REPORT_FILE'"

rubocop --fail-level error $SUBPROJECT_DIR/app/ $SUBPROJECT_DIR/lib/ >$REPORT_FILE

if [ $? != 0 ]; then
  echo "There were Ruby syntax errors in subproject '$SUBPROJECT_DIR'." >&2
  echo "Check '$REPORT_FILE' for details." >&2
fi
