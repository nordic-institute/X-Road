@CentralServer
@Authentication
Feature: Central Server authentication

  Scenario: User successfully authenticates
  This is temporary scenario until we introduce new ones

    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd-sec logs in to SecurityServer with password secret

