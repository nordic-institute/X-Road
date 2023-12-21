@CentralServer
@Member
Feature: 0400 - CS: Members

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

  Scenario: Member detail info is correctly shown
    Given Members tab is selected
    When A new member with name: E2E Test Member Detail, code: e2e-test-member-detail & member class: E2E-TC1 is added
    And Member E2E Test Member Detail is selected
    Then The member name: E2E Test Member Detail, code: e2e-test-member-detail and class: E2E-TC1 are correctly shown
    And The Owned Servers table is correctly shown
    And The Global Groups table is correctly shown

  Scenario: Is able to change the name of the member
    Given Members tab is selected
    And Member E2E Test Member Detail is selected
    When The name of the member is changed to E2E Test Member Detail Other
    Then The member name: E2E Test Member Detail Other, code: e2e-test-member-detail and class: E2E-TC1 are correctly shown

  Scenario: Is able to delete the member and deleting the member requires the user to input the member code
    Given Members tab is selected
    And Member E2E Test Member Detail Other is selected
    Then Deleting the member requires the user to input the member code: e2e-test-member-detail

  Scenario: Search for member with long name
    Given Members tab is selected
    And A new member with name: the-name-is-longer-than-twenty-five-characters, code: long-name-code & member class: E2E-TC1 is added
    And A new member with name: another-name-is-longer-than-twenty-five-characters, code: long-name-code1 & member class: E2E-TC1 is added
    When user searches for member using "the-name-is-longer-than-twenty-five"
    Then A member with name: the-name-is-longer-than-twenty-five-characters, code: long-name-code & member class: E2E-TC1 is listed
    Then A member with name: another-name-is-longer-than-twenty-five-characters, code: long-name-code1 & member class: E2E-TC1 is not listed
