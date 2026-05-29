#!/bin/bash
set -e
LAST_SUPPORTED_VERSION=7.8.0

# Global variable to determine if text coloring is enabled
isTextColoringEnabled=$(command -v tput >/dev/null && tput setaf 1 &>/dev/null && echo true || echo false)

warn() {
  if $isTextColoringEnabled; then
    echo "$(tput setaf 3)*** $*$(tput sgr0)"
  else
    echo "*** $*"
  fi
}

function builddeb {
    local root="$1"
    local dist="$2"
    local suffix="$3"
    local packageVersion="$4"

    pushd "$(pwd)"
    cd "$root/$dist"
    cp -n ../generic/* debian/
    version="$(dpkg-parsechangelog -l../generic/changelog | sed -n -e 's/^Version: //p')"
    compat="$(cat debian/compat)"
    sed -i "s/\${debhelper-version}/$compat/" debian/control

    for p in $(dh_listpackages); do
      if ! [ -f "debian/$p.preinst" ]; then
        echo "Generating $p.preinst"
        cat <<EOF >"debian/$p.preinst"
#!/bin/bash
if [ "\$1" = "upgrade" ]; then
  if dpkg --compare-versions "$LAST_SUPPORTED_VERSION" gt "\$2"; then
    echo "ERROR: Upgrade supported from version $LAST_SUPPORTED_VERSION or newer" >&2
    exit 1
  fi
fi
#DEBHELPER#
exit 0
EOF
      fi
    done
    sed -i "s/#LAST_SUPPORTED_VERSION#/${LAST_SUPPORTED_VERSION}/" debian/*.preinst

    warn "Building $dist packages.."
    warn "using packageVersion $packageVersion"
    if [[ $packageVersion != "-release" ]]; then
        version=$version."$packageVersion"
    else
        export DEB_BUILD_OPTIONS=release
    fi

    dch -b -v "$version.$suffix" "Build for $dist"
    dch --distribution $dist -r ""
    local host_arch
    host_arch="$(dpkg --print-architecture)"
    local cross_arch
    if [[ "$host_arch" == "amd64" ]]; then
        cross_arch="arm64"
    else
        cross_arch="amd64"
    fi

    # Pass 1: build all packages for host arch (all _all.deb + signer for host arch)
    warn "Pass 1: building all packages for $host_arch..."
    dpkg-buildpackage -b -us -uc
    warn "Pass 1: $host_arch build finished."

    # Pass 2: cross-build arch-specific packages for the other arch (only signer)
    warn "Pass 2: cross-building xroad-signer for $cross_arch..."
    dpkg-buildpackage -B -us -uc -a "$cross_arch" -tc
    warn "Pass 2: $cross_arch cross-build finished."
    popd

    warn "Collecting .deb packages..."
    find $root \( -name "xroad*$suffix*_all.deb" -o -name "xroad*$suffix*_amd64.deb" -o -name "xroad*$suffix*_arm64.deb" \) -exec mv {} "build/$suffix/" \;
    warn "Done."
}

function prepare {
    mkdir -p "build/$1"
    rm -f "build/$1/"*.deb
}

DIR="$(cd "$(dirname $0)" && pwd)"
cd "$DIR"

mkdir -p build/xroad
rm -rf build/xroad/ubuntu
cp -a src/xroad/ubuntu build/xroad/

# version was not given, use empty
if [ -z "$2" ]; then
  readonly PACKAGE_VERSION="$(date --utc --date @`git show -s --format=%ct` +'%Y%m%d%H%M%S')$(git show -s --format=git%h --abbrev=7)"
else
  readonly PACKAGE_VERSION="$2"
fi

case "$1" in
    noble)
        prepare ubuntu24.04
        builddeb build/xroad/ubuntu noble ubuntu24.04 "$PACKAGE_VERSION"
        ;;
    resolute)
        prepare ubuntu26.04
        builddeb build/xroad/ubuntu resolute ubuntu26.04 "$PACKAGE_VERSION"
        ;;
    *)
        echo "Unsupported distribution $dist"
        exit 1;
        ;;
esac
