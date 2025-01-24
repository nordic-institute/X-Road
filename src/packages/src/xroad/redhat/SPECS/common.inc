# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

# compare check assumes versions like "major.minor.bugfix-release", compares only major.minor parts
# also last_supported_version macro needs to be defined
%define upgrade_check \
version_gt() { \
    local major1 minor1 fix1 rel1 \
    local major2 minor2 fix2 rel2 \
    IFS=".- " read -r major1 minor1 fix1 rel1 <<<"$1" \
    IFS=".- " read -r major2 minor2 fix2 rel2 <<<"$2" \
    if [[ $major1 -gt $major2 || ( $major1 -eq $major2 && $minor1 -gt $minor2 ) ]]; then \
      return 0 \
    fi \
    return 1 \
} \
if [ "$1" -gt 1 ] ; then \
    # upgrade \
    installed_version=$(rpm -q "%name" --queryformat="%%{version}") \
    if version_gt "%{last_supported_version}" "$installed_version"; then \
      echo "ERROR: Upgrade supported from version %{last_supported_version} or newer." >&2\
      exit 1 \
    fi \
fi \
%{nil}

%define systemd_try_restart() \
systemctl --quiet try-restart %{?*} >/dev/null 2>&1 || : \
%{nil}

%define set_yaml_property_function                                    \
function set_yaml_property { # $1 property, $2 value, $3 file         \
  if [ ! -f "$3" ]; then                                              \
    echo "File $3 does not exist. Creating the file."                 \
    touch "$3"                                                        \
    chown xroad:xroad "$3"                                            \
  fi                                                                  \
                                                                      \
  CURRENT_VALUE=$(yq "$1" "$3")                                       \
  if [ -z "$CURRENT_VALUE" ] || [ "$CURRENT_VALUE" == "null" ]; then  \
    yq -i "$1 = $2" "$3"                                              \
  fi                                                                  \
}                                                                     \
%{nil}

%define disable_addon_function                                        \
function disable_addon { # $1 property, $2 file                       \
  if [ ! -f "$2" ]; then                                              \
    return 0                                                          \
  fi                                                                  \
  CURRENT_VALUE=$(yq "$1" "$2")                                       \
  if [ -z "$CURRENT_VALUE" ]; then                                    \
    return 0;                                                         \
  fi                                                                  \
  if [ "$CURRENT_VALUE" == "true" ]; then                             \
    yq -i "$1 = false" "$2"                                           \
  fi                                                                  \
}                                                                     \
%{nil}

