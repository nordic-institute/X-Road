#!/bin/sh

CENTERUI_HOME=$XROAD_HOME/center-ui

read FILE_INPUT
echo $FILE_INPUT > $CENTERUI_HOME/build/optional_content

echo "firstWarningLine" >&2
echo "secondWarningLine" >&2

echo "simplyInfoLine"
