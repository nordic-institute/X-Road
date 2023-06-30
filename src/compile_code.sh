#!/bin/bash
source compile_env.sh

RELEASE="SNAPSHOT"

for i in "$@"; do
case "$i" in
    "-release")
        RELEASE="RELEASE"
        ;;
    "sonar"|"-sonar")
        SONAR=1
        ;;
    "-nodaemon")
        NODAEMON=1
        ;;
esac
done

ARGUMENTS=("-PxroadBuildType=$RELEASE" --stacktrace build runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest)

if [[ -n "$SONAR" ]]; then
    ARGUMENTS+=(dependencyCheckAnalyze sonarqube)
fi

if [[ -n "$NODAEMON" ]]; then
    ARGUMENTS+=(--no-daemon -v /var/run/docker.sock:/var/run/docker.sock)
fi

./gradlew "${ARGUMENTS[@]}"

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi
