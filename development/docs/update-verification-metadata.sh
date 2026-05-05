#!/usr/bin/env bash
#
# update-verification-metadata.sh
#
# Detects OS-classifier artifacts in $XROAD_HOME/src/gradle/verification-metadata.xml
# that are present on Maven Central but missing from the metadata, then
# downloads, hashes, and inserts them.
#
# Runs after `./gradlew --write-verification-metadata sha256 build`, which
# only writes entries for the host's os.detected.classifier. This script
# completes the metadata so builds verify on other platforms too.
#
# Usage:
#   update-verification-metadata.sh [--dry-run]
#
# Exit codes:
#   0 success (clean or patched)
#   1 tool missing / network / validation failure
#
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../.scripts/base-script.sh
source "${SCRIPT_DIR}/../../.scripts/base-script.sh"
set -u

META="${XROAD_HOME}/src/gradle/verification-metadata.xml"
MAVEN_CENTRAL="https://repo1.maven.org/maven2"
PLATFORMS=(linux-x86_64 linux-aarch_64 osx-x86_64 osx-aarch_64 windows-x86_64)

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=1
fi

die() { log_error "$*"; exit 1; }

command -v curl    >/dev/null || die "curl not found"
command -v python3 >/dev/null || die "python3 not found"
if command -v sha256sum >/dev/null; then
  SHA256_CMD=(sha256sum)
elif command -v shasum >/dev/null; then
  SHA256_CMD=(shasum -a 256)
else
  die "need sha256sum or shasum"
fi

[[ -f "$META" ]] || die "metadata not found: $META"

log_info "metadata: $META"
log_info "dry-run: $DRY_RUN"

# Step 1: extract (group, name, version, ext, present_classifiers) tuples
# for every component containing classifier-style artifacts.
TARGETS_FILE="$(mktemp)"
trap 'rm -f "$TARGETS_FILE"' EXIT

python3 - "$META" >"$TARGETS_FILE" <<'PY'
import re, sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
ns = {'v': 'https://schema.gradle.org/dependency-verification'}
tree = ET.parse(path)
root = tree.getroot()

CLS_RE = re.compile(r'^(linux|osx|windows|freebsd)-(x86_64|aarch_64|ppc_64|ppc64le)$')

for comp in root.iter('{https://schema.gradle.org/dependency-verification}component'):
    group   = comp.get('group')
    name    = comp.get('name')
    version = comp.get('version')
    prefix  = f"{name}-{version}-"
    present = []
    ext     = None
    for art in comp.findall('v:artifact', ns):
        aname = art.get('name')
        if not aname.startswith(prefix):
            continue
        rest = aname[len(prefix):]
        # rest = "<classifier>.<ext>"
        if '.' not in rest:
            continue
        cls, _, tail = rest.rpartition('.')
        if tail not in ('exe', 'jar'):
            continue
        if not CLS_RE.match(cls):
            continue
        present.append(cls)
        ext = tail
    if present:
        print(f"{group}|{name}|{version}|{ext}|{','.join(sorted(set(present)))}")
PY

if [[ ! -s "$TARGETS_FILE" ]]; then
  log_info "no classifier-style components found, nothing to do"
  exit 0
fi

log_info "components with classifier artifacts:"
while IFS='|' read -r group name version ext present; do
  log_info "  $group:$name:$version [.$ext] present=$present"
done <"$TARGETS_FILE"

# Step 2: probe Maven Central for each missing classifier, download+hash.
PLANNED_FILE="$(mktemp)"
trap 'rm -f "$TARGETS_FILE" "$PLANNED_FILE"' EXIT

while IFS='|' read -r group name version ext present; do
  group_path="$(printf '%s' "$group" | tr '.' '/')"
  IFS=',' read -r -a present_arr <<<"$present"
  for cls in "${PLATFORMS[@]}"; do
    skip=0
    for p in "${present_arr[@]}"; do
      if [[ "$p" == "$cls" ]]; then skip=1; break; fi
    done
    [[ $skip -eq 1 ]] && continue

    artifact="${name}-${version}-${cls}.${ext}"
    url="${MAVEN_CENTRAL}/${group_path}/${name}/${version}/${artifact}"
    code="$(curl -sI -o /dev/null -w '%{http_code}' --max-time 15 "$url" || echo '000')"
    if [[ "$code" == "200" ]]; then
      log_info "  + will add $artifact"
      if [[ $DRY_RUN -eq 1 ]]; then
        printf '%s|%s|%s|%s|DRY\n' "$group" "$name" "$version" "$artifact" >>"$PLANNED_FILE"
      else
        hash="$(curl -sL --max-time 120 "$url" | "${SHA256_CMD[@]}" | awk '{print $1}')"
        [[ ${#hash} -eq 64 ]] || die "bad hash for $artifact: $hash"
        printf '%s|%s|%s|%s|%s\n' "$group" "$name" "$version" "$artifact" "$hash" >>"$PLANNED_FILE"
      fi
    elif [[ "$code" == "404" ]]; then
      : # not published for this platform, skip silently
    else
      log_warn "  ? HTTP $code for $url (skipping)"
    fi
  done
done <"$TARGETS_FILE"

if [[ ! -s "$PLANNED_FILE" ]]; then
  log_success "metadata already complete for all detected components"
  exit 0
fi

log_info "planned additions: $(wc -l <"$PLANNED_FILE" | tr -d ' ')"

if [[ $DRY_RUN -eq 1 ]]; then
  while IFS='|' read -r group name version artifact _; do
    printf '  %s:%s:%s -> %s\n' "$group" "$name" "$version" "$artifact"
  done <"$PLANNED_FILE"
  log_info "dry-run complete, no file changes"
  exit 0
fi

# Step 3: insert entries via python (preserves formatting).
python3 - "$META" "$PLANNED_FILE" <<'PY'
import sys, re

meta_path, planned_path = sys.argv[1], sys.argv[2]
with open(meta_path, 'r', encoding='utf-8') as f:
    text = f.read()

with open(planned_path, 'r', encoding='utf-8') as f:
    planned = [ln.rstrip('\n').split('|') for ln in f if ln.strip()]

comp_re_tmpl = (
    r'(<component\s+group="{g}"\s+name="{n}"\s+version="{v}"\s*>)(.*?)(</component>)'
)

def esc(s): return re.escape(s)

added = 0
for group, name, version, artifact, hashval in planned:
    pat = re.compile(
        comp_re_tmpl.format(g=esc(group), n=esc(name), v=esc(version)),
        re.DOTALL,
    )
    m = pat.search(text)
    if not m:
        sys.stderr.write(f"[update-meta] WARN: component not found: {group}:{name}:{version}\n")
        continue
    head, body, tail = m.group(1), m.group(2), m.group(3)

    # Detect indentation of existing <artifact> inside body.
    art_indent_m = re.search(r'(\n)([ \t]+)<artifact\s', body)
    if art_indent_m:
        art_indent = art_indent_m.group(2)
    else:
        art_indent = '         '
    sha_indent = art_indent + '   '

    new_block = (
        f'{art_indent}<artifact name="{artifact}">\n'
        f'{sha_indent}<sha256 value="{hashval}" origin="Generated by update-verification-metadata.sh"/>\n'
        f'{art_indent}</artifact>\n'
    )

    # Insert before the alphabetically-next <artifact> inside this component.
    # If none, append after the last </artifact>, preserving the trailing
    # whitespace that indents </component>.
    entries = list(re.finditer(r'([ \t]+)<artifact\s+name="([^"]+)">', body))
    insert_at = None
    for e in entries:
        if artifact < e.group(2):
            insert_at = e.start()
            break
    if insert_at is None:
        last_close = body.rfind('</artifact>')
        if last_close == -1:
            sys.stderr.write(f"[update-meta] WARN: no existing artifact in component; skipping {artifact}\n")
            continue
        after_close = body.find('\n', last_close)
        insert_pos = (after_close + 1) if after_close != -1 else len(body)
        new_body = body[:insert_pos] + new_block + body[insert_pos:]
    else:
        line_start = body.rfind('\n', 0, insert_at) + 1
        new_body = body[:line_start] + new_block + body[line_start:]

    text = text[:m.start()] + head + new_body + tail + text[m.end():]
    added += 1

# Normalize </component> indent when body exits cleanly but trailing whitespace
# was stripped (e.g. by a prior regen run): ensure every </component> has the
# same 6-space indent used throughout the file.
text = re.sub(r'\n(?!\s)</component>', '\n      </component>', text)

with open(meta_path, 'w', encoding='utf-8') as f:
    f.write(text)

print(f"[update-meta] inserted {added} artifact entries")
PY

# Step 4: XML validity check (python3 stdlib, no extra tool required).
if ! python3 -c "import xml.etree.ElementTree as E, sys; E.parse(sys.argv[1])" "$META"; then
  die "XML validation failed after insertion"
fi

log_success "done"
