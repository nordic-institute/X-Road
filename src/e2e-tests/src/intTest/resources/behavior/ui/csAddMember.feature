@CentralServer
@Member
Feature: Central Server Add Member

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

  Scenario: Member is created and listed
    Given Members tab is selected
    When A new member is added
    Then A new member is listed
