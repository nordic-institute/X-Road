@SecurityServer
@Traffic
Feature: 0900 - SS:Diagnostics - Traffic

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Diagnostics tab is selected
    And Traffic sub-tab is selected

  Scenario: Traffic chart is displayed
    Then Traffic chart is visible



