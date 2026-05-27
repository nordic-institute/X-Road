@SecurityServer
@DataSpaces
@Skip
Feature: 5000 - DS control plane tests

  Scenario: Issuer Service is provisioned
    Given Issuer Service participant context "issuer" with DID "did:web:ds-issuer-service%3A6183:issuer" is initialized and keypair is generated with private key alias "issuer-key"
    And Holder "did:web:ds-identity-hub%3A7183" with DID "did:web:ds-identity-hub%3A7183" is created in issuer service participant "issuer"
    And Attestation definition "xroad-membership-attestation-definition" of type "holder" is created in issuer service participant "issuer"
    And Credential definition "xroad-membership-credential-definition" of type "XRoadMembershipCredential" with format "VC1_0_JWT" is created in issuer service participant "issuer" with attestation "xroad-membership-attestation-definition"

  Scenario: Identity Hub is provisioned
    #Given Secret is created with key "test-part-ctx-key" and value "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIMI4q3sYL6CbZml4AhnChB2JhNQz4HvdIPdTpPeqcr7N\n-----END PRIVATE KEY-----"
    #And Identity Hub participant context "test-part-ctx" with DID did:web:ds-identity-hub%3A7183" is initialized for X-Road member "DEV/COM/MEMBER-A" with existing private key in vault with alias "test-part-ctx-key" and public key "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAabMf5XIoieY8g7YxnS4QnQvz59Yz+2/8buyztuXAMgA=\n-----END PUBLIC KEY-----"
    Given Identity Hub participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A7183" is initialized for X-Road member "DEV/COM/MEMBER-A" and keypair is generated with private key alias "test-part-ctx-key"
    When Credential request "xroad-membership-credential-request" for credential definition "xroad-membership-credential-definition" of type "XRoadMembershipCredential" from issuer "did:web:ds-issuer-service%3A6183:issuer" is submitted for participant "test-part-ctx"
    Then Credential request "xroad-membership-credential-request" for participant "test-part-ctx" reaches status "ISSUED"

  Scenario: Catalog can be retrieved over DSP protocol
    And Participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A7183" is created
    And Participant context "test-part-ctx" config with DID "did:web:ds-identity-hub%3A7183" is created
    And Asset is created in participant context "test-part-ctx"
    And Policy definition is created in participant context "test-part-ctx"
    And Contract definition is created in participant context "test-part-ctx"
    Then Catalog can be retrieved using participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A7183"
    And Participant context "test-part-ctx" can be retrieved
