#!/bin/bash
source compile_env.sh

RELEASE="SNAPSHOT"

for i in "$@"; do
case "$i" in
    "-release")
        RELEASE="RELEASE"
        ;;
    "-nodaemon")
        NODAEMON=1
        ;;
    "--skip-tests")
        SKIP_TESTS=1
        ;;
esac
done

ARGUMENTS=("-PxroadBuildType=$RELEASE" --stacktrace build )

if [[ -n "$SKIP_TESTS" ]]; then
    ARGUMENTS+=(-xtest -xintegrationTest -xintTest)
else
    ARGUMENTS+=(runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest)
fi

if [[ -n "$NODAEMON" ]]; then
    ARGUMENTS+=(--no-daemon)
fi

./gradlew "${ARGUMENTS[@]}"

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi
