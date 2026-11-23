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

if [ -f /run/secrets/mirror_token ] && [ -n "$XROAD_MIRROR_URL" ] && [ -n "$XROAD_MIRROR_USER" ] \
    && [ -f /etc/apt/sources.list.d/00-mirror.sources ]; then
    mkdir -p /etc/apt/auth.conf.d
    (
        umask 077
        mirror_host=$(echo "$XROAD_MIRROR_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||')
        echo "machine $mirror_host login $XROAD_MIRROR_USER password $(cat /run/secrets/mirror_token)" \
            > /etc/apt/auth.conf.d/mirror.conf
    )
    trap 'rm -f /etc/apt/auth.conf.d/mirror.conf' EXIT
fi
