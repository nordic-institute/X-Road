#!/bin/bash

NEXTUPDATE_PARAMS_FILE=$1
# classpath needs signer.jar for the dependencies
CLASSPATH=/usr/share/xroad/jlib/signer.jar
VALIDATOR_CLASS=ee.ria.xroad.common.conf.globalconfextension.StdinValidator

test -e $NEXTUPDATE_PARAMS_FILE
FILE_EXISTS=$?

set -e

if (( $FILE_EXISTS == 0 )) ;  then
  echo validating ocsp nextupdate params file
  cat $NEXTUPDATE_PARAMS_FILE | java -cp $CLASSPATH $VALIDATOR_CLASS
  echo done.
else
  echo ocsp nextupdate params file $NEXTUPDATE_PARAMS_FILE not found or parameter missing
  echo please give path to ocsp nextupdate params file
fi

