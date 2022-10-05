@CentralServer
@Member
Feature: Central Server Member Details View

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

    And CentralServer Settings tab is selected
    And System settings tab is selected
    And A new member class E2E is added
    And Members tab is selected
    And A new member with name: E2E Test Member Detail, code: e2e-test-member-detail & member class: E2E is added

  Scenario: Member detail info is showing and member name can change and is able to delete the member
    Given Members tab is selected
    And Member E2E Test Member Detail is selected
    And The member name: E2E Test Member Detail, code: e2e-test-member-detail and class: E2E are correctly shown
    And The Owned Servers table is correctly shown
    And The Global Groups table is correctly shown
    And The name of the member is able to changed
    And Deleting the member requires the user to input the member code: e2e-test-member-detail
