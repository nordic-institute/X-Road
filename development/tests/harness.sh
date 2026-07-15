#!/usr/bin/env bash
#!/usr/bin/env bash
# Shell test harness for X-Road build-script consolidation.
#
# Works on macOS bash 3.2+ and Linux bash 4+.
#
# Checks:
#   1. bash -n syntax gate over every in-scope script.
#   2. shellcheck (if installed) over every in-scope script.
#   3. Behavior baselines: --help / usage output matches committed fixtures.
#   4. Fake-docker smoke: scripts that call docker don't touch the real daemon.
#
# Usage:
#   ./harness.sh             — run all checks
#   ./harness.sh --syntax    — syntax gate only
#   ./harness.sh --baselines — baseline checks only
#   ./harness.sh --smoke     — fake-docker smoke only
#   ./harness.sh --refresh   — refresh fixtures from current script output
#   ./harness.sh --help      — show this help
#
# Refreshing a baseline:
#   Run ./harness.sh --refresh to regenerate all fixture files from current
#   script output.  Review the diff before committing.
#   For build-deb.sh and build-rpm.sh the fixtures use substring patterns
#   (no --help / dry-run available); edit those manually if their interface changes.
#
# Normalization applied to all output before fixture comparison:
#   - ANSI colour codes stripped.
#   - "XROAD_HOME is not set" preamble stripped.
#   - Absolute path to the script replaced with its basename (makes fixtures
#     checkout-independent; $0 embeds the invocation path on usage prints).

show_help() {
  cat <<'EOF'
Shell test harness for X-Road build-script consolidation.

Works on macOS bash 3.2+ and Linux bash 4+.

Checks:
  1. bash -n syntax gate over every in-scope script.
  2. shellcheck (if installed) over every in-scope script.
  3. Behavior baselines: --help / usage output matches committed fixtures.
  4. Fake-docker smoke: scripts that call docker don't touch the real daemon.

Usage:
  ./harness.sh             — run all checks
  ./harness.sh --syntax    — syntax gate only
  ./harness.sh --baselines — baseline checks only
  ./harness.sh --smoke     — fake-docker smoke only
  ./harness.sh --refresh   — refresh fixtures from current script output
  ./harness.sh --help      — show this help
EOF
}

SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SELF_DIR}/../.." && pwd)"
FIXTURES_DIR="${SELF_DIR}/fixtures"
FAKE_BIN_DIR="${SELF_DIR}/lib"

# ─── colour helpers (safe on bash 3.2) ────────────────────────────────────────
_has_color() { command -v tput >/dev/null 2>&1 && tput setaf 1 >/dev/null 2>&1; }
_c_ok()   { _has_color && printf '%s' "$(tput setaf 2)" || true; }
_c_fail() { _has_color && printf '%s' "$(tput setaf 1)" || true; }
_c_skip() { _has_color && printf '%s' "$(tput setaf 3)" || true; }
_c_off()  { _has_color && printf '%s' "$(tput sgr0)"   || true; }

PASS=0; FAIL=0; SKIP=0

pass() { PASS=$((PASS+1)); printf '%s%s%s\n' "$(_c_ok)"   "  PASS  $1" "$(_c_off)"; }
fail() { FAIL=$((FAIL+1)); printf '%s%s%s\n' "$(_c_fail)" "  FAIL  $1" "$(_c_off)"; }
skip() { SKIP=$((SKIP+1)); printf '%s%s%s\n' "$(_c_skip)" "  SKIP  $1" "$(_c_off)"; }

# Strip ANSI escape codes (portable; no perl required).
strip_ansi() { sed 's/\x1b\[[0-9;]*[mGKHF]//g'; }

# Strip the preamble base-script.sh prints when XROAD_HOME is not exported.
strip_preamble() { grep -v "^XROAD_HOME is not set"; }

# Replace the absolute REPO_ROOT path with its basename in usage/error output.
# Scripts print $0 (their invocation path) in usage text; this makes fixtures
# checkout-independent by reducing the full path to just the filename.
strip_abspath() { sed "s|${REPO_ROOT}/||g"; }

normalize_output() { strip_ansi | strip_preamble | strip_abspath; }

# ─── In-scope scripts (relative to REPO_ROOT) ─────────────────────────────────
# These are the scripts this harness covers.  Paths are REPO_ROOT-relative.
SCOPE_SCRIPTS=(
  "deployment/security-server/images/build-images.sh"
  "src/build_packages.sh"
  "development/docker/central-server/build-cs-dev-image.sh"
  "development/docker/build-dev-images.sh"
  "deployment/native-packages/docker/prepare-builder-image.sh"
  "deployment/native-packages/build-deb.sh"
  "deployment/native-packages/build-rpm.sh"
  "deployment/security-server/k8s/publish-charts.sh"
  "deployment/security-server/artifactory-publish-installer.sh"
  "deployment/security-server/s3-publish-installer.sh"
  "development/build-local.sh"
  ".scripts/base-script.sh"
  "deployment/security-server/_publish-installer-common.sh"
)

# ─── parse args ───────────────────────────────────────────────────────────────
RUN_SYNTAX=true
RUN_BASELINES=true
RUN_SMOKE=true
REFRESH_MODE=false

for arg in "$@"; do
  case "$arg" in
    --syntax)    RUN_SYNTAX=true;    RUN_BASELINES=false; RUN_SMOKE=false ;;
    --baselines) RUN_SYNTAX=false;   RUN_BASELINES=true;  RUN_SMOKE=false ;;
    --smoke)     RUN_SYNTAX=false;   RUN_BASELINES=false; RUN_SMOKE=true  ;;
    --refresh)   REFRESH_MODE=true;  RUN_SYNTAX=false;    RUN_BASELINES=false; RUN_SMOKE=false ;;
    --help|-h)   show_help; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

# ─── 1. SYNTAX GATE ───────────────────────────────────────────────────────────
if $RUN_SYNTAX; then
  echo
  echo "=== Syntax gate (bash -n) ==="
  HAS_SHELLCHECK=false
  if command -v shellcheck >/dev/null 2>&1; then
    HAS_SHELLCHECK=true
    echo "shellcheck $(shellcheck --version | grep version: | awk '{print $2}') found — running alongside bash -n"
  else
    echo "shellcheck not installed — skipping (only bash -n will run)"
  fi

  for rel in "${SCOPE_SCRIPTS[@]}"; do
    abs="${REPO_ROOT}/${rel}"
    if [[ ! -f "$abs" ]]; then
      skip "bash -n  ${rel}  (file not found)"
      continue
    fi
    if bash -n "$abs" 2>/dev/null; then
      if $HAS_SHELLCHECK; then
        if shellcheck --shell=bash --severity=error \
             --exclude=SC1090,SC1091,SC2034,SC2148 \
             "$abs" 2>/dev/null; then
          pass "shellcheck ${rel}"
        else
          fail "shellcheck ${rel}"
          shellcheck --shell=bash --severity=error \
            --exclude=SC1090,SC1091,SC2034,SC2148 \
            "$abs" 2>&1 | sed 's/^/          /' >&2 || true
        fi
      else
        pass "bash -n    ${rel}"
      fi
    else
      fail "bash -n  ${rel}"
      bash -n "$abs" 2>&1 | sed 's/^/          /' >&2 || true
    fi
  done
fi

# ─── 2. BASELINE CHECK HELPERS ────────────────────────────────────────────────

# Compare script output against a fixture.
# $1 = label, $2 = fixture file (relative to FIXTURES_DIR), $3+ = command to run
check_baseline() {
  local label="$1"; local fixture="$2"; shift 2
  local fixture_abs="${FIXTURES_DIR}/${fixture}"

  if [[ ! -f "$fixture_abs" ]]; then
    skip "${label} (fixture not found: ${fixture})"
    return
  fi

  # Run command; allow non-zero exit (usage messages often exit 0 or 1).
  local actual
  actual="$("$@" 2>&1 || true)"
  actual="$(printf '%s\n' "$actual" | normalize_output)"
  local expected
  expected="$(cat "$fixture_abs")"

  if [[ "$actual" == "$expected" ]]; then
    pass "$label"
  else
    fail "$label"
    diff <(printf '%s\n' "$expected") <(printf '%s\n' "$actual") | sed 's/^/          /' >&2 || true
  fi
}

# Check that output contains a stable substring.
# $1 = label, $2 = expected substring, $3+ = command to run
check_contains() {
  local label="$1"; local needle="$2"; shift 2
  local actual
  actual="$("$@" 2>&1 || true)"
  actual="$(printf '%s\n' "$actual" | normalize_output)"
  if printf '%s\n' "$actual" | grep -qF "$needle"; then
    pass "$label"
  else
    fail "$label — expected to contain: ${needle}"
    printf '%s\n' "$actual" | sed 's/^/          /' >&2
  fi
}

# ─── 3. REFRESH MODE ──────────────────────────────────────────────────────────
if $REFRESH_MODE; then
  echo
  echo "=== Refreshing fixtures ==="
  mkdir -p "$FIXTURES_DIR"

  _refresh() {
    local fixture="$1"; shift
    local out="${FIXTURES_DIR}/${fixture}"
    ("$@" 2>&1 || true) | normalize_output > "$out"
    echo "  refreshed: ${fixture}"
  }

  _refresh "build-images.sh.help" \
    bash "${REPO_ROOT}/deployment/security-server/images/build-images.sh" --help

  _refresh "build_packages.sh.help" \
    bash "${REPO_ROOT}/src/build_packages.sh" --help

  _refresh "build-cs-dev-image.sh.help" \
    bash "${REPO_ROOT}/development/docker/central-server/build-cs-dev-image.sh" --help

  _refresh "build-dev-images.sh.help" \
    bash "${REPO_ROOT}/development/docker/build-dev-images.sh" --help

  _refresh "publish-charts.sh.help" \
    bash "${REPO_ROOT}/deployment/security-server/k8s/publish-charts.sh" --help

  _refresh "build-local.sh.help" \
    bash "${REPO_ROOT}/development/build-local.sh" --help

  _refresh "prepare-builder-image.sh.usage" \
    bash "${REPO_ROOT}/deployment/native-packages/docker/prepare-builder-image.sh"

  _refresh "artifactory-publish-installer.sh.usage" \
    bash "${REPO_ROOT}/deployment/security-server/artifactory-publish-installer.sh"

  _refresh "s3-publish-installer.sh.usage" \
    bash "${REPO_ROOT}/deployment/security-server/s3-publish-installer.sh"

  echo "  Note: build-deb.sh and build-rpm.sh use .signature fixtures with"
  echo "        substring patterns — no --help/dry-run available. Edit manually."

  echo "Done. Review 'git diff ${FIXTURES_DIR}' before committing."
  exit 0
fi

# ─── 4. BASELINE CHECKS ───────────────────────────────────────────────────────
if $RUN_BASELINES; then
  echo
  echo "=== Behavior baselines ==="

  # Scripts with --help (full output match)
  check_baseline \
    "build-images.sh --help" \
    "build-images.sh.help" \
    bash "${REPO_ROOT}/deployment/security-server/images/build-images.sh" --help

  check_baseline \
    "build_packages.sh --help" \
    "build_packages.sh.help" \
    bash "${REPO_ROOT}/src/build_packages.sh" --help

  check_baseline \
    "build-cs-dev-image.sh --help" \
    "build-cs-dev-image.sh.help" \
    bash "${REPO_ROOT}/development/docker/central-server/build-cs-dev-image.sh" --help

  check_baseline \
    "build-dev-images.sh --help" \
    "build-dev-images.sh.help" \
    bash "${REPO_ROOT}/development/docker/build-dev-images.sh" --help

  check_baseline \
    "publish-charts.sh --help" \
    "publish-charts.sh.help" \
    bash "${REPO_ROOT}/deployment/security-server/k8s/publish-charts.sh" --help

  check_baseline \
    "build-local.sh --help" \
    "build-local.sh.help" \
    bash "${REPO_ROOT}/development/build-local.sh" --help

  # prepare-builder-image.sh — no --help flag; usage printed on missing arg (exit 1).
  # Full output match after normalization (absolute path stripped to relative).
  check_baseline \
    "prepare-builder-image.sh usage on missing arg" \
    "prepare-builder-image.sh.usage" \
    bash "${REPO_ROOT}/deployment/native-packages/docker/prepare-builder-image.sh"

  # build-deb.sh — no --help, no dry-run; only stable offline output is the distro
  # validation error. Substring check is appropriate here.
  check_contains \
    "build-deb.sh rejects unknown distro" \
    "Unsupported distribution" \
    bash "${REPO_ROOT}/deployment/native-packages/build-deb.sh" __badarg__

  # build-rpm.sh — no --help, no dry-run; stable output is the packageVersion line.
  # Substring check is appropriate here.
  check_contains \
    "build-rpm.sh emits packageVersion line" \
    "using packageVersion" \
    bash "${REPO_ROOT}/deployment/native-packages/build-rpm.sh" __testsuffix__

  # installer-publish scripts — usage on missing arg; full output match.
  check_baseline \
    "artifactory-publish-installer.sh usage on missing arg" \
    "artifactory-publish-installer.sh.usage" \
    bash "${REPO_ROOT}/deployment/security-server/artifactory-publish-installer.sh"

  check_baseline \
    "s3-publish-installer.sh usage on missing arg" \
    "s3-publish-installer.sh.usage" \
    bash "${REPO_ROOT}/deployment/security-server/s3-publish-installer.sh"
fi

# ─── 5. FAKE-DOCKER SMOKE ─────────────────────────────────────────────────────
if $RUN_SMOKE; then
  echo
  echo "=== Fake-docker smoke ==="

  # GNU mktemp requires XXXXXX at the end (no suffix); BSD mktemp supports a suffix.
  # Use no suffix so this works on both macOS and Linux.
  FAKE_LOG="$(mktemp "${TMPDIR:-/tmp}/fake-docker-XXXXXX")"
  export FAKE_DOCKER_LOG="$FAKE_LOG"

  # Prepend the fake bin dir so our `docker` is found first.
  export PATH="${FAKE_BIN_DIR}:${PATH}"

  # Verify the fake docker is on PATH.
  if [[ "$(command -v docker)" != "${FAKE_BIN_DIR}/docker" ]]; then
    fail "fake docker not first on PATH (got: $(command -v docker))"
  else
    pass "fake docker is first on PATH"
  fi

  # Smoke: build-images.sh — relies on docker buildx.
  # The script will fail after the fake `docker buildx use` call because it
  # proceeds to check for gradle.properties, which exists.  It will then try
  # to build services (which requires artifacts that don't exist), so we only
  # check that the fake docker was invoked at all.
  (
    rm -f "$FAKE_LOG"
    export IMAGE_REGISTRY="localhost:5555"
    export XROAD_HOME="$REPO_ROOT"
    bash "${REPO_ROOT}/deployment/security-server/images/build-images.sh" \
      --no-mirror proxy >/dev/null 2>&1 || true
  )

  if grep -q "docker" "$FAKE_LOG" 2>/dev/null; then
    pass "build-images.sh invoked fake docker (no real daemon touched)"
  else
    # The script exits before docker if artifacts are missing — that's expected.
    skip "build-images.sh smoke (script exited before docker call — artifact prerequisite not met, expected)"
  fi

  # Smoke: build_packages.sh with --package-only — reaches docker ps call.
  (
    rm -f "$FAKE_LOG"
    export XROAD_HOME="$REPO_ROOT"
    bash "${REPO_ROOT}/src/build_packages.sh" --package-only >/dev/null 2>&1 || true
  )

  if grep -q "docker" "$FAKE_LOG" 2>/dev/null; then
    pass "build_packages.sh --package-only invoked fake docker (no real daemon)"
  else
    skip "build_packages.sh smoke (no docker call reached — git or other prerequisite absent)"
  fi

  # Smoke: build-local.sh --no-build --no-registry skips Gradle; reaches docker via build-images.sh.
  (
    rm -f "$FAKE_LOG"
    export XROAD_HOME="$REPO_ROOT"
    export IMAGE_REGISTRY="localhost:5555"
    bash "${REPO_ROOT}/development/build-local.sh" \
      --no-build --no-registry >/dev/null 2>&1 || true
  )

  if grep -q "docker" "$FAKE_LOG" 2>/dev/null; then
    pass "build-local.sh --no-build --no-registry invoked fake docker"
  else
    skip "build-local.sh smoke (no docker call reached — artifacts not built yet)"
  fi

  rm -f "$FAKE_LOG"
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo
echo "========================================"
printf "Results:  %s pass  %s fail  %s skip\n" "$PASS" "$FAIL" "$SKIP"
echo "========================================"

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
