@CentralServer
@ManagementServices
Feature: 0970 - CS: Settings -> System Settings -> Management Services

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And System settings sub-tab is selected

  Scenario: Management service provider can be registered
    Given service provider identifier field should have value SUBSYSTEM:CS-E2E:E2E-TC1:e2e-member-management:e2e-sub-management
    And security server field should be empty
    And management service security server Edit button is visible
    When management service security server Edit button is clicked
    Then Select Security Server dialog should be open
    When e2e-ss3 is written in security server search field
    And checkbox for security server E2E-SS3 is selected
    And Select button for security server is clicked
    Then success snackbar should be visible
    And security server field should have SERVER:CS-E2E:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS3
    And management service security server Edit button is not visible
