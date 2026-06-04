# =============================================================================
# DSP Mock Participant Provisioning — k8s-hosted SS2 (hybrid env)
#
# Mirrors the ss1 sections of scenarios/00-setup-dsp.hurl for the k8s SS2.
# Identity is anchored in the SAME dataspace as the LXD stack:
#   - holder registered on the LXD issuer (xrd-cs), so ss0/ss1 trust ss2
#   - IH participant ctx + credential on the k8s ds-identity-hub
#   - CP participant ctx + STS config on the k8s ds-control-plane
#
# ss2's advertised identity host is xrd-ss2.lxd — an LXD dnsmasq host-record
# pointing at the mac, where 0.0.0.0 kubectl port-forwards (7183/7185/8183)
# terminate into the cluster. The k8s IH/CP mgmt APIs are reached here via
# the mac-local port-forwards 7182/8182.
#
# Assumes scenarios/00-setup-dsp.hurl has already run on the LXD side
# (attestation + credential definitions exist on the issuer).
# =============================================================================

# --- 1. LXD ISSUER: register holder = ss2's identity-hub DID ---

POST https://{{is_api_host}}:{{is_admin_port}}/api/admin/v1alpha/participants/issuer/holders
Authorization: Bearer {{is_participant_token}}
Content-Type: application/json

{
    "did": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
    "holderId": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
    "name": "SS2 Holder (dev mock)",
    "properties": {
        "membershipType": "X-Road",
        "xrdMemberIdentifier": "{{ss2_member_id}}"
    }
}

HTTP *
[Asserts]
status < 500

# --- 2. K8S IDENTITY HUB: participant ctx + credential ---

# 2.1 Create IH participant context on ss2.
POST https://{{ss2_ih_api}}:{{ih_id_port}}/api/identity/v1alpha/participants
Authorization: Bearer {{ih_provisioner_token}}
Content-Type: application/json

{
    "roles": [],
    "serviceEndpoints": [
        {
            "type": "CredentialService",
            "serviceEndpoint": "https://{{ss2_ih_host}}:{{ih_cred_port}}/api/credentials/v1/participants/{{ss2_participant_id}}",
            "id": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}-credential-service"
        }
    ],
    "active": true,
    "participantContextId": "{{ss2_participant_id}}",
    "did": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
    "key": {
        "keyId": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}#key-1",
        "privateKeyAlias": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}-key",
        "keyGeneratorParams": {
            "algorithm": "EdDSA"
        }
    }
}

HTTP *
[Asserts]
status < 500

# 2.2 Submit credential request from the LXD issuer for ss2's IH.
POST https://{{ss2_ih_api}}:{{ih_id_port}}/api/identity/v1alpha/participants/{{ss2_participant_id}}/credentials/request
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
status < 500

# 2.3 Poll credential request status; expect ISSUED (hurl --retry drives the poll).
GET https://{{ss2_ih_api}}:{{ih_id_port}}/api/identity/v1alpha/participants/{{ss2_participant_id}}/credentials/request/xroad-membership-credential-request
Authorization: Bearer {{ih_admin_token}}

HTTP 200
[Asserts]
jsonpath "$.status" != "ERROR"
jsonpath "$.status" == "ISSUED"

# --- 3. K8S CONTROL PLANE: participant ctx + STS-bound config ---

# 3.1 Create CP participant context on ss2.
POST https://{{ss2_cp_api}}:{{ds_cp_mgmt_port}}/api/management/v5beta/participants
Authorization: Bearer {{cp_provisioner_token}}
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContext",
    "identity": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
    "@id": "{{ss2_participant_id}}"
}

HTTP *
[Asserts]
status < 500

# 3.2 PUT participant context config on ss2. STS lives on the in-cluster
# identity hub, so the token URL uses the k8s service name (covered by the
# ds-https certificate SANs), not the LXD-facing advertised host.
PUT https://{{ss2_cp_api}}:{{ds_cp_mgmt_port}}/api/management/v5beta/participants/{{ss2_participant_id}}/config
Authorization: Bearer {{cp_provisioner_token}}
Content-Type: application/json

{
    "@context": ["https://w3id.org/edc/connector/management/v2"],
    "@type": "ParticipantContextConfig",
    "entries": {
        "edc.participant.id": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
        "edc.participant.did": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
        "edc.iam.sts.oauth.token.url": "https://ds-identity-hub:{{ih_sts_port}}/api/sts/token",
        "edc.iam.sts.oauth.client.id": "did:web:{{ss2_ih_host}}%3A{{ih_did_port}}",
        "edc.iam.sts.oauth.client.secret.alias": "{{ss2_participant_id}}-sts-client-secret"
    },
    "privateEntries": {}
}

HTTP *
[Asserts]
status < 500
