#!/bin/bash

# Usage: ./decrypt-archives.sh <file prefix> <private key id> <passphrase> <output folder>

# This script decrypts multiple GPG-encrypted files (name starting with prefix) in a specified directory using
# a provided private key and passphrase. Any errors during decryption are ignored.

DIR="/var/lib/xroad"

decrypt_file() {
  local FILE="$1"
  local KEY_FILE="$2"
  local PASS="$3"
  local RESULT_FOLDER="$4"

  local OUTFILE="$(basename "$FILE" .gpg)"

  mkdir -p "$RESULT_FOLDER"

  # Create a temporary GNUPGHOME to avoid polluting your keyring
  TMP_GNUPGHOME=$(mktemp -d)
  chmod 700 "$TMP_GNUPGHOME"

  # Import the private key
  gpg --homedir "$TMP_GNUPGHOME" --batch --yes --import "$KEY_FILE" >/dev/null 2>&1

  #decrypt the file
  gpg --homedir "$TMP_GNUPGHOME" --batch --no-tty --pinentry-mode loopback --passphrase "$PASS" \
      --output "$RESULT_FOLDER/$OUTFILE" \
      --decrypt "$FILE" 2>/dev/null

  rm -rf "$TMP_GNUPGHOME"
}

declare -i filecount=0
for FILE in "$DIR"/$1*; do
    # Make sure it's a regular file
    [[ -f "$FILE" ]] || continue

    # Call the decrypt_file function
    ((filecount++))
    echo "Decrypting file: $FILE"
    decrypt_file "$FILE" "$2" "$3" "$4" || true
done

echo "Processed $filecount files."

