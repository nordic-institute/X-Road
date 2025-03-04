@SecurityServer
@Client
Feature: 0500 - SS: Client Subsystems

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Add subsystem was cancelled
    When Subsystem add page is opened for Client "Test member"
    And Subsystem selection window is opened
    And Subsystem with ID "DEV:COM:1234:TestService" is selected from the window
    And Register subsystem is unchecked
    Then Add subsystem form is set to MemberName: "Test member", MemberClass: "COM", MemberCode: "1234", SubsystemCode: "TestService"
    When Add subsystem form is closed
    Then Client "TestService" is missing in the list


  Scenario Outline: Already existing Subsystem <$subsystemIdentifier> is added
    When Subsystem add page is opened for Client "<$member>"
    And Subsystem selection window is opened
    And Subsystem with ID "<$subsystemIdentifier>" is selected from the window
    And Register subsystem is unchecked
    Then Add subsystem form is set to MemberName: "<$member>", MemberClass: "<$memberClass>", MemberCode: "<$memberCode>", SubsystemCode: "<$subsystem>"
    And Subsystem is renamed to '<$newSubsystemName>'
    When Add subsystem form is submitted
    Then Client "<$subsystemName>" with status "<$status>" is present in the list
    Examples:
      | $member     | $memberClass | $memberCode | $subsystem    | $subsystemName | $newSubsystemName | $subsystemIdentifier       | $status    |
      | Test member | COM          | 1234        | Testservice   | Test service   | Test service      | DEV:COM:1234:TestService   | REGISTERED |
      | Test member | COM          | 1234        | Testsaved     | Test saved     | Test saved edited | DEV:COM:1234:TestSaved     | REGISTERED |
      | Test member | COM          | 1234        | test-consumer | Test consumer  | Test consumer     | DEV:COM:1234:test-consumer | SAVED      |

  Scenario: New Subsystem is added, but management registration fails
    When Subsystem add page is opened for Client "Test member"
    And Subsystem code is set to "random-sub-1"
    When Add subsystem form is submitted
    And Register client send registration request dialog is confirmed
    Then Client "undefined" with id "DEV:COM:1234:random-sub-1" and status "SAVED" is present in the list
    #And error: "Security server has no valid authentication certificate" was displayed


  Scenario: New Subsystem is added with name, but management registration fails
    When Subsystem add page is opened for Client "Test member"
    And Subsystem code is set to "named-random-sub-3"
    And Subsystem name is set to "Named random sub 3"
    When Add subsystem form is submitted
    And Register client send registration request dialog is confirmed
    Then Client "undefined" with id "DEV:COM:1234:named-random-sub-3" and status "SAVED" is present in the list
