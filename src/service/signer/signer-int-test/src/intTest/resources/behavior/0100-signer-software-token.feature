@SoftToken
Feature: 0100 - Signer: SoftToken

  Background:
    Given tokens are listed

  Scenario: Token has its friendly name updated
    When name "soft-token-000" is set for token with id "0"
    Then token with id "0" name is "soft-token-000" on primary node
    When secondary node sync is forced
    Then token with id "0" name is "soft-token-000" on secondary node

  Scenario: Token is in initialized
    Given tokens list contains token "soft-token-000"
    And token "soft-token-000" status is "NOT_INITIALIZED"
    When signer is initialized with pin "1234"
    Then token "soft-token-000" is not active
    And token "soft-token-000" status is "OK"

  Scenario: Token must be manually activated when when the autologin configuration does not match
    When Waiting 4 seconds for auto-login to take effect
    Given token "soft-token-000" is not active
    When token "soft-token-000" is logged in with pin "1234"
    Then token "soft-token-000" is active

  Scenario: Token is deactivated
    When token "soft-token-000" is logged out
    Then token "soft-token-000" is not active

  Scenario: Token pin is updated to match the autologin configuration
    Given token "soft-token-000" is not active
    And token "soft-token-000" is logged in with pin "1234"
    When token "soft-token-000" pin is updated from "1234" to "4321"
    And signer service is restarted
    And Waiting 4 seconds for auto-login to take effect
    Then token "soft-token-000" is active

