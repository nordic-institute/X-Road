#!/bin/bash
# Wrapper script to ensure secret store token is ready before starting xroad services

set -e

TOKEN_FILE="/etc/xroad/secret-store-client-token"
TIMEOUT=60
ELAPSED=0

echo "Waiting for secret store token at $TOKEN_FILE..."

# Wait for token file to exist and be non-empty
while [ ! -f "$TOKEN_FILE" ] || [ ! -s "$TOKEN_FILE" ]; do
    if [ $ELAPSED -ge $TIMEOUT ]; then
        echo "ERROR: Timeout waiting for secret store token after ${TIMEOUT} seconds"
        echo "The file $TOKEN_FILE does not exist or is empty"
        exit 1
    fi
    
    if [ $((ELAPSED % 5)) -eq 0 ] && [ $ELAPSED -gt 0 ]; then
        echo "Still waiting for token... (${ELAPSED}s elapsed)"
    fi
    
    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

# Verify file is readable and has proper permissions
if [ ! -r "$TOKEN_FILE" ]; then
    echo "ERROR: Token file exists but is not readable"
    ls -la "$TOKEN_FILE"
    exit 1
fi

echo "Secret store token found after ${ELAPSED} seconds"
echo "Token file info: $(ls -lh $TOKEN_FILE)"

# Execute the actual service command passed as arguments
exec "$@"

