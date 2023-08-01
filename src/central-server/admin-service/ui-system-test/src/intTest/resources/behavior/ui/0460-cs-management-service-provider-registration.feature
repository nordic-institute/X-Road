@CentralServer
@Member
Feature: 0460 - CS: management service provider registration

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

  Scenario: Initial management services configuration is displayed
    Given CentralServer Settings tab is selected
    And System settings sub-tab is selected
    Then service provider identifier field should be empty
    And service provider name field should be empty
    And security server field should be empty
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And management services address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners

  Scenario: Registering management service provider
    Given Members tab is selected
    And A new member with name: E2E Management Member, code: e2e-member-management & member class: E2E-TC1 is added
    And Member E2E Management Member is selected
    And Subsystems tab is selected
    And A new subsystem with code: e2e-sub-management is added
    And CentralServer Settings tab is selected
    And System settings sub-tab is selected
    And service provider identifier field should be empty
    When edit management member button is clicked
    Then Select Subsystem dialog should be open
    When e2e-sub-management is written in search field
    And checkbox for subsystem e2e-sub-management is selected
    And select button is clicked
    Then service provider identifier field should have value SUBSYSTEM:CS-E2E:E2E-TC1:e2e-member-management:e2e-sub-management
    And service provider name field should have value E2E Management Member
    And security server field should be empty
    And wsdl address field should have value http://valid-edited.example.org/managementservices.wsdl
    And management services address field should have value https://valid-edited.example.org:4002/managementservice/manage/
    And security server owner group code field should have value security-server-owners
    And success snackbar should be visible
