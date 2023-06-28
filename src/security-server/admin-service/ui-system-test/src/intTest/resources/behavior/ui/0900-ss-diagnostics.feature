@SecurityServer
@Diagnostics
Feature: 0900 - SS:Diagnostics

  Background:
    Given SecurityServer login page is open
    And User xrd logs in to SecurityServer with password secret

  Scenario: Diagnostics checks are successful
    When Diagnostics tab is selected
    Then Java version status should be ok
    And Global configuration status should be ok
    And Timestamping status should be ok
    And OCSP responders status should be ok
    And Backup encryption is enabled
    And Backup encryption configuration has 3 keys

  @Skip
  Scenario: Message log encryption is enabled
    When Diagnostics tab is selected
    Then Message log archive encryption is enabled
    And Message log database encryption is enabled
    And Message log grouping is set to MEMBER
    And At least one member should have encryption key configured
    And At least one member should use default encryption key



