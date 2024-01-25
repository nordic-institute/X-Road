#!/bin/bash

# Check for macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    # Create aliases for loopback interface. Used with ProxyTestSuite
    sudo ifconfig lo0 alias 127.0.0.2 up
    sudo ifconfig lo0 alias 127.0.0.3 up
    sudo ifconfig lo0 alias 127.0.0.5 up
    sudo ifconfig lo0 alias 127.0.0.7 up
else
    echo "This script is intended to run on macOS only."
    exit 1
fi
