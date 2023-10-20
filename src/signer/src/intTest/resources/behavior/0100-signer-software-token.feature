@SoftToken
Feature: 0100 - Signer: SoftToken

  Background:
    Given tokens are listed

  Scenario: Token has its friendly name updated
    When name "soft-token-000" is set for token with id "0"
    Then token with id "0" name is "soft-token-000"

  Scenario: Token is in initialized
    Given tokens list contains token "soft-token-000"
    And token "soft-token-000" status is "NOT_INITIALIZED"
    When signer is initialized with pin "1234"
    Then token "soft-token-000" is not active
    And token "soft-token-000" status is "OK"

  Scenario: Token is activated
    Given token "soft-token-000" is not active
    When token "soft-token-000" is logged in with pin "1234"
    Then token "soft-token-000" is active

  Scenario: Token is deactivated
    When token "soft-token-000" is logged out
    Then token "soft-token-000" is not active

  Scenario: Token pin is updated
    Given token "soft-token-000" is not active
    And token "soft-token-000" is logged in with pin "1234"
    When token "soft-token-000" pin is updated from "1234" to "4321"
    And token "soft-token-000" is logged in with pin "4321"
    Then token "soft-token-000" is active

  Scenario: Keys are generated
    When new key "key-1" generated for token "soft-token-000"
    And name "First key" is set for generated key
    When new key "key-2" generated for token "soft-token-000"
    And name "Second key" is set for generated key
    When new key "key-3" generated for token "soft-token-000"
    And name "Third key" is set for generated key
    Then token "soft-token-000" has exact keys "First key,Second key,Third key"
    And sign mechanism for token "soft-token-000" key "Second key" is not null

  Scenario: Key is deleted
    Given new key "key-X" generated for token "soft-token-000"
    And name "KeyX" is set for generated key
    Then token info can be retrieved by key id
    When key "Third key" is deleted from token "soft-token-000"
    Then token "soft-token-000" has exact keys "First key,Second key,KeyX"

  Scenario: A key with Sign certificate is created
    Given new key "key-10" generated for token "soft-token-000"
    And name "SignKey from CA" is set for generated key
    And token "soft-token-000" has exact keys "First key,Second key,KeyX,SignKey from CA"
    And sign mechanism for token "soft-token-000" key "SignKey from CA" is not null
    When the SIGNING cert request is generated for token "soft-token-000" key "SignKey from CA" for client "CS:ORG:2908758-4:Management"
    And SIGN CSR is processed by test CA
    And Generated certificate with initial status "registered" is imported for client "CS:ORG:2908758-4:Management"
    Then token info can be retrieved by key id

  Scenario: A key with Auth certificate is created
    Given new key "key-20" generated for token "soft-token-000"
    And name "AuthKey from CA" is set for generated key
    And token "soft-token-000" has exact keys "First key,Second key,KeyX,SignKey from CA,AuthKey from CA"
    And sign mechanism for token "soft-token-000" key "AuthKey from CA" is not null
    When the AUTHENTICATION cert request is generated for token "soft-token-000" key "AuthKey from CA" for client "CS:ORG:2908758-4:Management"
    And CSR is processed by test CA
    And Generated certificate with initial status "registered" is imported for client "CS:ORG:2908758-4:Management"
    Then token info can be retrieved by key id

  Scenario: Sign fails with an unknown algorithm error
    Given digest can be signed using key "KeyX" from token "soft-token-000"
    And Signing with unknown algorithm fails using key "KeyX" from token "soft-token-000"

  Scenario: Generate/Regenerate cert request
    When the SIGNING cert request is generated for token "soft-token-000" key "Second key" for client "CS:test:member-2"
    And token and key can be retrieved by cert request
    Then cert request can be deleted
    When the SIGNING cert request is generated for token "soft-token-000" key "Second key" for client "CS:test:member-2"
    And cert request is regenerated

  Scenario: Self Signed certificate can be (re)imported
    Given tokens list contains token "soft-token-000"
    When Wrong Certificate is not imported for client "CS:test:member-2"
    And self signed cert generated for token "soft-token-000" key "Second key", client "CS:test:member-2"
    And certificate info can be retrieved by cert hash
    When certificate can be deleted
    Then token "soft-token-000" key "Second key" has 0 certificates
    When Certificate is imported for client "CS:test:member-2"
    Then token "soft-token-000" key "Second key" has 1 certificates

  Scenario: Self signed certificate
    Given token "soft-token-000" key "First key" has 0 certificates
    When self signed cert generated for token "soft-token-000" key "First key", client "CS:test:member-1"
    Then token "soft-token-000" key "First key" has 1 certificates
    And keyId can be retrieved by cert hash
    And token and keyId can be retrieved by cert hash
    And certificate can be signed using key "First key" from token "soft-token-000"

  Scenario: Member signing info can be retrieved
    Given tokens list contains token "soft-token-000"
    * Member signing info for client "CS:ORG:2908758-4:Management" is retrieved

  Scenario: Member certs are retrieved
    Then member "CS:test:member-1" has 1 certificate

  Scenario: Cert status can be updated
    Given self signed cert generated for token "soft-token-000" key "KeyX", client "CS:test:member-2"
    And certificate info can be retrieved by cert hash
    Then certificate can be deactivated
    And certificate can be activated
    And certificate status can be changed to "deletion in progress"
    And certificate can be deleted

  Scenario: Miscellaneous checks
    * check token "soft-token-000" key "First key" batch signing enabled

  Scenario: Exceptions are being handled
    * Set token name fails with TokenNotFound exception when token does not exist
    * Deleting not existing certificate from token fails
    * Retrieving token info by not existing key fails
    * Deleting not existing certRequest fails
    * Signing with unknown key fails
    * Getting key by not existing cert hash fails
    * Not existing certificate can not be activated
    * Member signing info for client "CS:test:member-1" fails if not suitable certificates are found
    * auth key retrieval for Security Server "CS:ORG:2908758-4:SS100" fails when no active token is found

  Scenario: Ocsp responses
    When ocsp responses are set
    Then ocsp responses can be retrieved
    And null ocsp response is returned for unknown certificate

#  not covered SignerProxy methods:
#  AuthKeyInfo getAuthKey(SecurityServerId serverId)                            #requires valid ocsp response
