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
