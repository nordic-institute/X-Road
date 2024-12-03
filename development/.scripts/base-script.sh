#!/bin/bash

isTextColoringEnabled=$(command -v tput >/dev/null && tput setaf 1 &>/dev/null && echo true || echo false)

errorExit() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 1)*** $*(tput sgr0)" 1>&2
  else
    echo "*** $*" 1>&2
  fi
  exit 1
}

log_warn() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 3)*** $*$(tput sgr0)"
  else
    echo "*** $*"
  fi
}

log_info() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 2)$*$(tput sgr0)"
  else
    echo "$*"
  fi
}

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