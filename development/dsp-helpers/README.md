# dsp-helpers — EDC management + control API inspection

One-line shell wrappers for ad-hoc inspection of `ds-control-plane` on LXD dev nodes.

## Prerequisites

- `curl`, `jq`, `awk` on PATH
- `lxc` on PATH (for `ss0`/`ss1` shorthand resolution)
- LXD stack running (`xrd-ss0`, `xrd-ss1` containers up)

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `DSP_MGMT_PORT` | `8182` | Management API port |
| `DSP_CONTROL_PORT` | `8184` | Control API port |
| `DSP_PROVISIONER_JWT` | auto (from setup_dsp.hurl) | Override provisioner token |
| `DSP_PARTICIPANT_JWT` | auto (from setup_dsp.hurl) | Override participant token |
| `DSP_RAW` | `0` | Set to `1` to skip jq pretty-printing |

## Commands

```sh
# List participant contexts on ss0
./dsp participants ss0

# Create participant context
./dsp participant-create ss0 my-ctx my-identity

# List assets for a participant context
./dsp assets ss0 test-part-ctx

# Create an asset
./dsp asset-create ss0 test-part-ctx my-asset http://backend/service

# List policy definitions
./dsp policies ss0 test-part-ctx

# List contract definitions
./dsp contracts ss0 test-part-ctx

# List registered data-planes (no auth required, control port 8184)
./dsp dataplanes ss0
./dsp dataplanes ss1

# Query full DSP catalog (optionally targeting a provider)
./dsp catalog ss0 test-part-ctx
./dsp catalog ss0 test-part-ctx http://xrd-ss1:8183/api/dsp

# Seed both nodes via setup_dsp.hurl then verify
./dsp seed
```

## Host argument

`ss0` / `ss1` are resolved to their container IPv4 via `lxc list`. Pass a literal IP
or hostname to target any host (e.g. inside a container: `./dsp participants localhost`).

## Token source

Tokens are extracted from `../hurl/scenarios/setup_dsp.hurl` (single source of truth).
Override per-invocation via `DSP_PROVISIONER_JWT` / `DSP_PARTICIPANT_JWT` env vars.
