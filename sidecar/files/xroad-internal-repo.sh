#!/bin/bash
# Turns the bind-mounted tree-built DEBs (/tmp/packages) into a temporary local
# apt repository for an internal image build, and tears it down after install.
# External builds install from the signed repository registered in the
# repo-external stage, so both phases are no-ops for them.
set -euo pipefail

phase="$1"
package_source="$2"

[[ "$package_source" == "internal" ]] || exit 0

case "$phase" in
  provision)
    apt-get -qq install dpkg-dev
    cp -r /tmp/packages /tmp/repo
    cd /tmp/repo && dpkg-scanpackages -m . >Packages
    echo "deb [trusted=yes] file:/tmp/repo /" >/etc/apt/sources.list.d/xroad.list
    ;;
  cleanup)
    # the file: repo does not outlive the build, so its source list must go with
    # it - a dangling trusted=yes source would fail every later apt-get update
    apt-get -qq remove dpkg-dev
    apt-get -qq autoremove
    rm -rf /tmp/repo /etc/apt/sources.list.d/xroad.list
    ;;
  *)
    echo "unknown phase: $phase (expected provision or cleanup)" >&2
    exit 1
    ;;
esac
