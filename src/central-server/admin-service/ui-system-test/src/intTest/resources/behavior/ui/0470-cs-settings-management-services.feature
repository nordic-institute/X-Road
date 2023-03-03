@CentralServer
@ManagementServices
Feature: CS: System Settings -> System parameters  -> Management services

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And System settings sub-tab is selected

  Scenario: Management services configuration is displayed
    Then service provider identifier field should have value SUBSYSTEM:CS-E2E:ORG:2908758-4:Management
    And service provider name field should have value TestOrg
    And security server field should have value SERVER:CS-E2E:GOV:0245437-2:SS1; SERVER:CS-E2E:ORG:2908758-4:SS0
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And central server address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners

  Scenario: Management service provider can be changed
    When edit management member button is clicked
    And TestSaved is written in search field
    And checkbox for subsystem TestSaved is selected
    And select button is clicked
    Then service provider identifier field should have value SUBSYSTEM:CS:GOV:0245437-2:TestSaved
    And service provider name field should have value TestGov
    And security server field should have value SERVER:CS:GOV:0245437-2:SS1
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And central server address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners
    And success snackbar should be visible
