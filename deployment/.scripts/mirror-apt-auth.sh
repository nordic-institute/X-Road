# shellcheck shell=sh
# Mirror APT Credentials (Container Side)
#
# Source this file at the start of every RUN step that runs apt, with the
# mirror_token BuildKit secret mounted in that step:
#
#   RUN --mount=type=bind,from=mirror-scripts,source=mirror-apt-auth.sh,target=/tmp/mirror-apt-auth.sh \
#       --mount=type=secret,id=mirror_token \
#       . /tmp/mirror-apt-auth.sh && apt-get update && apt-get install ...
#
# Writes the apt auth file from the secret and removes it when the shell of the
# step exits, so no image layer holds the credential. Does nothing when the
# mirror is not configured. Reads XROAD_MIRROR_URL and XROAD_MIRROR_USER from
# the environment.
#
# The file also defines the only writer of the auth line, for configure-mirror-apt.sh:
#   mirror_apt_host <URL>                  prints the host part of the mirror URL
#   mirror_apt_write_auth <URL> <USER> <TOKEN>
# With XROAD_MIRROR_APT_AUTH_FUNCTIONS_ONLY set, sourcing only defines the functions.

mirror_apt_host() {
    printf '%s\n' "$1" | sed -e 's|^[^/]*//||' -e 's|/.*$||'
}

mirror_apt_write_auth() {
    mkdir -p /etc/apt/auth.conf.d
    (
        umask 077
        printf 'machine %s login %s password %s\n' "$(mirror_apt_host "$1")" "$2" "$3" \
            > /etc/apt/auth.conf.d/mirror.conf
    )
    chmod 600 /etc/apt/auth.conf.d/mirror.conf
}

if [ -z "$XROAD_MIRROR_APT_AUTH_FUNCTIONS_ONLY" ] \
    && [ -f /run/secrets/mirror_token ] && [ -n "$XROAD_MIRROR_URL" ] && [ -n "$XROAD_MIRROR_USER" ] \
    && [ -f /etc/apt/sources.list.d/00-mirror.sources ]; then
    mirror_apt_write_auth "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER" "$(cat /run/secrets/mirror_token)"
    trap 'rm -f /etc/apt/auth.conf.d/mirror.conf' EXIT
fi
