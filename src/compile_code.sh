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
esac
done

ARGUMENTS=("-PxroadBuildType=$RELEASE" --stacktrace buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest)

if [[ -n "$SONAR" ]]; then
    ARGUMENTS+=(dependencyCheckAnalyze sonarqube)
fi

./gradlew "${ARGUMENTS[@]}"

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi
