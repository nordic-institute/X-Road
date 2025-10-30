@SecurityServer
@UI
@Traffic
@Skip #TODO beta1 release preparation
Feature: 0900 - SS:Diagnostics - Traffic

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Diagnostics tab is selected
    And Traffic sub-tab is selected

  Scenario: Default filter and traffic chart is displayed
    Then Client is not selected
    And Exchange role is not selected
    And Status is not selected
    And Service select is disabled
    And Traffic chart is visible

  Scenario: Services are loaded
    Given Client "DEV:COM:1234:TestService" is selected
    Then Service select is enabled
    And Service "s4c2" is present

