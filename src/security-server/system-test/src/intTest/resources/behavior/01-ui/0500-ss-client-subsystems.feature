@SecurityServer
@Client
Feature: 0500 - SS: Client Subsystems

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Add subsystem was cancelled
    When Subsystem add page is opened for Client "TestGov"
    And Subsystem selection window is opened
    And Subsystem with ID "CS:GOV:0245437-2:TestService" is selected from the window
    And Register subsystem is unchecked
    Then Add subsystem form is set to MemberName: "TestGov", MemberClass: "GOV", MemberCode: "0245437-2", SubsystemCode: "TestService"
    When Add subsystem form is closed
    Then Client "TestService" is missing in the list


  Scenario Outline: Already existing Subsystem <$subsystem> is added
    When Subsystem add page is opened for Client "<$member>"
    And Subsystem selection window is opened
    And Subsystem with ID "<$subsystemIdentifier>" is selected from the window
    And Register subsystem is unchecked
    Then Add subsystem form is set to MemberName: "<$member>", MemberClass: "<$memberClass>", MemberCode: "<$memberCode>", SubsystemCode: "<$subsystem>"
    When Add subsystem form is submitted
    Then Client "<$subsystem>" with status "<$status>" is present in the list
    Examples:
      | $member | $memberClass | $memberCode | $subsystem    | $subsystemIdentifier           | $status    |
      | TestGov | GOV          | 0245437-2   | TestService   | CS:GOV:0245437-2:TestService   | REGISTERED |
      | TestGov | GOV          | 0245437-2   | TestSaved     | CS:GOV:0245437-2:TestSaved     | REGISTERED |
      | TestGov | GOV          | 0245437-2   | test-consumer | CS:GOV:0245437-2:test-consumer | SAVED      |

  Scenario: New Subsystem is added, but management registration fails
    When Subsystem add page is opened for Client "TestGov"
    And Subsystem code is set to "random-sub-1"
    When Add subsystem form is submitted
    And Register client send registration request dialog is confirmed
    Then Client "random-sub-1" with status "SAVED" is present in the list
    #And error: "Security server has no valid authentication certificate" was displayed
