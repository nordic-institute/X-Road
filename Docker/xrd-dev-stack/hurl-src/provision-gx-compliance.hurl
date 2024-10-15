### Provision Gaia-X Compliance for ss0 ###

# Get Gaia-X participant VC from Identity Hub
GET https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials/https%3A%2F%2Fss0%3A9396%2Fparticipant.json

HTTP 200

[Captures]
ss0_participant_credential: jsonpath "$.verifiableCredential.rawVc"


# Get Gaia-X Terms & Conditions VC from Identity Hub
GET https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials/https%3A%2F%2Fss0%3A9396%2Ftsandcs.json

HTTP 200

[Captures]
ss0_tsandcs_credential: jsonpath "$.verifiableCredential.rawVc"


# Request a Legal Registration Number from the Gaia-X Notary
POST {{gx_notary_baseurl}}/registrationNumberVC?vcid=https%3A%2F%2Fss0%3A9396%2Flrn.json
{
  "@context": [
    "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/participant"
  ],
  "type": "gx:legalRegistrationNumber",
  "id": "https://ss0:9396/lrn.json#cs",
  "gx:vatID": "EE102114496"
}

HTTP 200

[Captures]
ss0_lrn_credential: body


# Submit to Gaia-X compliance
POST {{gx_compliance_baseurl}}/api/credential-offers?vcid=https%3A%2F%2Fss0%3A9396%2Fcompliance.json
```json
{
  "@context": "https://www.w3.org/2018/credentials/v1",
  "type": "VerifiablePresentation",
  "verifiableCredential": [
    {{ss0_participant_credential}},
    {{ss0_tsandcs_credential}},
    {{ss0_lrn_credential}}
  ]
}
```

HTTP 201

[Captures]
ss0_compliance_credential: body


# Save Gaia-X notary credential into Identity Hub
POST https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "https://ss0:9396/lrn.json",
  "participantId": "did:web:ss0%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss0_lrn_credential}}",
    "format": "JSON_LD",
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


# Save Gaia-X compliance credential into Identity Hub
POST https://{{ss0_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczAlM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "https://ss0:9396/compliance.json",
  "participantId": "did:web:ss0%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss0_compliance_credential}}",
    "format": "JSON_LD",
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



### Provision Gaia-X Compliance for ss1 ###

# Get Gaia-X participant VC from Identity Hub
GET https://{{ss1_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczElM0E5Mzk2/credentials/https%3A%2F%2Fss1%3A9396%2Fparticipant.json

HTTP 200

[Captures]
ss1_participant_credential: jsonpath "$.verifiableCredential.rawVc"


# Get Gaia-X Terms & Conditions VC from Identity Hub
GET https://{{ss1_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczElM0E5Mzk2/credentials/https%3A%2F%2Fss1%3A9396%2Ftsandcs.json

HTTP 200

[Captures]
ss1_tsandcs_credential: jsonpath "$.verifiableCredential.rawVc"


# Request a Legal Registration Number from the Gaia-X Notary
POST {{gx_notary_baseurl}}/registrationNumberVC?vcid=https%3A%2F%2Fss1%3A9396%2Flrn.json
{
  "@context": [
    "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/participant"
  ],
  "type": "gx:legalRegistrationNumber",
  "id": "https://ss1:9396/lrn.json#cs",
  "gx:vatID": "EE102114496"
}

HTTP 200

[Captures]
ss1_lrn_credential: body


# Submit to Gaia-X compliance
POST {{gx_compliance_baseurl}}/api/credential-offers?vcid=https%3A%2F%2Fss1%3A9396%2Fcompliance.json
```json
{
  "@context": "https://www.w3.org/2018/credentials/v1",
  "type": "VerifiablePresentation",
  "verifiableCredential": [
    {{ss1_participant_credential}},
    {{ss1_tsandcs_credential}},
    {{ss1_lrn_credential}}
  ]
}
```

HTTP 201

[Captures]
ss1_compliance_credential: body


# Save Gaia-X notary credential into Identity Hub
POST https://{{ss1_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczElM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "https://ss1:9396/lrn.json",
  "participantId": "did:web:ss1%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss1_lrn_credential}}",
    "format": "JSON_LD",
    "credential": {
      "credentialSubject": [
        {
          "id": "test-subject",
          "test-key": "test-val"
        }
      ],
      "id": "did:web:ss1%3A9396",
      "type": ["XRoadCredential"],
      "issuanceDate": "2024-10-09T10:39:06.629Z",
      "issuer": {
        "id": "did:web:ss1%3A9396"
      }
    }
  }
}

HTTP 204


# Save Gaia-X compliance credential into Identity Hub
POST https://{{ss1_host}}:9393/identity/v1alpha/participants/ZGlkOndlYjpzczElM0E5Mzk2/credentials
Content-Type: application/json
{
  "id": "https://ss1:9396/compliance.json",
  "participantId": "did:web:ss1%3A9396",
  "verifiableCredentialContainer": {
    "rawVc": "{{ss1_compliance_credential}}",
    "format": "JSON_LD",
    "credential": {
      "credentialSubject": [
        {
          "id": "test-subject",
          "test-key": "test-val"
        }
      ],
      "id": "did:web:ss1%3A9396",
      "type": ["XRoadCredential"],
      "issuanceDate": "2024-10-09T10:39:06.629Z",
      "issuer": {
        "id": "did:web:ss1%3A9396"
      }
    }
  }
}

HTTP 204
