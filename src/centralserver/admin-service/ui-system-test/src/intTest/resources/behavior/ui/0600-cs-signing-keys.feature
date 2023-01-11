@CentralServer
@SigningKeys
@LoadingTesting
Feature: CS: Global configuration: Internal configuration: Signing keys

  Background:
    Given CentralServer login page is open
    Then Browser is set in CELLULAR2G network speed
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And Internal configuration sub-tab is selected
    And Details for Token: softToken-0 is expanded

  Scenario: Internal configuration: Add Key is disabled on logged-out token
    And Token: softToken-0 is logged-out
    Then Add key button is disabled for token: softToken-0

  Scenario: Internal configuration: Add Key is enabled on logged-in token
    Given User logs in token: softToken-0 with PIN: Valid_Pin_11
    Then User adds signing key for token: softToken-0 with name: internal_key_name


