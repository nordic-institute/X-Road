# =============================================================================
# DSP Issuer Fallback / Rollback Seed (LXD dev env)
#
# PRIMARY PATH SUPERSEDED: when xroad.dataspace.enabled=true in the CS
# application config, CS initialization (InitializationServiceImpl.initialize)
# provisions the issuer over X-Road gRPC automatically as part of CS init.
# The REST calls in this file are then redundant and harmless (idempotent).
#
# Keep this file as:
#   1. Rollback / manual re-seed tool — if the gRPC path fails or the flag
#      was disabled at init time, this re-seeds the Issuer Service directly
#      via its REST API without reinitializing the CS.
#   2. Reference for the shape of the issuer seed — used to validate that
#      the gRPC path produces the same participant context / attestation /
#      credential definition shapes.
#
# Filename prefix `00-` ensures this runs BEFORE setup.hurl (run-hurl.sh
# concatenates *.hurl files in alphabetical order). Setup.hurl's CS/SS
# initialisation invokes management-service flows that require the DSP
# control-plane participant context to already exist on each SS.
#
# What this seeds: ONLY the Issuer Service (on xrd-cs) — issuer participant ctx,
# attestation + credential definitions. No holders: the issuer runs with
# `edc.issuance.anonymous.allowed=true`, so its DcpHolderTokenVerifier auto-creates
# an anonymous holder per requesting DID. The X-Road membership credential derives
# its `xroadInstance`/`memberClass`/`memberCode` from the verified `xroadMemberClaim`,
# not the holder record, so a pre-registered holder is redundant.
#
# Identity Hub + Control Plane participant contexts (+ STS config) and the
# membership-credential issuance are provisioned per-SS by the SS reconciler,
# triggered from setup.hurl after the SIGN certs.
#
# What this DOES NOT seed:
#   - Asset / policy / contract definitions. Production behaviour: assets are
#     resolved dynamically from serverconf via the X-Road EDC asset extension
#     at request time. Dev policies/contracts are similarly auto-provisioned.
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
POST https://{{is_host}}:{{is_id_port}}/api/identity/v1beta/participants
Authorization: Bearer {{is_provisioner_token}}
Content-Type: application/json

{
    "scopes": ["identity-api:admin", "issuer-admin-api:write", "issuer-admin-api:read"],
    "serviceEndpoints": [
        {
            "type": "IssuerService",
            "serviceEndpoint": "https://{{is_host}}:{{is_iss_port}}/api/issuance/v1beta/participants/issuer",
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
status < 500

# 1.4 Create attestation definition "xroad-membership".
# Production: attestation schema curated and versioned by NIIS.
POST https://{{is_host}}:{{is_admin_port}}/api/admin/v1beta/participants/issuer/attestations
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "attestationType": "holder",
    "configuration": {},
    "id": "xroad-membership-attestation-definition"
}

HTTP *
[Asserts]
status < 500

# 1.5 Create credential definition "xroad-membership" -> emits XRoadMembershipCredential VCs.
POST https://{{is_host}}:{{is_admin_port}}/api/admin/v1beta/participants/issuer/credentialdefinitions
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "attestations": ["xroad-membership-attestation-definition"],
    "credentialType": "XRoadMembershipCredential",
    "id": "xroad-membership-credential-definition",
    "jsonSchema": "{}",
    "jsonSchemaUrl": "https://example.com/schema/XRoadMembershipCredential.json",
    "mappings": [
        {
            "input": "membershipType",
            "output": "credentialSubject.membershipType",
            "required": "true"
        },
        {
            "input": "xroadInstance",
            "output": "credentialSubject.xroadInstance",
            "required": "true"
        },
        {
            "input": "memberClass",
            "output": "credentialSubject.memberClass",
            "required": "true"
        },
        {
            "input": "memberCode",
            "output": "credentialSubject.memberCode",
            "required": "true"
        }
    ],
    "rules": [],
    "format": "VC1_0_JWT",
    "validity": "2592000"
}

HTTP *
[Asserts]
status < 500
