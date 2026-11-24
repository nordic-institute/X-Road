#!/usr/bin/env bash
# Result helpers for shell test scripts. Source this file; do not run it.
#
# The caller sets FAILURES=0 and may define on_check_failure(), which runs after each failed check.
# Output uses log_success and log_error from base-script.sh when the caller sourced it, plain echo otherwise.

pass() {
  if declare -F log_success >/dev/null; then log_success "  ok: $1"; else echo "  ok: $1"; fi
}

fail() {
  if declare -F log_error >/dev/null; then log_error "  FAILED: $1"; else echo "  FAILED: $1" >&2; fi
  FAILURES=$((FAILURES + 1))
  if declare -F on_check_failure >/dev/null; then on_check_failure; fi
}

check() {
  local description="$1"
  shift
  if "$@"; then pass "${description}"; else fail "${description}"; fi
}
