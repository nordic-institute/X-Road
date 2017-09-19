#!/bin/bash

FETCHINTERVAL_PARAMS_FILE=$1
# classpath needs signer.jar for the dependencies
CLASSPATH=/usr/share/xroad/jlib/signer.jar
VALIDATOR_CLASS=ee.ria.xroad.common.conf.globalconfextension.OcspFetchIntervalStdinValidator

test -e $FETCHINTERVAL_PARAMS_FILE
FILE_EXISTS=$?

set -e

if (( $FILE_EXISTS == 0 )) ;  then
  echo validating ocsp fetch interval params file
  cat $FETCHINTERVAL_PARAMS_FILE | java -cp $CLASSPATH $VALIDATOR_CLASS
  echo done.
else
  echo ocsp fetch interval params file $FETCHINTERVAL_PARAMS_FILE not found or parameter missing
  echo please give path to ocsp fetch interval params file
fi
