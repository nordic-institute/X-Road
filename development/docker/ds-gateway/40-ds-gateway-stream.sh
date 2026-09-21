#!/bin/sh
# Generates the stream forwarders from env:
#   DS_GATEWAY_IDENTITY_HUB_UPSTREAM   host:port behind 7183 (DID resolution), default ds-identity-hub:7183
#   DS_GATEWAY_CONTROL_PLANE_UPSTREAM  host:port behind 8183 (DSP), default ds-control-plane:8183
#   DS_GATEWAY_PROXY_HOST              when set, also forwards 5500/5577 (X-Road message exchange, OCSP)
#                                      to this host; unset when the proxy shares the network namespace
#   DS_GATEWAY_RESOLVER                when set, upstreams are re-resolved through this DNS server at
#                                      proxy time (Docker's embedded DNS is 127.0.0.11), so a restarted
#                                      backend with a new IP keeps working
set -eu

out=/etc/nginx/stream.d/ds-gateway.conf
identity_hub="${DS_GATEWAY_IDENTITY_HUB_UPSTREAM:-ds-identity-hub:7183}"
control_plane="${DS_GATEWAY_CONTROL_PLANE_UPSTREAM:-ds-control-plane:8183}"
proxy_host="${DS_GATEWAY_PROXY_HOST:-}"
resolver="${DS_GATEWAY_RESOLVER:-}"

forward() {
    listen_port="$1"
    upstream="$2"
    if [ -n "$resolver" ]; then
        # stream has no `set`; a map-fed variable makes nginx resolve at proxy time.
        var="upstream_$listen_port"
        printf 'map "" $%s { default %s; }\n' "$var" "$upstream"
        printf 'server { listen %s; proxy_pass $%s; }\n' "$listen_port" "$var"
    else
        printf 'server { listen %s; proxy_pass %s; }\n' "$listen_port" "$upstream"
    fi
}

{
    if [ -n "$resolver" ]; then
        printf 'resolver %s valid=10s;\n' "$resolver"
    fi
    forward 7183 "$identity_hub"
    forward 8183 "$control_plane"
    if [ -n "$proxy_host" ]; then
        forward 5500 "$proxy_host:5500"
        forward 5577 "$proxy_host:5577"
    fi
} > "$out"
