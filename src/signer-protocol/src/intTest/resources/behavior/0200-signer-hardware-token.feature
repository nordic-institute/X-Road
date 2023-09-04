@HardwareToken
Feature: 0200 - Signer: HardwareToken
  Uses SoftHSM to emulate hardware token.

  Background:
    Given tokens are listed

  Scenario: HSM is operational
    * HSM is operational

  Scenario: Token has its friendly name updated
    When friendly name "xrd-softhsm-0" is set for token with label "x-road-softhsm2"
    Then token with label "x-road-softhsm2" name is "xrd-softhsm-0"

   Scenario: Token is in initialized
    Given tokens list contains token "xrd-softhsm-0"
    And token "xrd-softhsm-0" status is "OK"

  Scenario: Token is activated
    Given token "xrd-softhsm-0" is not active
    When token "xrd-softhsm-0" is logged in with pin "1234"
    Then token "xrd-softhsm-0" is active

  Scenario: Token is deactivated
    When token "xrd-softhsm-0" is logged out
    Then token "xrd-softhsm-0" is not active

  Scenario: Token pin update is not supported for hardware token
    Given token "xrd-softhsm-0" is not active
    And token "xrd-softhsm-0" is logged in with pin "1234"
    When token "xrd-softhsm-0" pin is update from "1234" to "4321" fails with an error
    Then token "xrd-softhsm-0" is active

  Scenario: Keys are generated
    When new key "key-1" generated for token "xrd-softhsm-0"
    And name "First key" is set for generated key
    When new key "key-2" generated for token "xrd-softhsm-0"
    And name "Second key" is set for generated key
    When new key "key-3" generated for token "xrd-softhsm-0"
    And name "Third key" is set for generated key
    Then token "xrd-softhsm-0" has exact keys "First key,Second key,Third key"
    And sign mechanism for token "xrd-softhsm-0" key "Second key" is not null

  Scenario: Key is deleted
    Given new key "key-X" generated for token "xrd-softhsm-0"
    And name "KeyX" is set for generated key
    Then token info can be retrieved by key id
    When key "Third key" is deleted from token "xrd-softhsm-0"
    Then token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX"

  Scenario: Cert request is (re)generated
    When the SIGNING cert request is generated for token "xrd-softhsm-0" key "Second key" for client "CS:test:member-2"
    And token and key can be retrieved by cert request
    Then cert request can be deleted
    When the SIGNING cert request is generated for token "xrd-softhsm-0" key "Second key" for client "CS:test:member-2"
    And cert request is regenerated

  Scenario: A key with Sign certificate is created
    Given new key "key-100" generated for token "xrd-softhsm-0"
    And name "SignKey from CA" is set for generated key
    And token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX,SignKey from CA"
    And sign mechanism for token "xrd-softhsm-0" key "SignKey from CA" is not null
    When the SIGNING cert request is generated for token "xrd-softhsm-0" key "SignKey from CA" for client "CS:ORG:2908758-4:Management"
    And SIGN CSR is processed by test CA
    And Generated certificate with initial status "registered" is imported for client "CS:ORG:2908758-4:Management"
    Then token info can be retrieved by key id

  Scenario: A key with Auth certificate is not created in hardware token
    Given new key "key-200" generated for token "xrd-softhsm-0"
    And name "BadAuthKey from CA" is set for generated key
    When token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX,SignKey from CA,BadAuthKey from CA"
    Then the AUTHENTICATION cert request is generated for token "xrd-softhsm-0" key "BadAuthKey from CA" for client "CS:ORG:2908758-4:Management" throws exception

  Scenario: Self signed certificate is generated
    Given token "xrd-softhsm-0" key "First key" has 0 certificates
    When self signed cert generated for token "xrd-softhsm-0" key "First key", client "CS:test:member-1"
    Then token "xrd-softhsm-0" key "First key" has 1 certificates
    And keyId can be retrieved by cert hash
    And token and keyId can be retrieved by cert hash
    And certificate can be signed using key "First key" from token "xrd-softhsm-0"

  Scenario: Self Signed Certificate can be (re)imported
    Given tokens list contains token "xrd-softhsm-0"
    When Wrong Certificate is not imported for client "CS:test:member-2"
    And self signed cert generated for token "xrd-softhsm-0" key "Second key", client "CS:test:member-2"
    And certificate info can be retrieved by cert hash
    When certificate can be deleted
    Then token "xrd-softhsm-0" key "Second key" has 0 certificates
    When Certificate is imported for client "CS:test:member-2"
    Then token "xrd-softhsm-0" key "Second key" has 1 certificates

  Scenario: Sign fails with an unknown algorithm error
    Given digest can be signed using key "KeyX" from token "xrd-softhsm-0"
    And Signing with unknown algorithm fails using key "KeyX" from token "xrd-softhsm-0"

  Scenario: Sign data is successful
    Given digest can be signed using key "SignKey from CA" from token "xrd-softhsm-0"
    And Digest is signed using key "KeyX" from token "xrd-softhsm-0"

  Scenario: Member signing info can be retrieved
    Given tokens list contains token "xrd-softhsm-0"
    * Member signing info for client "CS:ORG:2908758-4:Management" is retrieved

  Scenario: Member certs are retrieved
    Then member "CS:test:member-1" has 2 certificate

  Scenario: Cert status can be updated
    Given self signed cert generated for token "xrd-softhsm-0" key "KeyX", client "CS:test:member-2"
    And certificate info can be retrieved by cert hash
    Then certificate can be deactivated
    And certificate can be activated
    And certificate status can be changed to "deletion in progress"
    And certificate can be deleted

  Scenario: Miscellaneous checks
    * check token "xrd-softhsm-0" key "First key" batch signing enabled
