@SecurityServer
@UI
@Client
Feature: 0520 - SS: Client Details

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Subsystem rename allowed multiple times on saved client
    Given Client with id: "DEV:COM:1234:named-random-sub-3" is opened
    And Subsystem is rename status is: 'Name change will be applied on client registration'
    When Client Edit button is clicked
    Then Rename dialog save button is disabled
    When Subsystem name is set to ""
    Then Rename dialog save button is disabled
    When Subsystem name is set to "Updated1"
    Then Rename dialog save button is active
    When Dialog data is saved and success message 'Subsystem name change successfully added and will be applied on client registration' is shown
    Then Subsystem is rename status is: 'Name change will be applied on client registration'
    When Client Edit button is clicked
    Then Rename dialog save button is disabled
    When Subsystem name is set to "Updated2"
    Then Rename dialog save button is active
    When Dialog data is saved and success message 'Subsystem name change successfully added and will be applied on client registration' is shown
    Then Subsystem is rename status is: 'Name change will be applied on client registration'

  Scenario: Subsystem rename request is sent imidiately
    Given Client "Test service" is opened
    And Subsystem is rename status is: 'Name change will be applied on client registration'
    When Client Edit button is clicked
    Then Rename dialog save button is disabled
    When Subsystem name is set to ""
    Then Rename dialog save button is disabled
    When Subsystem name is set to "Updated1"
    Then Rename dialog save button is active
    Then Dialog data is saved and error message 'Sending of management request failed' is shown
    And Subsystem is rename status is: 'Name change will be applied on client registration'
