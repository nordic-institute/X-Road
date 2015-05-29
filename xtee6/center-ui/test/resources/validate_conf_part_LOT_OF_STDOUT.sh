#!/bin/bash

CENTERUI_HOME=$XROAD_HOME/center-ui

FILE_INPUT=$(cat)
echo $FILE_INPUT > $CENTERUI_HOME/build/optional_content

echo "Before large stdout" >&2

yes 1234567890 | head --bytes 100000

echo "After large stdout" >&2
