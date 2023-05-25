@CentralServer
@SigningKeys
@LoadingTesting
Feature: 0600 - CS: Global configuration: Internal configuration: Signing keys

  Background:
    Given CentralServer login page is open
    Then Browser is set in CELLULAR2G network speed
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And Internal configuration sub-tab is selected
    And Details for Token: softToken-0 is expanded

  Scenario: Add Key is disabled on logged-out token
    And Token: softToken-0 is logged-out
    Then Add key button is disabled for token: softToken-0

  Scenario: User can add only 2 signings keys after token log-in
    Given User logs in token: softToken-0 with PIN: Valid_Pin_11
    And Add key button is enabled for token: softToken-0
    When User adds signing key for token: softToken-0 with name: internal_key_name_1
    And User adds signing key for token: softToken-0 with name: internal_key_name_2
    Then Add key button is disabled for token: softToken-0

  Scenario: User can activate signing key
    Given Signing key: internal_key_name_1 can't be activated for token: softToken-0
    And Signing key: internal_key_name_2 can be activated for token: softToken-0
    When User activates signing key: internal_key_name_2 for token: softToken-0
    Then Signing key: internal_key_name_1 can be activated for token: softToken-0
    And Signing key: internal_key_name_2 can't be activated for token: softToken-0

  Scenario: User can't delete or activate signing key on logged out token
    Given Signing key: internal_key_name_1 can be activated for token: softToken-0
    And Signing key: internal_key_name_1 can be deleted for token: softToken-0
    And Token: softToken-0 is logged-in
    When User logs out token: softToken-0
    Then Signing key: internal_key_name_1 can't be activated for token: softToken-0
    And Signing key: internal_key_name_1 can't be deleted for token: softToken-0

  Scenario: User can delete signing key
    Given User logs in token: softToken-0 with PIN: Valid_Pin_11
    And Signing key: internal_key_name_1 can be deleted for token: softToken-0
    When User deletes signing key: internal_key_name_1 for token: softToken-0
    Then Signing key: internal_key_name_1 is not present for token: softToken-0
    And Add key button is enabled for token: softToken-0

  Scenario: User logs out token and can not re-add signing key
    Given Token: softToken-0 is logged-in
    When User logs out token: softToken-0
    Then Add key button is disabled for token: softToken-0
