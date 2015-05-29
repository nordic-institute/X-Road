#!/bin/sh

OUTPUT_FILE=build/transfer_output.txt

echo "This one goes to standard output."
echo "And this one to standard error." >&2
echo "Output 2."
echo "Error 2." >&2

echo "Archived date: $(date)" > $OUTPUT_FILE
echo "First argument: $1" >> $OUTPUT_FILE

# exit 7
