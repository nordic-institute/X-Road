@SecurityServer
@DataSpaces
Feature: 5000 - DS control plane tests

  Scenario: Participant context is created
    #Given Secret is created with key "test-part-ctx-private-key" and value "-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIMI4q3sYL6CbZml4AhnChB2JhNQz4HvdIPdTpPeqcr7N\n-----END PRIVATE KEY-----"
    #And Identity Hub participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A10100" is initialized with existing private key in vault with alias "test-part-ctx-private-key" and public key "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VwAyEAabMf5XIoieY8g7YxnS4QnQvz59Yz+2/8buyztuXAMgA=\n-----END PUBLIC KEY-----"
    Given Identity Hub participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A10100" is initialized and keypair is generated with private key alias "test-part-ctx-private-key"
    And Participant context "test-part-ctx" is created
    And Participant context "test-part-ctx" config with DID "did:web:ds-identity-hub%3A10100" is created
    And Asset is created in participant context "test-part-ctx"
    And Policy definition is created in participant context "test-part-ctx"
    And Contract definition is created in participant context "test-part-ctx"
    Then Catalog can be retrieved from participant context "test-part-ctx" with DID "did:web:ds-identity-hub%3A10100"
    And Participant context "test-part-ctx" can be retrieved
