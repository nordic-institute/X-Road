@SecurityServer
@DataSpaces
Feature: 5000 - DS control plane tests

  Scenario: Issuer Service is provisioned
    Given Issuance Service participant context "issuer" with DID "did:web:ds-issuance-service%3A10100:issuer" is initialized and keypair is generated with private key alias "issuer-key"
    And Holder "did:web:ds-identity-hub%3A10100" with DID "did:web:ds-identity-hub%3A10100" is created in issuance service participant "issuer"
    And Attestation definition "xroad-membership-attestation-definition" of type "holder" is created in issuance service participant "issuer"
    And Credential definition "xroad-membership-credential-definition" of type "MembershipCredential" with format "VC1_0_JWT" is created in issuance service participant "issuer" with attestation "xroad-membership-attestation-definition"

  Scenario: Identity Hub is provisioned
    #Given Secret is created with key "test-part-ctx-key" and value "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIMI4q3sYL6CbZml4AhnChB2JhNQz4HvdIPdTpPeqcr7N\n-----END PRIVATE KEY-----"
    #And Identity Hub participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A10100" is initialized with existing private key in vault with alias "test-part-ctx-key" and public key "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAabMf5XIoieY8g7YxnS4QnQvz59Yz+2/8buyztuXAMgA=\n-----END PUBLIC KEY-----"
    Given Identity Hub participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A10100" is initialized and keypair is generated with private key alias "test-part-ctx-key"
    When Credential request "xroad-membership-credential-request" for credential definition "xroad-membership-credential-definition" of type "MembershipCredential" from issuer "did:web:ds-issuance-service%3A10100:issuer" is submitted for participant "test-part-ctx"
    Then Credential request "xroad-membership-credential-request" for participant "test-part-ctx" reaches status "ISSUED"

  Scenario: Catalog can be retrieved over DSP protocol
    And Participant context "test-part-ctx" is created
    And Participant context "test-part-ctx" config with DID "did:web:ds-identity-hub%3A10100" is created
    And Asset is created in participant context "test-part-ctx"
    And Policy definition is created in participant context "test-part-ctx"
    And Contract definition is created in participant context "test-part-ctx"
    Then Catalog can be retrieved from participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A10100"
    And Participant context "test-part-ctx" can be retrieved
