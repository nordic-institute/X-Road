#!/bin/bash
source compile_env.sh

RELEASE=0

for i in "$@"
do
case $i in
    -release)
    RELEASE=1
    ;;
esac
done

ARGUMENTS="-PxroadBuildType=$RELEASE --stacktrace buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest"

if [[ -n $1 ]] && [[ $1 == "sonar" ]]; then
    ARGUMENTS="$ARGUMENTS dependencyCheckAnalyze sonarqube"
fi

./gradlew "$ARGUMENTS"

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi
