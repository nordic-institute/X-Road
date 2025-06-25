@HardwareToken
@RSA
Feature: 0210 - Signer: HardwareToken: Key operations (RSA)
  Uses SoftHSM to emulate hardware token.

  Background:
    Given tokens are listed

  Scenario: HSM is operational
    * HSM is operational

  Scenario: Keys are generated
    When new RSA key "key-1" generated for token "xrd-softhsm-0"
    And name "First key" is set for generated key
    When new RSA key "key-2" generated for token "xrd-softhsm-0"
    And name "Second key" is set for generated key
    When new RSA key "key-3" generated for token "xrd-softhsm-0"
    And name "Third key" is set for generated key
    And secondary node sync is forced
    Then token "xrd-softhsm-0" has exact keys "First key,Second key,Third key"
    And token "xrd-softhsm-0" has exact keys "First key,Second key,Third key" on secondary node
    And sign mechanism for token "xrd-softhsm-0" key "Second key" is not null
    And sign mechanism for token "xrd-softhsm-0" key "Second key" is not null on secondary node

  Scenario: Key is deleted
    Given new RSA key "key-X" generated for token "xrd-softhsm-0"
    And name "KeyX" is set for generated key
    And secondary node sync is forced
    Then token info can be retrieved by key id
    And token info can be retrieved by key id on secondary node
    When key "Third key" is deleted from token "xrd-softhsm-0"
    And secondary node sync is forced
    Then token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX"
    And token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX" on secondary node

  Scenario: Cert request is (re)generated
    When the SIGNING cert request is generated for token "xrd-softhsm-0" key "Second key" for client "DEV:test:member-2"
    And secondary node sync is forced
    And token and key can be retrieved by cert request
    And token and key can be retrieved by cert request on secondary node
    Then cert request can be deleted
    When the SIGNING cert request is generated for token "xrd-softhsm-0" key "Second key" for client "DEV:test:member-2"
    And cert request is regenerated

  Scenario: A key with Sign certificate is created
    Given new RSA key "key-100" generated for token "xrd-softhsm-0"
    And name "SignKey from CA" is set for generated key
    And secondary node sync is forced
    And token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX,SignKey from CA"
    And token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX,SignKey from CA" on secondary node
    And sign mechanism for token "xrd-softhsm-0" key "SignKey from CA" is not null
    And sign mechanism for token "xrd-softhsm-0" key "SignKey from CA" is not null on secondary node
    When the SIGNING cert request is generated for token "xrd-softhsm-0" key "SignKey from CA" for client "DEV:COM:1234:MANAGEMENT"
    And SIGN CSR is processed by test CA
    And Generated certificate with initial status "registered" is imported for client "DEV:COM:1234:MANAGEMENT"
    And secondary node sync is forced
    Then token info can be retrieved by key id
    Then token info can be retrieved by key id on secondary node

  Scenario: A key with Auth certificate is not created in hardware token
    Given new RSA key "key-200" generated for token "xrd-softhsm-0"
    And name "BadAuthKey from CA" is set for generated key
    When token "xrd-softhsm-0" has exact keys "First key,Second key,KeyX,SignKey from CA,BadAuthKey from CA"
    Then the AUTHENTICATION cert request is generated for token "xrd-softhsm-0" key "BadAuthKey from CA" for client "DEV:COM:1234:MANAGEMENT" throws exception

  Scenario: Self signed certificate is generated
    Given token "xrd-softhsm-0" key "First key" has 0 certificates
    When self signed cert generated for token "xrd-softhsm-0" key "First key", client "DEV:test:member-1"
    And secondary node sync is forced
    Then token "xrd-softhsm-0" key "First key" has 1 certificates
    And keyId can be retrieved by cert hash
    And token and keyId can be retrieved by cert hash
    Then token "xrd-softhsm-0" key "First key" has 1 certificates on secondary node
    And keyId can be retrieved by cert hash on secondary node
    And token and keyId can be retrieved by cert hash on secondary node
    And certificate can be signed using key "First key" from token "xrd-softhsm-0"

  Scenario: Self Signed Certificate can be (re)imported
    Given tokens list contains token "xrd-softhsm-0"
    When Wrong Certificate is not imported for client "DEV:test:member-2"
    And self signed cert generated for token "xrd-softhsm-0" key "Second key", client "DEV:test:member-2"
    And secondary node sync is forced
    And certificate info can be retrieved by cert hash
    And certificate info can be retrieved by cert hash on secondary node
    When certificate can be deleted
    And secondary node sync is forced
    Then token "xrd-softhsm-0" key "Second key" has 0 certificates
    Then token "xrd-softhsm-0" key "Second key" has 0 certificates on secondary node
    When Certificate is imported for client "DEV:test:member-2"
    And secondary node sync is forced
    Then token "xrd-softhsm-0" key "Second key" has 1 certificates
    Then token "xrd-softhsm-0" key "Second key" has 1 certificates on secondary node

  Scenario: Sign fails with an unknown algorithm error
    Given digest can be signed using key "KeyX" from token "xrd-softhsm-0"
    And Signing with unknown algorithm fails using key "KeyX" from token "xrd-softhsm-0"

  Scenario: Sign data is successful
    Given digest can be signed using key "SignKey from CA" from token "xrd-softhsm-0"
    And Digest is signed using key "KeyX" from token "xrd-softhsm-0"

  Scenario: Member signing info can be retrieved
    Given tokens list contains token "xrd-softhsm-0"
    * Member signing info for client "DEV:COM:1234:MANAGEMENT" is retrieved

  Scenario: Member certs are retrieved
    Then member "DEV:test:member-1" has 2 certificate
    Then member "DEV:test:member-1" has 2 certificate on secondary node

  Scenario: Cert status can be updated
    Given self signed cert generated for token "xrd-softhsm-0" key "KeyX", client "DEV:test:member-2"
    And certificate info can be retrieved by cert hash
    Then certificate can be deactivated
    And certificate can be activated
    And certificate status can be changed to "deletion in progress"
    And certificate can be deleted

  Scenario: Miscellaneous checks
    * check token "xrd-softhsm-0" key "First key" batch signing enabled
