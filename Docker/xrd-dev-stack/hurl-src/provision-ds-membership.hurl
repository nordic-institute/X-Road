### Provision X-Road dataspace credential for ss0 ###

# Get self-descriptive VC from Identity Hub
GET https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials/xroad-self-description

HTTP 200

[Captures]
ss0_self_description: jsonpath "$.verifiableCredential.rawVc"


# Submit to Central Server's Credential Service
POST https://{{cs_host}}:9491/membership-credential
Content-Type: application/jwt
```
{{ss0_self_description}}
```

HTTP 200

[Captures]
ss0_membership_credential: body


# Save X-Road membership credential into Identity Hub
POST https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "xroad-membership-credential",
  "participantId": "did:web:ss0%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss0_membership_credential}}",
    "format": "JWT",
    "credential": {
      "credentialSubject": [
        {
          "id": "test-subject",
          "test-key": "test-val"
        }
      ],
      "id": "did:web:ss0%3A9396",
      "type": ["XRoadCredential"],
      "issuanceDate": "2024-10-28T10:39:06.629Z",
      "issuer": {
        "id": "did:web:ss0%3A9396"
      }
    }
  }
}

HTTP 204



### Provision X-Road dataspace credential for ss1 ###

# Get self-descriptive VC from Identity Hub
GET https://{{ss1_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczElM0E5Mzk2/credentials/xroad-self-description

HTTP 200

[Captures]
ss1_self_description: jsonpath "$.verifiableCredential.rawVc"


# Submit to Central Server's Credential Service
POST https://{{cs_host}}:9491/membership-credential
Content-Type: application/jwt
```
{{ss1_self_description}}
```

HTTP 200

[Captures]
ss0_membership_credential: body


# Save X-Road membership credential into Identity Hub
POST https://{{ss1_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczElM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "xroad-membership-credential",
  "participantId": "did:web:ss1%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss0_membership_credential}}",
    "format": "JWT",
    "credential": {
      "credentialSubject": [
        {
          "id": "test-subject",
          "test-key": "test-val"
        }
      ],
      "id": "did:web:ss1%3A9396",
      "type": ["XRoadCredential"],
      "issuanceDate": "2024-10-28T10:39:06.629Z",
      "issuer": {
        "id": "did:web:ss1%3A9396"
      }
    }
  }
}

HTTP 204
