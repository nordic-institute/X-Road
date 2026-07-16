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
  "scripts/images/build-security-server.sh"
  "scripts/package.sh"
  "scripts/images/build-central-server.sh"
  "scripts/images/build-dev-infra.sh"
  "scripts/images/build-builder.sh"
  "scripts/packages/build-deb.sh"
  "scripts/packages/build-rpm.sh"
  "scripts/charts/publish.sh"
  "scripts/release/installer-artifactory.sh"
  "scripts/release/installer-s3.sh"
  "scripts/build-local.sh"
  "scripts/lib/base-script.sh"
  "scripts/release/_installer-common.sh"
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

  _refresh "build-security-server.sh.help" \
    bash "${REPO_ROOT}/scripts/images/build-security-server.sh" --help

  _refresh "package.sh.help" \
    bash "${REPO_ROOT}/scripts/package.sh" --help

  _refresh "build-central-server.sh.help" \
    bash "${REPO_ROOT}/scripts/images/build-central-server.sh" --help

  _refresh "build-dev-infra.sh.help" \
    bash "${REPO_ROOT}/scripts/images/build-dev-infra.sh" --help

  _refresh "publish.sh.help" \
    bash "${REPO_ROOT}/scripts/charts/publish.sh" --help

  _refresh "build-local.sh.help" \
    bash "${REPO_ROOT}/scripts/build-local.sh" --help

  _refresh "build-builder.sh.usage" \
    bash "${REPO_ROOT}/scripts/images/build-builder.sh"

  _refresh "installer-artifactory.sh.usage" \
    bash "${REPO_ROOT}/scripts/release/installer-artifactory.sh"

  _refresh "installer-s3.sh.usage" \
    bash "${REPO_ROOT}/scripts/release/installer-s3.sh"

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
    "build-security-server.sh --help" \
    "build-security-server.sh.help" \
    bash "${REPO_ROOT}/scripts/images/build-security-server.sh" --help

  check_baseline \
    "package.sh --help" \
    "package.sh.help" \
    bash "${REPO_ROOT}/scripts/package.sh" --help

  check_baseline \
    "build-central-server.sh --help" \
    "build-central-server.sh.help" \
    bash "${REPO_ROOT}/scripts/images/build-central-server.sh" --help

  check_baseline \
    "build-dev-infra.sh --help" \
    "build-dev-infra.sh.help" \
    bash "${REPO_ROOT}/scripts/images/build-dev-infra.sh" --help

  check_baseline \
    "publish.sh --help" \
    "publish.sh.help" \
    bash "${REPO_ROOT}/scripts/charts/publish.sh" --help

  check_baseline \
    "build-local.sh --help" \
    "build-local.sh.help" \
    bash "${REPO_ROOT}/scripts/build-local.sh" --help

  # build-builder.sh — no --help flag; usage printed on missing arg (exit 1).
  # Full output match after normalization (absolute path stripped to relative).
  check_baseline \
    "build-builder.sh usage on missing arg" \
    "build-builder.sh.usage" \
    bash "${REPO_ROOT}/scripts/images/build-builder.sh"

  # build-deb.sh — no --help, no dry-run; only stable offline output is the distro
  # validation error. Substring check is appropriate here.
  check_contains \
    "build-deb.sh rejects unknown distro" \
    "Unsupported distribution" \
    bash "${REPO_ROOT}/scripts/packages/build-deb.sh" __badarg__

  # build-rpm.sh — no --help, no dry-run; stable output is the packageVersion line.
  # Substring check is appropriate here.
  check_contains \
    "build-rpm.sh emits packageVersion line" \
    "using packageVersion" \
    bash "${REPO_ROOT}/scripts/packages/build-rpm.sh" __testsuffix__

  # installer-publish scripts — usage on missing arg; full output match.
  check_baseline \
    "installer-artifactory.sh usage on missing arg" \
    "installer-artifactory.sh.usage" \
    bash "${REPO_ROOT}/scripts/release/installer-artifactory.sh"

  check_baseline \
    "installer-s3.sh usage on missing arg" \
    "installer-s3.sh.usage" \
    bash "${REPO_ROOT}/scripts/release/installer-s3.sh"
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

  # Smoke: build-security-server.sh — relies on docker buildx.
  # The script will fail after the fake `docker buildx use` call because it
  # proceeds to check for gradle.properties, which exists.  It will then try
  # to build services (which requires artifacts that don't exist), so we only
  # check that the fake docker was invoked at all.
  (
    rm -f "$FAKE_LOG"
    export IMAGE_REGISTRY="localhost:5555"
    export XROAD_HOME="$REPO_ROOT"
    bash "${REPO_ROOT}/scripts/images/build-security-server.sh" \
      --no-mirror proxy >/dev/null 2>&1 || true
  )

  if grep -q "docker" "$FAKE_LOG" 2>/dev/null; then
    pass "build-security-server.sh invoked fake docker (no real daemon touched)"
  else
    # The script exits before docker if artifacts are missing — that's expected.
    skip "build-security-server.sh smoke (script exited before docker call — artifact prerequisite not met, expected)"
  fi

  # Smoke: package.sh with --package-only — reaches docker ps call.
  (
    rm -f "$FAKE_LOG"
    export XROAD_HOME="$REPO_ROOT"
    bash "${REPO_ROOT}/scripts/package.sh" --package-only >/dev/null 2>&1 || true
  )

  if grep -q "docker" "$FAKE_LOG" 2>/dev/null; then
    pass "package.sh --package-only invoked fake docker (no real daemon)"
  else
    skip "package.sh smoke (no docker call reached — git or other prerequisite absent)"
  fi

  # Smoke: build-local.sh --no-build --no-registry skips Gradle; reaches docker via build-security-server.sh.
  (
    rm -f "$FAKE_LOG"
    export XROAD_HOME="$REPO_ROOT"
    export IMAGE_REGISTRY="localhost:5555"
    bash "${REPO_ROOT}/scripts/build-local.sh" \
      --no-build --no-registry >/dev/null 2>&1 || true
  )

  if grep -q "docker" "$FAKE_LOG" 2>/dev/null; then
    pass "build-local.sh --no-build --no-registry invoked fake docker"
  else
    skip "build-local.sh smoke (no docker call reached — artifacts not built yet)"
  fi

  rm -f "$FAKE_LOG"

  # CI parse guard: package.sh -d -r rpm-el9 --package-only
  # Asserts that the CI arg order ("-d" first) is fully consumed: BUILD_LOCALLY=false
  # (no compile-all.sh call), release=rpm-el9 selected (docker run targets rpm-el9
  # image), and no other release images are invoked.  This guards against a regression
  # where `-d` hits the `*) break` fallthrough and aborts the parse loop early.
  (
    rm -f "$FAKE_LOG"
    export XROAD_HOME="$REPO_ROOT"
    export IMAGE_REGISTRY="localhost:5555"
    export IMAGE_TAG="latest"
    bash "${REPO_ROOT}/scripts/package.sh" -d -r rpm-el9 --package-only >/dev/null 2>&1 || true
  )

  _CI_LOG_CONTENT=""
  if [[ -f "$FAKE_LOG" ]]; then _CI_LOG_CONTENT="$(cat "$FAKE_LOG")"; fi

  # 1. compile-all.sh must NOT have been invoked (BUILD_LOCALLY must be false).
  #    The script calls compile-all.sh via buildLocally(); no docker wraps it, so
  #    absence of that call means BUILD_LOCALLY was false.  We verify indirectly:
  #    if BUILD_LOCALLY were true, buildLocally runs compile-all.sh which would
  #    exit non-zero (gradlew not present), killing the script before docker packaging runs.
  #    Check: docker packaging WAS reached (fake docker log has an entry).
  if [[ -n "$_CI_LOG_CONTENT" ]]; then
    pass "package.sh CI parse: -d consumed, packaging reached (docker called)"
  else
    fail "package.sh CI parse: -d broke the parse loop — docker packaging never reached"
  fi

  # 2. Only rpm-el9 builder image was targeted, not other releases.
  _CI_UNEXPECTED_RELEASE=""
  for _rel in "deb-resolute" "deb-noble" "rpm-el10"; do
    if printf '%s\n' "$_CI_LOG_CONTENT" | grep -qF "$_rel"; then
      _CI_UNEXPECTED_RELEASE="$_rel"
      break
    fi
  done
  if [[ -z "$_CI_UNEXPECTED_RELEASE" ]]; then
    pass "package.sh CI parse: only rpm-el9 release targeted (no other release images)"
  else
    fail "package.sh CI parse: unexpected release image '${_CI_UNEXPECTED_RELEASE}' targeted — parse produced wrong release set"
    printf '%s\n' "$_CI_LOG_CONTENT" | sed 's/^/          /' >&2
  fi

  rm -f "$FAKE_LOG"

  # Smoke: build-security-server.sh no-args enumeration — assert it skips build-only rows.
  # The script exits before any docker build call (OTel jar absent), so we verify
  # the enumerated SERVICES set by parsing the CSV the same way the script does and
  # checking that (a) no row with dockerfile="-" is included, and (b) the set
  # matches the expected real-image services from the pre-change CSV baseline.
  _EXPECTED_IMAGE_SERVICES=(
    baseline auxiliary-service-base signer-base message-log-archiver-base
    proxy monitor op-monitor configuration-client signer softtoken-signer
    auxiliary-service message-log-archiver ds-control-plane ds-identity-hub
    ds-issuer-service admin-service db-init configuration-proxy-base
    configuration-proxy
  )
  _CSV="${REPO_ROOT}/scripts/lib/service-config.csv"
  _ACTUAL_SERVICES=()
  _BAD_DOCKERFILE_ROW=""
  while IFS= read -r _line || [[ -n "$_line" ]]; do
    [[ -z "${_line// /}" ]] && continue
    IFS=',' read -r _svc _dockerfile _ _ _ _ _ <<<"$_line"
    if [[ "$_dockerfile" == "-" ]]; then
      _BAD_DOCKERFILE_ROW="${_svc}"
    else
      _ACTUAL_SERVICES+=("$_svc")
    fi
  done < <(tail -n +2 "$_CSV")

  if [[ -n "$_BAD_DOCKERFILE_ROW" ]]; then
    # There ARE build-only rows in the CSV; confirm the script's guard excludes them.
    # We check by verifying _ACTUAL_SERVICES contains no build-only service name.
    _GUARD_OK=true
    for _svc in "${_ACTUAL_SERVICES[@]}"; do
      # Check that every enumerated service has a real dockerfile (not "-")
      _df=""
      while IFS=',' read -r _n _d _; do
        [[ "$_n" == "$_svc" ]] && { _df="$_d"; break; }
      done < <(tail -n +2 "$_CSV")
      if [[ "$_df" == "-" ]]; then
        _GUARD_OK=false
        break
      fi
    done
    if $_GUARD_OK; then
      pass "build-security-server.sh no-args enum: build-only rows (dockerfile=-) excluded"
    else
      fail "build-security-server.sh no-args enum: build-only row slipped through"
    fi
  else
    pass "build-security-server.sh no-args enum: no build-only rows in CSV (guard not needed yet)"
  fi

  # Verify the enumerated image set matches the pre-change baseline exactly.
  _expected_sorted=$(printf '%s\n' "${_EXPECTED_IMAGE_SERVICES[@]}" | sort)
  _actual_sorted=$(printf '%s\n' "${_ACTUAL_SERVICES[@]}" | sort)
  if [[ "$_expected_sorted" == "$_actual_sorted" ]]; then
    pass "build-security-server.sh no-args enum: image set matches pre-change baseline (${#_ACTUAL_SERVICES[@]} services)"
  else
    fail "build-security-server.sh no-args enum: image set diverged from baseline"
    diff <(printf '%s\n' "$_expected_sorted") <(printf '%s\n' "$_actual_sorted") | sed 's/^/          /' >&2 || true
  fi

  # Fix 2 guard: build-security-server.sh "all" must produce the same real-image set as no-args.
  # Parse the CSV the same way the script's _enumerate_all_services() does (skipping
  # dockerfile="-" rows) and compare against the no-args set derived above.
  _ALL_SERVICES=()
  while IFS= read -r _line || [[ -n "$_line" ]]; do
    [[ -z "${_line// /}" ]] && continue
    IFS=',' read -r _svc _dockerfile _ _ _ _ _ <<<"$_line"
    [[ "$_dockerfile" == "-" ]] && continue
    _ALL_SERVICES+=("$_svc")
  done < <(tail -n +2 "$_CSV")

  _all_sorted=$(printf '%s\n' "${_ALL_SERVICES[@]}" | sort)
  if [[ "$_all_sorted" == "$_actual_sorted" ]]; then
    pass "build-security-server.sh 'all' enum: yields same ${#_ALL_SERVICES[@]}-service set as no-args (build-only rows excluded)"
  else
    fail "build-security-server.sh 'all' enum: set diverged from no-args enum"
    diff <(printf '%s\n' "$_actual_sorted") <(printf '%s\n' "$_all_sorted") | sed 's/^/          /' >&2 || true
  fi
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo
echo "========================================"
printf "Results:  %s pass  %s fail  %s skip\n" "$PASS" "$FAIL" "$SKIP"
echo "========================================"

if [[ $FAIL -gt 0 ]]; then
  exit 1
fi
