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
