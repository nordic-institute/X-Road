@CentralServer
@ManagementServices
Feature: CS: System Settings -> System parameters  -> Management services

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected

  Scenario: Initial management services configuration is displayed
    When System settings sub-tab is selected
    Then service provider identifier field should be empty
    And service provider name field should be empty
    And security server field should be empty
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And central server address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners

  Scenario: Management service provider can be changed
    When System settings sub-tab is selected
    And edit management member button is clicked
    And e2e-tc2-subsystem is written in search field
    And checkbox for subsystem e2e-tc2-subsystem is selected
    And select button is clicked
    Then service provider identifier field should have value SUBSYSTEM:CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem
    And service provider name field should have value E2E TC2 Member with Subsystems
    And security server field should be empty
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And central server address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners
    And success snackbar should be visible
