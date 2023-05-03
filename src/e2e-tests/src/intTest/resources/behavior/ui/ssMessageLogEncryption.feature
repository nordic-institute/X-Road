@SecurityServer
@Diagnostics
Feature: Configuration backup configuration
  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd-obs logs in to SecurityServer with password secret

  Scenario: Message log encryption is enabled
    When Diagnostics tab is selected
    Then Message log archive encryption is enabled
    And Message log database encryption is enabled
    And Message log grouping is set to MEMBER
    And At least one member should have encryption key configured
    And At least one member should use default encryption key






