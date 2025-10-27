Feature: 0400 - Signer: Secondary node tests

  Background:
    Given tokens are listed
    And tokens are listed on secondary node

  Scenario: Write operations are not allowed on secondary node
    * Init software token on secondary node not allowed
    * Set token friendly name on secondary node not allowed
    * Delete token on secondary node not allowed

  Scenario: Activate token on secondary node
    When token "soft-token-000" is logged in with pin "4321" on secondary node
    Then token "soft-token-000" is active on secondary node
    And Update token pin on secondary node not allowed
    And Generate new key on secondary node not allowed

  Scenario: Signing data on secondary node
    Given new RSA key "key-test-secondary-1" generated for token "soft-token-000"
    And new EC key "key-test-secondary-2" generated for token "soft-token-000"
    And new RSA key "key-test-secondary-3" generated for token "xrd-softhsm-0"
    And new EC key "key-test-secondary-4" generated for token "xrd-softhsm-0"
    And secondary node sync is forced
    When token "xrd-softhsm-0" is logged in with pin "1234" on secondary node
    And token "soft-token-000" is logged in with pin "4321" on secondary node
    Then digest can be signed using key "key-test-secondary-1" from token "soft-token-000" on secondary node
    And digest can be signed using key "key-test-secondary-2" from token "soft-token-000" on secondary node
    And digest can be signed using key "key-test-secondary-3" from token "xrd-softhsm-0" on secondary node
    And digest can be signed using key "key-test-secondary-4" from token "xrd-softhsm-0" on secondary node

  Scenario: Loading token with transient certificate from HSM
    Given all keys are deleted from token "xrd-softhsm-0"
    And token "xrd-softhsm-0" has 0 key on primary node
    And primary node is refreshed
    And secondary node sync is forced
    When new key with id "1357" and certificate magically appears on HSM
    And primary node is refreshed
    And secondary node sync is forced
    Then token "xrd-softhsm-0" has 1 key on secondary node
    And token "xrd-softhsm-0" token is not saved to configuration on primary node
    And token "xrd-softhsm-0" token is not saved to configuration on secondary node
