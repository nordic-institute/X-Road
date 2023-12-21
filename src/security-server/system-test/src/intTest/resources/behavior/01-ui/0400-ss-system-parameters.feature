@SecurityServer
@Initialization
Feature: 0400 - SS: System Parameters
  Verify that SS can be initialized from fresh state.

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Settings tab is selected
    And System Parameters sub-tab is selected

  Scenario: Security server address is update fails
    Given Security Server address is displayed
    And Security Server address edit button is enabled
    When Security Server address edit button is clicked
    And new Security Server address "new.address" is submitted
    Then error: "Sending of management request failed" was displayed

  Scenario: Timestamping service is selected and deleted
    Given Timestamping services table has 0 entries
    When Add Timestamping services dialog is opened
    When First timestamping option is selected
    Then Timestamping services table has 1 entries
    When Timestamping service on row 0 is deleted
    Then Timestamping services table has 0 entries

  Scenario: Timestamping service is selected
    And Timestamping services table has 0 entries
    When Add Timestamping services dialog is opened
    And Add Timestamping services dialog is closed
    And Add Timestamping services dialog is opened
    And First timestamping option is selected
    Then  Timestamping services table has 1 entries
    And Timestamping services table row 0 has service "X-Road Test TSA CN" and url "http://ca:8899"
