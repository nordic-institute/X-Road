#!/bin/sh

die() {
  echo "ERROR: $1" >&2
  echo "Program will exit." >&2
  exit 1
}

warn() {
  echo "WARNING: $1" >&2
}

ruby_version=$(ruby -v)
if [ $? != 0 ]; then
  warn "Ruby is not installed"
fi

if ! echo "$ruby_version" | egrep -q  ^jruby\ 1\.7; then
  warn "Ruby version 'jruby-1.7.x' is supported, but used is: \n\t$ruby_version"
fi

# Install Rubocop if not present
rubocop -v
if [ $? != 0 ]; then
  # XXX: rubocop-0.32.1 seems to contain fatal bug, 0.32.0 is proven to be working here.
  gem install rubocop -v 0.32.0 || warn "Failed to install Ruby gem 'rubocop'."
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
