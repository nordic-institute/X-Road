# =============================================================================
# DSP Mock Participant Provisioning (LXD dev env)
#
# WIP / MOCK SEED — replaces production participant onboarding.
# Filename prefix `00-` ensures this runs BEFORE setup.hurl (run-hurl.sh
# concatenates *.hurl files in alphabetical order). Setup.hurl's CS/SS
# initialisation invokes management-service flows that require the DSP
# control-plane participant context to already exist on each SS.
#
# What this seeds (mirrors e2e 0300-ds-baseline.feature, minus assets):
#   1. Issuer Service (on xrd-cs)        — issuer participant ctx, holders for
#                                          ss0/ss1 DIDs, attestation +
#                                          credential definitions.
#   2. Identity Hub  (on xrd-ss0/xrd-ss1)— participant ctx, credential request
#                                          from issuer; polled until ISSUED.
#   3. Control Plane (on xrd-ss0/xrd-ss1)— participant ctx + STS-bound config.
#
# What this DOES NOT seed:
#   - Asset / policy / contract definitions. Production behaviour: assets are
#     resolved dynamically from serverconf via the X-Road EDC asset extension
#     at request time. Dev policies/contracts are similarly auto-provisioned.
#
# Production replacement plan (REMOVE this file when implemented):
#   - Issuer onboarding via Central Server admin UI / mgmt-service flow.
#   - IH credential issuance triggered by member registration on CS.
#   - CP participant context auto-created on SS first start, STS secrets
#     drawn from OpenBao instead of pre-shared aliases.
#   - Permissions: replace lax `policy-allow-all` with role/membership-bound
#     ODRL policies issued per data-product.
#
# Auth: all bearer tokens are pre-signed JWTs from the dev mock-jwks-server
# (xrd-cs:8099). Same tokens as e2e DsStepDefs — DO NOT use in any non-dev env.
# =============================================================================

# --- Bearer tokens (dev only — signed by dev mock JWKS) ---
# Provisioner role on Issuer Service (scope: issuer-admin-api + identity-api)
# eyJ...PROVISIONER...

# Roles & scopes encoded in token claims; rotation is a no-op until prod issuer
# replaces the mock JWKS.

# =============================================================================
# 1. ISSUER SERVICE (xrd-cs) — runs once, shared by all SSes
# =============================================================================

# 1.1 Create issuer participant context "issuer" with IssuerService endpoint.
# DID `did:web:xrd-cs%3A6183:issuer` resolves over https://xrd-cs:6183/issuer/did.json
POST https://{{is_host}}:{{is_id_port}}/api/identity/v1alpha/participants
Authorization: Bearer {{is_provisioner_token}}
Content-Type: application/json

{
    "roles": ["admin"],
    "serviceEndpoints": [
        {
            "type": "IssuerService",
            "serviceEndpoint": "https://{{is_host}}:{{is_iss_port}}/api/issuance/v1alpha/participants/issuer",
            "id": "issuer-issuer-service"
        }
    ],
    "active": true,
    "participantContextId": "issuer",
    "did": "did:web:{{is_host}}%3A{{is_did_port}}:issuer",
    "key": {
        "keyId": "did:web:{{is_host}}%3A{{is_did_port}}:issuer#key-1",
        "privateKeyAlias": "issuer-key",
        "keyGeneratorParams": {
            "algorithm": "EdDSA"
        }
    }
}

# Tolerate re-runs: 200 on first run, conflict on rerun.
HTTP *
[Asserts]
status < 300 or status == 409

# 1.2 Register holder = ss0's identity-hub DID.
# Production: holders added when a member onboards in CS UI.
POST https://{{is_host}}:{{is_admin_port}}/api/admin/v1alpha/participants/issuer/holders
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "did": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
    "holderId": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
    "name": "SS0 Holder (dev mock)",
    "properties": {
        "membershipType": "X-Road"
    }
}

HTTP *
[Asserts]
status < 300 or status == 409

# 1.3 Register holder = ss1's identity-hub DID.
POST https://{{is_host}}:{{is_admin_port}}/api/admin/v1alpha/participants/issuer/holders
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "did": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
    "holderId": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
    "name": "SS1 Holder (dev mock)",
    "properties": {
        "membershipType": "X-Road"
    }
}

HTTP *
[Asserts]
status < 300 or status == 409

# 1.4 Create attestation definition "xroad-membership".
# Production: attestation schema curated and versioned by NIIS.
POST https://{{is_host}}:{{is_admin_port}}/api/admin/v1alpha/participants/issuer/attestations
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "attestationType": "holder",
    "configuration": {},
    "id": "xroad-membership-attestation-definition"
}

HTTP *
[Asserts]
status < 300 or status == 409

# 1.5 Create credential definition "xroad-membership" -> emits MembershipCredential VCs.
POST https://{{is_host}}:{{is_admin_port}}/api/admin/v1alpha/participants/issuer/credentialdefinitions
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "attestations": ["xroad-membership-attestation-definition"],
    "credentialType": "MembershipCredential",
    "id": "xroad-membership-credential-definition",
    "jsonSchema": "{}",
    "jsonSchemaUrl": "https://example.com/schema/MembershipCredential.json",
    "mappings": [
        {
            "input": "membershipType",
            "output": "credentialSubject.membershipType",
            "required": "true"
        }
    ],
    "rules": [],
    "format": "VC1_0_JWT",
    "validity": "604800"
}

HTTP *
[Asserts]
status < 300 or status == 409

# =============================================================================
# 2. IDENTITY HUB — per-SS participant context + credential issuance
# =============================================================================
#
# Readiness gate: do not enter section 2 until the issuer participant context
# from section 1 is fully active and its DID document is published. Without
# this, `--retry` on section 2's credential-request poll only retries the
# *poll*, not the underlying request — once the IH stores a holder cred
# request in ERROR (because the issuer DID couldn't resolve at section-1 time),
# every retry just re-reads the same ERROR row. The gate below blocks
# section 2 until section 1's effects are observable on the wire.

# Gate: issuer DID document is published (200 with full JSON, not 204).
GET https://{{is_host}}:{{is_did_port}}/issuer/did.json

HTTP 200
[Asserts]
jsonpath "$.id" == "did:web:{{is_host}}%3A{{is_did_port}}:issuer"

# --- 2a. xrd-ss0 ---

# 2a.1 Create IH participant context on ss0.
POST https://{{ss0_host}}:{{ih_id_port}}/api/identity/v1alpha/participants
Authorization: Bearer {{ih_provisioner_token}}
Content-Type: application/json

{
    "roles": [],
    "serviceEndpoints": [
        {
            "type": "CredentialService",
            "serviceEndpoint": "https://{{ss0_host}}:{{ih_cred_port}}/api/credentials/v1/participants/{{ss0_host}}",
            "id": "did:web:{{ss0_host}}%3A{{ih_did_port}}-credential-service"
        }
    ],
    "active": true,
    "participantContextId": "{{ss0_host}}",
    "did": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
    "key": {
        "keyId": "did:web:{{ss0_host}}%3A{{ih_did_port}}#key-1",
        "privateKeyAlias": "did:web:{{ss0_host}}%3A{{ih_did_port}}-key",
        "keyGeneratorParams": {
            "algorithm": "EdDSA"
        }
    }
}

HTTP *
[Asserts]
status < 300 or status == 409

# 2a.2 Submit credential request from issuer for ss0's IH.
POST https://{{ss0_host}}:{{ih_id_port}}/api/identity/v1alpha/participants/{{ss0_host}}/credentials/request
Authorization: Bearer {{ih_admin_token}}
Content-Type: application/json

{
    "issuerDid": "did:web:{{is_host}}%3A{{is_did_port}}:issuer",
    "holderPid": "xroad-membership-credential-request",
    "credentials": [
        {
            "format": "VC1_0_JWT",
            "type": "MembershipCredential",
            "id": "xroad-membership-credential-definition"
        }
    ]
}

HTTP *
[Asserts]
status < 300 or status == 409

# 2a.3 Poll credential request status; expect ISSUED. Hurl --retry handles retry.
# If ERROR, fail loud — points to JWKS / DID resolution / network misconfig.
GET https://{{ss0_host}}:{{ih_id_port}}/api/identity/v1alpha/participants/{{ss0_host}}/credentials/request/xroad-membership-credential-request
Authorization: Bearer {{ih_admin_token}}

HTTP 200
[Asserts]
jsonpath "$.status" != "ERROR"
jsonpath "$.status" == "ISSUED"

# --- 2b. xrd-ss1 ---

# 2b.1 Create IH participant context on ss1.
POST https://{{ss1_host}}:{{ih_id_port}}/api/identity/v1alpha/participants
Authorization: Bearer {{ih_provisioner_token}}
Content-Type: application/json

{
    "roles": [],
    "serviceEndpoints": [
        {
            "type": "CredentialService",
            "serviceEndpoint": "https://{{ss1_host}}:{{ih_cred_port}}/api/credentials/v1/participants/{{ss1_host}}",
            "id": "did:web:{{ss1_host}}%3A{{ih_did_port}}-credential-service"
        }
    ],
    "active": true,
    "participantContextId": "{{ss1_host}}",
    "did": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
    "key": {
        "keyId": "did:web:{{ss1_host}}%3A{{ih_did_port}}#key-1",
        "privateKeyAlias": "did:web:{{ss1_host}}%3A{{ih_did_port}}-key",
        "keyGeneratorParams": {
            "algorithm": "EdDSA"
        }
    }
}

HTTP *
[Asserts]
status < 300 or status == 409

# 2b.2 Submit credential request from issuer for ss1's IH.
POST https://{{ss1_host}}:{{ih_id_port}}/api/identity/v1alpha/participants/{{ss1_host}}/credentials/request
Authorization: Bearer {{ih_admin_token}}
Content-Type: application/json

{
    "issuerDid": "did:web:{{is_host}}%3A{{is_did_port}}:issuer",
    "holderPid": "xroad-membership-credential-request",
    "credentials": [
        {
            "format": "VC1_0_JWT",
            "type": "MembershipCredential",
            "id": "xroad-membership-credential-definition"
        }
    ]
}

HTTP *
[Asserts]
status < 300 or status == 409

# 2b.3 Poll credential request status; expect ISSUED.
GET https://{{ss1_host}}:{{ih_id_port}}/api/identity/v1alpha/participants/{{ss1_host}}/credentials/request/xroad-membership-credential-request
Authorization: Bearer {{ih_admin_token}}

HTTP 200
[Asserts]
jsonpath "$.status" != "ERROR"
jsonpath "$.status" == "ISSUED"

# =============================================================================
# 3. CONTROL PLANE — participant ctx + STS-bound config (per SS)
# =============================================================================
#
# Uses /api/management/v5beta/* (renamed from v4alpha in Phase 11 / XRDDEV-3153).
# Permissions are deliberately wide-open here — production must scope token
# audiences and bind STS secrets via OpenBao.
#
# Note: edc.iam.sts.* entries point each CP at the IH co-located on the same
# host (xrd-ss0 -> xrd-ss0:7184). The secret alias follows the per-SS
# convention `<ssN_host>-sts-client-secret` and is pre-seeded by IH
# provisioning; renaming it here without re-seeding the vault breaks DSP auth.

# --- 3a. xrd-ss0 ---

# 3a.1 Create CP participant context on ss0.
POST https://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/management/v5beta/participants
Authorization: Bearer {{cp_provisioner_token}}
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContext",
    "identity": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
    "@id": "{{ss0_host}}"
}

HTTP *
[Asserts]
status < 300 or status == 409

# 3a.2 PUT participant context config — STS endpoints + DID.
# Returns 204 No Content on success (per EDC v5beta).
PUT https://{{ss0_host}}:{{ds_cp_mgmt_port}}/api/management/v5beta/participants/{{ss0_host}}/config
Authorization: Bearer {{cp_provisioner_token}}
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContextConfig",
    "entries": {
        "edc.participant.id": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
        "edc.participant.did": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
        "edc.iam.sts.oauth.token.url": "https://{{ss0_host}}:{{ih_sts_port}}/api/sts/token",
        "edc.iam.sts.oauth.client.id": "did:web:{{ss0_host}}%3A{{ih_did_port}}",
        "edc.iam.sts.oauth.client.secret.alias": "{{ss0_host}}-sts-client-secret"
    },
    "privateEntries": {}
}

HTTP *
[Asserts]
status < 300 or status == 409

# --- 3b. xrd-ss1 ---

# 3b.1 Create CP participant context on ss1.
POST https://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/management/v5beta/participants
Authorization: Bearer {{cp_provisioner_token}}
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContext",
    "identity": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
    "@id": "{{ss1_host}}"
}

HTTP *
[Asserts]
status < 300 or status == 409

# 3b.2 PUT participant context config on ss1.
PUT https://{{ss1_host}}:{{ds_cp_mgmt_port}}/api/management/v5beta/participants/{{ss1_host}}/config
Authorization: Bearer {{cp_provisioner_token}}
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContextConfig",
    "entries": {
        "edc.participant.id": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
        "edc.participant.did": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
        "edc.iam.sts.oauth.token.url": "https://{{ss1_host}}:{{ih_sts_port}}/api/sts/token",
        "edc.iam.sts.oauth.client.id": "did:web:{{ss1_host}}%3A{{ih_did_port}}",
        "edc.iam.sts.oauth.client.secret.alias": "{{ss1_host}}-sts-client-secret"
    },
    "privateEntries": {}
}

HTTP *
[Asserts]
status < 300 or status == 409
