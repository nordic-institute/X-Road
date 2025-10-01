#!/bin/bash

# X-Road Base Script - Common utilities and functions
# This script should be sourced by other scripts to provide common functionality

# Check if we're running with Bash
if [[ -z "${BASH_VERSION}" ]]; then
  echo "Error: This script requires Bash. Please run with bash."
  exit 1
fi

# Detect color support
isTextColoringEnabled=$(command -v tput >/dev/null && tput setaf 1 &>/dev/null && echo true || echo false)

# Set XROAD_HOME to repository root using the script's location
if [ -z "$XROAD_HOME" ]; then
  # Use the script's location instead of pwd to find repo root
  XROAD_HOME=$(realpath "$(dirname "${BASH_SOURCE[0]}")/../..")
  echo "XROAD_HOME is not set. Setting it to $XROAD_HOME"
fi

# Color codes for enhanced logging (ANSI escape sequences)
if [ "$isTextColoringEnabled" = true ]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[0;34m'
  NC='\033[0m' # No Color
else
  RED=''
  GREEN=''
  YELLOW=''
  BLUE=''
  NC=''
fi

# Legacy function - kept for backward compatibility
errorExit() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 1)*** $*(tput sgr0)" 1>&2
  else
    echo "*** $*" 1>&2
  fi
  exit 1
}

# Enhanced logging functions with consistent formatting
log_error() {
  echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Key-value logging function
function log_kv() {
    # Validate input parameters
    if [ $# -ne 4 ]; then
        echo "Usage: log_kv <key> <value> <key_color_num> <value_color_num>"
        echo "Colors (0-7): black red green yellow blue magenta cyan white"
        return 1
    fi

    local key="$1"
    local value="$2"
    local key_color="$3"
    local value_color="$4"

    if [ "${isTextColoringEnabled}" = true ] && [ -t 1 ]; then
            # Validate color numbers
            if ! [[ "$key_color" =~ ^[0-7]$ ]] || ! [[ "$value_color" =~ ^[0-7]$ ]]; then
                echo "Error: Colors must be numbers 0-7"
                return 1
            fi

            # Print with colors
            tput setaf "$key_color"
            echo -n "$key"
            tput sgr0
            echo -n ": "
            tput setaf "$value_color"
            echo "$value"
            tput sgr0
        else
            # Fallback to plain text if colors not supported
            echo "$key: $value"
        fi
}

# Format duration in seconds to human-readable format
format_duration() {
  local duration=$1
  local minutes=$((duration / 60))
  local seconds=$((duration % 60))
  printf "%dm %ds" $minutes $seconds
}

# Read property from gradle.properties or similar files
read_gradle_property() {
  local property_name="$1"
  local property_file="$2"

  if [[ ! -f "$property_file" ]]; then
    log_error "Properties file not found: $property_file"
    return 1
  fi

  # Read property value, handling comments and empty lines
  grep "^${property_name}=" "$property_file" | cut -d'=' -f2- | tr -d ' \t'
}