@SecurityServer
@Diagnostics
Feature: Diagnostics tab checks
  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret

  Scenario: Diagnostics checks are successful
    When Diagnostics tab is selected
    Then Java version status should be ok
    And Global configuration status should be ok
    And Timestamping status should be ok
    And OCSP responders status should be ok




