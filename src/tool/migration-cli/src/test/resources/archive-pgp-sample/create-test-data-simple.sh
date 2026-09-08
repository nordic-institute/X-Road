#!/bin/bash
#
# Simplified GPG test data generator
# This version avoids gpg-agent issues by using minimal GPG commands
#

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Creating test GPG data..."
echo ""

# Check GPG
if ! command -v gpg &> /dev/null; then
    echo "ERROR: GPG not found. Install with:"
    echo "  macOS: brew install gnupg"
    echo "  Linux: apt-get install gnupg or yum install gnupg2"
    exit 1
fi

echo "GPG version: $(gpg --version | head -1)"
echo ""

# IMPORTANT: GPG agent has a socket path length limit (~108 chars on Unix)
# Work in /tmp with a short path, then move to final location
WORK_DIR="/tmp/xroad-gpg-test-$$"
echo "Working directory: $WORK_DIR (gpg-agent requires short paths)"
echo "Final location: $BASE_DIR"
echo ""

# Clean up old test data in base directory
rm -rf "$WORK_DIR"
rm -rf "$BASE_DIR/scenario-"* "$BASE_DIR"/*.asc "$BASE_DIR"/*.ini
mkdir -p "$WORK_DIR"
TEST_DATA_DIR="$WORK_DIR"

# Test instance
INSTANCE="TEST"

# Scenario 1: Single server key (no grouping)
echo "Scenario 1: Server-level encryption..."
S1_DIR="$TEST_DATA_DIR/scenario-1-no-grouping/gpghome"
mkdir -p "$S1_DIR"
chmod 700 "$S1_DIR"

# Key insight: Use --batch --pinentry-mode loopback --passphrase '' for unattended operation
# DO NOT redirect stdin - causes "/dev/tty not configured" error
gpg --homedir "$S1_DIR" \
    --batch \
    --pinentry-mode loopback \
    --passphrase '' \
    --quick-generate-key "$INSTANCE" default default never 2>&1 | grep -v "WARNING" || true

# Export keys (also need pinentry-mode for secret key export)
gpg --homedir "$S1_DIR" --export --armor > "$TEST_DATA_DIR/scenario-1-public.asc"
gpg --homedir "$S1_DIR" \
    --batch \
    --pinentry-mode loopback \
    --passphrase '' \
    --export-secret-keys --armor > "$TEST_DATA_DIR/scenario-1-secret.asc"

S1_KEY_ID=$(gpg --homedir "$S1_DIR" --list-keys --with-colons | grep '^pub' | cut -d':' -f5 | head -1)
echo "  ✓ Created server key: $S1_KEY_ID"

# Create config file for scenario 1
cat > "$TEST_DATA_DIR/scenario-1-local.ini" <<EOF
# Scenario 1: Server-level encryption (no grouping)
# All archives encrypted with single default server key

[message-log]
archive-encryption-enabled = true
archive-grouping = none
archive-default-encryption-key = $S1_KEY_ID

# Note: When grouping is 'none', all archives use the default key
# No archive-encryption-keys-config is needed
EOF

# Scenario 2: Member keys
echo "Scenario 2: Member-level grouping..."
S2_DIR="$TEST_DATA_DIR/scenario-2-member-grouping"
mkdir -p "$S2_DIR/gpghome" "$S2_DIR/member-keys"
chmod 700 "$S2_DIR/gpghome"

# Server key
gpg --homedir "$S2_DIR/gpghome" \
    --batch \
    --pinentry-mode loopback \
    --passphrase '' \
    --quick-generate-key "$INSTANCE-Server" default default never 2>&1 | grep -v "WARNING" || true

S2_SERVER_KEY_ID=$(gpg --homedir "$S2_DIR/gpghome" --list-keys --with-colons | grep '^pub' | cut -d':' -f5 | head -1)
echo "  ✓ Created server key: $S2_SERVER_KEY_ID"

# Member keys
for member in "GOV/1234" "COM/5678" "MUN/9012"; do
    member_dir="$S2_DIR/member-keys/${member//\//-}"
    mkdir -p "$member_dir"
    chmod 700 "$member_dir"

    gpg --homedir "$member_dir" \
        --batch \
        --pinentry-mode loopback \
        --passphrase '' \
        --quick-generate-key "$INSTANCE/$member" default default never 2>&1 | grep -v "WARNING" || true

    member_key_id=$(gpg --homedir "$member_dir" --list-keys --with-colons | grep '^pub' | cut -d':' -f5 | head -1)

    # Import public key to server home
    gpg --homedir "$member_dir" --export "$INSTANCE/$member" | \
        gpg --homedir "$S2_DIR/gpghome" --import 2>&1 | grep -v "WARNING" || true

    # Export member secret key
    gpg --homedir "$member_dir" \
        --batch \
        --pinentry-mode loopback \
        --passphrase '' \
        --export-secret-keys --armor > "$S2_DIR/member-keys/${member//\//-}-secret.asc"

    echo "  ✓ Created member key: $member -> $member_key_id"
done

# Export server keys
gpg --homedir "$S2_DIR/gpghome" --export --armor > "$TEST_DATA_DIR/scenario-2-all-public.asc"
gpg --homedir "$S2_DIR/gpghome" \
    --batch \
    --pinentry-mode loopback \
    --passphrase '' \
    --export-secret-keys --armor > "$TEST_DATA_DIR/scenario-2-server-secret.asc"

# Create mapping file
cat > "$TEST_DATA_DIR/scenario-2-mapping.ini" <<EOF
# Member key mappings for Scenario 2
# Format: INSTANCE/memberClass/memberCode = KEY_ID

TEST/GOV/1234 = $(gpg --homedir "$S2_DIR/member-keys/GOV-1234" --list-keys --with-colons | grep '^pub' | cut -d':' -f5 | head -1)
TEST/COM/5678 = $(gpg --homedir "$S2_DIR/member-keys/COM-5678" --list-keys --with-colons | grep '^pub' | cut -d':' -f5 | head -1)
TEST/MUN/9012 = $(gpg --homedir "$S2_DIR/member-keys/MUN-9012" --list-keys --with-colons | grep '^pub' | cut -d':' -f5 | head -1)
EOF

# Create config file for scenario 2
cat > "$TEST_DATA_DIR/scenario-2-local.ini" <<EOF
# Scenario 2: Member-level grouping
# Each member has dedicated encryption key

[message-log]
archive-encryption-enabled = true
archive-grouping = member
archive-default-encryption-key = $S2_SERVER_KEY_ID
archive-encryption-keys-config = /etc/xroad/messagelog/archive-encryption-mapping.ini

# Note: The archive-encryption-keys-config should point to the mapping file
# In production, copy scenario-2-mapping.ini to the configured location
# For testing, use the relative path or adjust to your test setup
EOF

# Move from /tmp to final location
echo "Moving test data to final location..."
cp -r "$WORK_DIR"/* "$BASE_DIR/"

# Clean up work directory
rm -rf "$WORK_DIR"

echo ""
echo "✅ Test data created successfully!"
echo ""
echo "Location: $BASE_DIR"
echo ""
echo "Generated scenarios:"
echo "  - Scenario 1: Server-level (no grouping)"
echo "    Key ID: $S1_KEY_ID"
echo "    Files: scenario-1-no-grouping/gpghome/"
echo ""
echo "  - Scenario 2: Member-level grouping"
echo "    Server Key: $S2_SERVER_KEY_ID"
echo "    Members: 3 (GOV/1234, COM/5678, MUN/9012)"
echo "    Files: scenario-2-member-grouping/"
echo ""
echo "Configuration files:"
echo "  - scenario-1-local.ini"
echo "  - scenario-2-local.ini"
echo "  - scenario-2-mapping.ini"
echo ""
echo "Exported keys:"
echo "  - scenario-1-public.asc"
echo "  - scenario-1-secret.asc"
echo "  - scenario-2-all-public.asc"
echo "  - scenario-2-server-secret.asc"
echo ""

