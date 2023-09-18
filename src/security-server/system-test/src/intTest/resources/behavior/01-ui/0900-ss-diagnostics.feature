@SecurityServer
@Diagnostics
Feature: 0900 - SS:Diagnostics

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
    And Backup encryption is enabled
    And Backup encryption configuration has 3 keys

  Scenario: Message log encryption is enabled
    When Diagnostics tab is selected
    Then Message log archive encryption is enabled
    And Message log database encryption is enabled
    And Message log grouping is set to NONE

  @Skip
  Scenario: Message log archive encryption should have per member configuration
    When Diagnostics tab is selected
    And Message log grouping is set to MEMBER
    And At least one member should have encryption key configured
    And At least one member should use default encryption key

