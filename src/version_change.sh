#!/bin/bash
set -e

export DEBFULLNAME=NIIS
export DEBEMAIL=info@niis.org

RELEASE=false
DEB_CHANGELOG=packages/src/xroad/ubuntu/generic/changelog
CURRENT_VERSION=`awk '{if ($1 == "##") {print $2; exit;}}' ../CHANGELOG.md`

function version { echo "$@" | awk -F. '{ printf("%03d%03d%03d\n", $1,$2,$3); }'; }

# Params: line to start delete from, version, changelog location, line offset in delete to
function downgrade {
    OFFSET=$4
    TO=`awk "/$2/{print NR-2}" $3`
    if [[ ! -z $OFFSET ]]; then
        TO=$(($TO+$OFFSET))
    fi

    if [[ -z $TO ]]; then
        echo "Version $2 not found from $3"
        exit 1
    fi

    sed -i "${1},${TO}d" $3
}

# Params: changelog location, version, message
function add_dch_entry {
    sed -i "1s/\-0/\-1/" $1
    dch -v "$2-0" --distribution stable -c $1 $3
}

function release_current {
    dch -r --distribution stable -c "$DEB_CHANGELOG" ""
    sed -i "1s/\-0/\-1/" "$DEB_CHANGELOG"
    CURRENT_DATE=`date +%Y-%m-%d`
    sed -i "0,/ - UNRELEASED$/ s// - $CURRENT_DATE/" ../CHANGELOG.md
}

for i in "$@"; do
case "$1" in
    "-r"|"--release")
        RELEASE=true
	    shift
        ;;
esac
done

VERSION=$1

if [[ -z $VERSION ]]; then
    if [[ $RELEASE == true ]]; then
        release_current
        echo "Versions released (changed $CURRENT_VERSION to $CURRENT_VERSION-1)"
        exit 1
    else
        echo "Usage: $0 [--release] <version>"
        exit 1
    fi
fi

if [[ ! $VERSION =~ ^[0-9][0-9]?.[0-9][0-9]?.[0-9][0-9]?$ ]]; then
    echo "Version must be in format x.y.z"
    exit 1
fi

if [[ $VERSION == $CURRENT_VERSION ]]; then
    echo "$VERSION is the current version"
    if [[ $RELEASE == true ]]; then
        echo "Releasing the current version"
        release_current
    fi
    exit 1
fi

DOWNGRADE=false

if [[ "$(version "$CURRENT_VERSION")" -gt "$(version "$VERSION")" ]]; then
    DOWNGRADE=true
    echo "Downgrading from $CURRENT_VERSION to $VERSION"
fi

while true; do
    read -p "Update version to $VERSION? (y/n): " yn
    case $yn in
        [Yy]* ) break;;
        [Nn]* ) exit;;
        * ) echo "Please answer yes or no.";;
    esac
done

if [[ $DOWNGRADE == true ]]; then
    downgrade "2" "$VERSION" "../CHANGELOG.md"
    sed -i "s/## $VERSION.*$/## $VERSION - UNRELEASED/" ../CHANGELOG.md
    downgrade "1" "$VERSION" "$DEB_CHANGELOG" "1"
    sed -i "1s/\-1/\-0/" $DEB_CHANGELOG
else
    sed -i "2a## $VERSION - UNRELEASED\n" ../CHANGELOG.md
    add_dch_entry "$DEB_CHANGELOG" "$VERSION" "Change history is found at /usr/share/doc/xroad-common/CHANGELOG.md.gz"
fi

if [[ $RELEASE == true ]]; then
    release_current
    echo "Versions released (changed $VERSION to $VERSION-1)"
fi

sed -i "s/^xroadVersion.*$/xroadVersion=$VERSION/" gradle.properties
sed -i "s/^VERSION=$CURRENT_VERSION.*$/VERSION=$VERSION/" packages/build-rpm.sh

echo "Version updated to $VERSION"
