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
if [ -z "${XROAD_HOME:-}" ]; then
  # Use the script's location instead of pwd to find repo root
  # This script is at scripts/lib/base-script.sh, so go up two levels
  XROAD_HOME=$(realpath "$(dirname "${BASH_SOURCE[0]}")/../..")
  echo "XROAD_HOME is not set. Setting it to $XROAD_HOME"
fi
export XROAD_HOME

# Color codes for enhanced logging (ANSI escape sequences)
if [ "$isTextColoringEnabled" = true ]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[0;34m'
  CYAN='\033[0;36m'
  NC='\033[0m' # No Color
else
  RED=''
  GREEN=''
  YELLOW=''
  BLUE=''
  CYAN=''
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

# Ensure the local Docker registry container (xrd-registry / registry:2 on port 5555) is running.
# States: running → no-op; stopped → start; absent → create.
ensure_local_registry() {
  local container_name="xrd-registry"

  if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
    log_info "Container ${container_name} is already running"
    return 0
  fi

  if docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
    log_info "Starting existing container ${container_name}"
    docker start "${container_name}"
  else
    log_info "Creating and starting new container ${container_name}"
    docker run -d -p 5555:5000 --name "${container_name}" registry:2
  fi
}

# Look up the Gradle project path for a service name by reading service-config.csv.
# Usage: resolve_module_gradle_path <service_name> <csv_path>
# Prints the gradle_path field and returns 0, or returns 1 if not found.
resolve_module_gradle_path() {
  local service="$1"
  local csv="$2"
  local svc_name gradle_path

  while IFS=',' read -r svc_name _ gradle_path _; do
    if [[ "${svc_name}" == "${service}" ]]; then
      echo "${gradle_path}"
      return 0
    fi
  done < <(tail -n +2 "${csv}")

  return 1
}

# Populate global MIRROR_BUILD_ARGS with Docker buildx --build-arg / --secret / --build-context
# flags for the configured mirrors.
#
# Usage: build_mirror_args <root_dir> [no_mirror]
#   root_dir   — repository root (used to locate deployment/.scripts)
#   no_mirror  — "true" to skip all mirror args except the mirror-scripts build context
#                (default: "false")
#
# Reads environment variables:
#   XROAD_MIRROR_DOCKER_URL, XROAD_MIRROR_GITHUB_URL, XROAD_MIRROR_K8S_URL,
#   XROAD_MIRROR_UBUNTU_URL, XROAD_MIRROR_USERNAME, XROAD_MIRROR_TOKEN
build_mirror_args() {
  local root_dir="$1"
  local no_mirror="${2:-false}"

  MIRROR_BUILD_ARGS=(--build-context "mirror-scripts=${root_dir}/deployment/.scripts")

  log_info "=== Mirror Configuration ==="
  log_info "NO_MIRROR flag: ${no_mirror}"
  log_info "XROAD_MIRROR_DOCKER_URL: ${XROAD_MIRROR_DOCKER_URL:-<not set>}"
  log_info "XROAD_MIRROR_UBUNTU_URL: ${XROAD_MIRROR_UBUNTU_URL:-<not set>}"
  log_info "XROAD_MIRROR_USERNAME: ${XROAD_MIRROR_USERNAME:-<not set>}"
  if [[ -n "${XROAD_MIRROR_TOKEN:-}" ]]; then
    log_info "XROAD_MIRROR_TOKEN: <present>"
  else
    log_info "XROAD_MIRROR_TOKEN: <not set>"
  fi

  if [[ "${no_mirror}" != "true" ]] && [[ -n "${XROAD_MIRROR_DOCKER_URL:-}" ]]; then
    MIRROR_BUILD_ARGS+=(--build-arg "DOCKER_REGISTRY=${XROAD_MIRROR_DOCKER_URL}")
    log_success "Docker mirror ENABLED: ${XROAD_MIRROR_DOCKER_URL}"
  fi

  if [[ "${no_mirror}" != "true" ]] && [[ -n "${XROAD_MIRROR_GITHUB_URL:-}" ]]; then
    MIRROR_BUILD_ARGS+=(--build-arg "GITHUB_URL=${XROAD_MIRROR_GITHUB_URL}")
    log_success "GitHub mirror ENABLED: ${XROAD_MIRROR_GITHUB_URL}"
  fi

  if [[ "${no_mirror}" != "true" ]] && [[ -n "${XROAD_MIRROR_K8S_URL:-}" ]]; then
    MIRROR_BUILD_ARGS+=(--build-arg "KUBECTL_DIST_URL=${XROAD_MIRROR_K8S_URL}")
    log_success "kubectl mirror ENABLED: ${XROAD_MIRROR_K8S_URL}"
  fi

  if [[ "${no_mirror}" != "true" ]] && [[ -n "${XROAD_MIRROR_UBUNTU_URL:-}" ]] && \
     [[ -n "${XROAD_MIRROR_USERNAME:-}" ]] && [[ -n "${XROAD_MIRROR_TOKEN:-}" ]]; then
    MIRROR_BUILD_ARGS+=(--build-arg XROAD_MIRROR_URL="${XROAD_MIRROR_UBUNTU_URL}")
    log_success "APT mirror ENABLED: ${XROAD_MIRROR_UBUNTU_URL}"
  else
    log_warn "APT mirror DISABLED (using public repos)"
  fi

  if [[ "${no_mirror}" != "true" ]] && [[ -n "${XROAD_MIRROR_USERNAME:-}" ]] && \
     [[ -n "${XROAD_MIRROR_TOKEN:-}" ]]; then
    MIRROR_BUILD_ARGS+=(
      --build-arg XROAD_MIRROR_USER="${XROAD_MIRROR_USERNAME}"
      --secret "id=mirror_token,env=XROAD_MIRROR_TOKEN"
    )
  fi

  log_info "MIRROR_BUILD_ARGS: ${MIRROR_BUILD_ARGS[*]}"
  echo
}

# Set JAVA_HOME by resolving javac on PATH when JAVA_HOME is not already set.
# On macOS, uses the -f flag of Python's os.path.realpath equivalent via a
# POSIX-safe traversal instead of readlink -f (which is absent on macOS).
resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    return 0
  fi

  local javac_bin
  javac_bin="$(command -v javac)" || {
    log_error "javac not found on PATH and JAVA_HOME is not set"
    return 1
  }

  # Resolve symlinks portably (readlink -f not available on macOS bash 3.2)
  local resolved="$javac_bin"
  while [[ -L "$resolved" ]]; do
    local link_target
    link_target="$(readlink "$resolved")"
    case "$link_target" in
      /*) resolved="$link_target" ;;
      *)  resolved="$(dirname "$resolved")/${link_target}" ;;
    esac
  done

  JAVA_HOME="$(dirname "$(dirname "$resolved")")"
  export JAVA_HOME
  PATH="${JAVA_HOME}/bin:${PATH}"
  export PATH
}

