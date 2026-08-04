#!/bin/bash

# Writes each optional kvSeed entry (chart's values.yaml) into OpenBao's KV v1
# store as a "payload" field, mirroring every other xrd-secret writer in this
# codebase (_openbao.sh, the LXD ansible role's msglog-archive-encryption.yml).
# A no-op when no kvSeed entries were configured (no /kvseed/manifest.json
# mounted). Safe to re-run: KV v1 write is a plain overwrite, not versioned.
seed_kv() {
  local addr="$1"
  local token="$2"
  local manifest="/kvseed/manifest.json"

  if [ ! -f "$manifest" ]; then
    return 0
  fi

  jq -c '.[]' "$manifest" | while IFS= read -r entry; do
    local path file
    path=$(echo "$entry" | jq -r '.path')
    file=$(echo "$entry" | jq -r '.file')

    echo "[SEED] Writing xrd-secret/${path}..." >&2
    jq -Rs '{payload: .}' "/kvseed/${file}" | \
      curl -fsS -k -X POST \
        -H "X-Vault-Token: ${token}" \
        -d @- \
        "${addr}/v1/xrd-secret/${path}" >/dev/null || {
      echo "[SEED] Failed to write xrd-secret/${path}" >&2
      return 1
    }
  done
}
