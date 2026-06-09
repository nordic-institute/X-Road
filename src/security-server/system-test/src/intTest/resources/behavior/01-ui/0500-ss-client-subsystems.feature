@SecurityServer
@UI
@Client
Feature: 0500 - SS: Client Subsystems

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Add subsystem was cancelled
    When Subsystem add page is opened for Client "Test member"
    And Subsystem selection window is opened
    And Subsystem with ID "DEV:COM:1234:TestService" is selected from the window
    And Register subsystem is unchecked
    Then Add subsystem form is set to MemberName: "Test member", MemberClass: "COM", MemberCode: "1234", SubsystemCode: "TestService"
    When Add subsystem form is closed
    Then Client "TestService" is missing in the list


