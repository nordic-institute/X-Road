#!/bin/bash

# Artifactory Credential Resolver (Host Side)
#
# Usage:
# source deployment/.scripts/resolve-artifactory-args.sh
# resolve_artifactory_args

# Source base script for logging and property reading utilities
if [ -n "$BASH_SOURCE" ]; then
    RESOLVE_SCRIPT_DIR=$(dirname "${BASH_SOURCE[0]}")
elif [ -n "$ZSH_VERSION" ]; then
    RESOLVE_SCRIPT_DIR=$(dirname "${(%):-%x}")
else
    RESOLVE_SCRIPT_DIR=$(dirname "$0")
fi
source "${RESOLVE_SCRIPT_DIR}/../../.scripts/base-script.sh"

# Resolve Artifactory credentials from gradle-local.properties
resolve_artifactory_args() {
  local gradle_local_properties="${XROAD_HOME}/src/gradle-local.properties"
  
  if [[ -f "$gradle_local_properties" ]]; then
    local url=$(read_gradle_property "artifactoryUbuntuRepoUrl" "$gradle_local_properties")
    local user=$(read_gradle_property "artifactoryUsername" "$gradle_local_properties")
    local token=$(read_gradle_property "artifactoryToken" "$gradle_local_properties")

    if [[ -n "$url" ]] && [[ -n "$user" ]] && [[ -n "$token" ]]; then
      export ARTIFACTORY_URL="$url"
      export ARTIFACTORY_USER="$user"
      export ARTIFACTORY_TOKEN="$token"
      
      # Read CA certificate bundle if it exists
      local ca_file="${XROAD_HOME}/deployment/.scripts/artifactory-ca.crt"
      if [[ -f "$ca_file" ]]; then
        export ARTIFACTORY_CA_CERT=$(cat "$ca_file")
        log_info "Loaded Artifactory CA certificate bundle"
      fi

      log_info "Resolved Artifactory configuration from gradle-local.properties"
    fi
  else
    log_warn "gradle-local.properties not found at $gradle_local_properties. Skipping Artifactory credential resolution."
  fi
}
