#!/bin/bash
source compile_env.sh

if [[ -n $1 ]] && [[ $1 == "sonar" ]]; then
    ./gradlew --stacktrace buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest dependencyCheckAnalyze sonarqube
else
    ./gradlew --stacktrace buildAll runProxyTest runMetaserviceTest runProxymonitorMetaserviceTest
fi

rc=$?; if [[ $rc != 0 ]]; then exit $rc; fi
