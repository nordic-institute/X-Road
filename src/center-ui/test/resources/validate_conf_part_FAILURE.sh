#!/bin/sh

CENTERUI_HOME=$XROAD_HOME/center-ui

read FILE_INPUT
echo $FILE_INPUT > $CENTERUI_HOME/build/optional_content

echo "firstErrorLine" >&2

exit 1
