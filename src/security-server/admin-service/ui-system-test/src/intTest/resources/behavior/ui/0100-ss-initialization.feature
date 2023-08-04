@SecurityServer
@Initialization
Feature: 0100 - SS: Initialization
  Verify that SS can be initialized from fresh state.

  Background:
    Given SecurityServer login page is open
    And User xrd logs in to SecurityServer with password secret

  Scenario: Security server is initialized
    Given Initial Configuration form is visible
    And Configuration anchor "configuration_anchor_CS_internal.xml" is uploaded
    And Configuration anchor details are confirmed
    Then Configuration anchor selection is submitted
    When Initial configuration of Owner member is set to class: GOV, code: 0245437-2 & Security Server Code: SS1
    Then Owner member configuration is submitted
    When PIN is set to "1234"
    And Confirmation PIN is set to "1234"
    And Initial Configuration is submitted
    And Server id exist warning is confirmed
    Then Clients Tab is present

  Scenario: Default token is initialized
    Given Clients tab is selected
    And Soft token pin alert is clicked
    And Token: softToken-0 is present
    When User logs in token: softToken-0 with PIN: 1234
    Then  Token: softToken-0 is logged-in
    When User logs out token: softToken-0
    Then Token: softToken-0 is logged-out
    When User logs in token: softToken-0 with PIN: 1234
    Then  Token: softToken-0 is logged-in
