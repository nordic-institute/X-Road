#!/bin/bash

MONITORING_PARAMS_FILE=$1
# classpath needs proxymonitor addon for the validator codes, and signer.jar for the dependencies
CLASSPATH=/usr/share/xroad/jlib/addon/proxy/proxymonitor-metaservice.jar:/usr/share/xroad/jlib/signer.jar
VALIDATOR_CLASS=ee.ria.xroad.proxy.serverproxy.StdinValidator

test -e $MONITORING_PARAMS_FILE
FILE_EXISTS=$?

set -e

if (( $FILE_EXISTS == 0 )) ;  then
  echo validating monitoring params file
  cat $MONITORING_PARAMS_FILE | java -cp $CLASSPATH $VALIDATOR_CLASS
  echo done.
else
  echo monitoring params file $MONITORING_PARAMS_FILE not found or parameter missing
  echo please give path to monitoring params file
fi

