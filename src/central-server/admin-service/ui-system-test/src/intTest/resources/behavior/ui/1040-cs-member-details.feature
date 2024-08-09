@CentralServer
@Member
Feature: 1040 - CS: Member details

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Members tab is selected

  Scenario: Navigate from member details to Security Server details and back
    Given Member E2E TC1 Member with Subsystems is selected
    And owned servers contains server "E2E-SS1"
    When user clicks on owned server "E2E-SS1"
    Then security server owner name: "E2E TC1 Member with Subsystems", class: "E2E-TC1" and code: "e2e-tc1-member-subsystem" are properly displayed
    When user clicks back
    Then The member name: E2E TC1 Member with Subsystems, code: e2e-tc1-member-subsystem and class: E2E-TC1 are correctly shown

  Scenario: Member used servers are listed
    Given Member E2E TC4 Test Member is selected
    And used servers contains server "E2E-SS1"
    When user clicks on used server "E2E-SS1"
    Then security server owner name: "E2E TC1 Member with Subsystems", class: "E2E-TC1" and code: "e2e-tc1-member-subsystem" are properly displayed
    When user clicks back
    Then The member name: E2E TC4 Test Member, code: e2e-tc4-test-member and class: E2E-TC1 are correctly shown
    When user clicks button to unregister from used server "E2E-SS1"
    And user clicks cancel in Unregister client dialog
    When user clicks button to unregister from used server "E2E-SS1"
    And user clicks Yes in Unregister client dialog
    Then used servers list is empty
