@CentralServer
@ManagementServices
Feature: CS: Settings -> System Settings -> Management Services

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And System settings sub-tab is selected

  Scenario: Initial management services configuration is displayed
    Given System settings sub-tab is selected
    Then service provider identifier field should be empty
    And service provider name field should be empty
    And security server field should be empty
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And central server address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners

  Scenario: Management service provider can be changed
    Given service provider identifier field should be empty
    When edit management member button is clicked
    Then Select Subsystem dialog should be open
    When e2e-tc4-subsystem is written in search field
    And checkbox for subsystem e2e-tc4-subsystem is selected
    And select button is clicked
    Then service provider identifier field should have value SUBSYSTEM:CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc4-subsystem
    And service provider name field should have value E2E TC2 Member with Subsystems
    And security server field should be empty
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And central server address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners
    And success snackbar should be visible

  Scenario: Management service provider can be registered
    Given service provider identifier field should have value SUBSYSTEM:CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc4-subsystem
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
