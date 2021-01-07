#!/bin/bash

# This is for testing ScriptUtils by printing the command line arguments and
# the options as seen by getopts, that may or may not contain spaces.

echo "Printing each command line argument"
for each in "$@"
do
    echo "$each"
done

echo "Using getopts to parse arguments"
while getopts ":a:b:c:d:e:" opt ; do
  case $opt in
    a)
      echo "opt a: ${OPTARG}"
      ;;
    b)
      echo "opt b: ${OPTARG}"
      ;;
    c)
      echo "opt c: ${OPTARG}"
      ;;
    d)
      echo "opt d: ${OPTARG}"
      ;;
    e)
      echo "opt e: ${OPTARG}"
      ;;
  esac

done

# vim: ts=2 sw=2 sts=2 et filetype=sh
