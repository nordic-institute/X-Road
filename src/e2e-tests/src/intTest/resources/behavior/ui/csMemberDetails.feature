@CentralServer
@Member
Feature: Central Server Member Details View

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

    When CentralServer Settings tab is selected
    And System settings tab is selected
    Then A new member class E2E is added

  Scenario: Member detail info is correctly shown
    Given Members tab is selected
    When A new member with name: E2E Test Member Detail, code: e2e-test-member-detail & member class: E2E is added
    And Member E2E Test Member Detail is selected
    Then The member name: E2E Test Member Detail, code: e2e-test-member-detail and class: E2E are correctly shown
    And The Owned Servers table is correctly shown
    And The Global Groups table is correctly shown

  Scenario: Is able to change the name of the member
    Given Members tab is selected
    When Member E2E Test Member Detail is selected
    Then The name of the member is able to changed

  Scenario: Is able to delete the member and deleting the member requires the user to input the member code
    Given Members tab is selected
    When Member E2E Test Member Detail is selected
    Then Deleting the member requires the user to input the member code: e2e-test-member-detail
