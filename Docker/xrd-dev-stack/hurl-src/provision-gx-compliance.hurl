### Provision Gaia-X Compliance for ss0 ###

# Request a Legal Registration Number from the Gaia-X Notary
GET {{gx_notary_baseurl}}/registration-numbers/vat-id/EE102114496?vcId=https%3A%2F%2Fss0%3A9396%2Flrn.json&subjectId=https%3A%2F%2Fss0%3A9396%2Flrn.json#cs

HTTP 200

[Captures]
ss0_lrn_credential: body


# Save Gaia-X notary credential into Identity Hub
POST https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "https://ss0:9396/lrn.json",
  "participantId": "did:web:ss0%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss0_lrn_credential}}",
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
      "issuanceDate": "2024-10-09T10:39:06.629Z",
      "issuer": {
        "id": "did:web:ss0%3A9396"
      }
    }
  }
}

HTTP 204

# GET JWT-VP for Gaia-X Compliance input
GET https://{{ss0_host}}:20001/resolution/v1/participants/did%3Aweb%3Ass0%253A9396/enveloped-verifiable-credentials?vcType=GaiaXCredential

HTTP 200

[Captures]
ss0_compliance_vp_jwt: body


# Submit to Gaia-X compliance
POST {{gx_compliance_baseurl}}/api/credential-offers/basic-conformity?vcid=https%3A%2F%2Fss0%3A9396%2Fcompliance.json
Content-Type: application/vp+ld+json+jwt
```
{{ss0_compliance_vp_jwt}}
```

HTTP 201

[Captures]
ss0_compliance_credential: body


# Save Gaia-X compliance credential into Identity Hub
POST https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "https://ss0:9396/compliance.json",
  "participantId": "did:web:ss0%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss0_compliance_credential}}",
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
      "issuanceDate": "2024-10-09T10:39:06.629Z",
      "issuer": {
        "id": "did:web:ss0%3A9396"
      }
    }
  }
}

HTTP 204
