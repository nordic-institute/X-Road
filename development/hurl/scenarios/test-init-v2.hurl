# V2 Initialization API Tests
# Usage: hurl --variable ss0_port=3200 --variables-file vars.env test-init-v2.hurl
#
# Prerequisites:
# - Security server must be running

# ============================================================================
# Login to Security Server
# ============================================================================
POST https://{{ss0_host}}:{{ss0_port}}/login
[Options]
insecure: true
[FormParams]
username: xrd
password: secret

HTTP 200
[Captures]
ss0_xsrf_token: cookie "XSRF-TOKEN"


# ============================================================================
# Test 1: Get V2 Initialization Status (before any initialization)
# ============================================================================
GET https://{{ss0_host}}:{{ss0_port}}/api/v2/initialization/status
X-XSRF-TOKEN: {{ss0_xsrf_token}}
[Options]
insecure: true

HTTP 200
[Asserts]
jsonpath "$.overall_status" exists
jsonpath "$.anchor_imported" exists
jsonpath "$.steps" isCollection
jsonpath "$.pending_steps" isCollection
jsonpath "$.failed_steps" isCollection
jsonpath "$.completed_steps" isCollection
jsonpath "$.fully_initialized" exists


# ============================================================================
# Test 2: Initialize ServerConf Step
# ============================================================================
POST https://{{ss0_host}}:{{ss0_port}}/api/v2/initialization/serverconf
X-XSRF-TOKEN: {{ss0_xsrf_token}}
Content-Type: application/json
[Options]
insecure: true
{
  "security_server_code": "SS0",
  "owner_member_class": "GOV",
  "owner_member_code": "0245437-2",
  "ignore_warnings": true
}

HTTP *
[Asserts]
jsonpath "$.step" == "SERVERCONF"
jsonpath "$.status" exists
jsonpath "$.success" exists


# ============================================================================
# Test 3: Initialize SoftToken Step
# ============================================================================
POST https://{{ss0_host}}:{{ss0_port}}/api/v2/initialization/softtoken
X-XSRF-TOKEN: {{ss0_xsrf_token}}
Content-Type: application/json
[Options]
insecure: true
{
  "software_token_pin": "TopSecretP1n."
}

HTTP *
[Asserts]
jsonpath "$.step" == "SOFTTOKEN"
jsonpath "$.status" exists
jsonpath "$.success" exists


# ============================================================================
# Test 4: Initialize GPG Key Step
# ============================================================================
POST https://{{ss0_host}}:{{ss0_port}}/api/v2/initialization/gpg-key
X-XSRF-TOKEN: {{ss0_xsrf_token}}
[Options]
insecure: true

HTTP *
[Asserts]
jsonpath "$.step" == "GPG_KEY"
jsonpath "$.status" exists
jsonpath "$.success" exists


# ============================================================================
# Test 5: Initialize Message Log Encryption Step
# ============================================================================
POST https://{{ss0_host}}:{{ss0_port}}/api/v2/initialization/messagelog-encryption
X-XSRF-TOKEN: {{ss0_xsrf_token}}
[Options]
insecure: true

HTTP *
[Asserts]
jsonpath "$.step" == "MLOG_ENCRYPTION"
jsonpath "$.status" exists
jsonpath "$.success" exists


# ============================================================================
# Test 6: Get V2 Initialization Status (after initialization)
# ============================================================================
GET https://{{ss0_host}}:{{ss0_port}}/api/v2/initialization/status
X-XSRF-TOKEN: {{ss0_xsrf_token}}
[Options]
insecure: true

HTTP 200
[Asserts]
jsonpath "$.overall_status" exists
jsonpath "$.steps" isCollection
jsonpath "$.completed_steps" isCollection
