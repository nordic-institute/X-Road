@SecurityServer
@UI
@Diagnostics
@DiagnosticsOverview
Feature: 0900 - SS:Diagnostics - Overview

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!

  @Download
  Scenario: Administrator can download diagnostics report
    Given Diagnostics tab is selected
    When download diagnostic report button is clicked
    Then downloaded diagnostic report contains required data

  @Skip
  Scenario: Message log archive encryption should have per member configuration
    When Diagnostics tab is selected
    And Message log grouping is set to MEMBER
    And At least one member should have encryption key configured
    And At least one member should use default encryption key
