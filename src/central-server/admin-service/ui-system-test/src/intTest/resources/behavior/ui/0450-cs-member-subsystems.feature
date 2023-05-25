@CentralServer
@Member
Feature: 0450 - CS: Member details view Subsystem tab

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

  Scenario: The Subsystems table are correctly shown
    Given Members tab is selected
    When A new member with name: E2E TC1 Member with Subsystems, code: e2e-tc1-member-subsystem & member class: E2E-TC1 is added
    And Member E2E TC1 Member with Subsystems is selected
    And Subsystems tab is selected
    Then Subsystems table are correctly shown

  Scenario: The subsystem can be added by code and the added subsystem immediately appears in the table
    Given Members tab is selected
    And A new member with name: E2E TC2 Member with Subsystems, code: e2e-tc2-member-subsystem & member class: E2E-TC1 is added
    And Member E2E TC2 Member with Subsystems is selected
    And Subsystems tab is selected
    When A new subsystem with code: e2e-tc2-subsystem is added
    Then Subsystem with code: e2e-tc2-subsystem and status: Unregistered is listed
    When A new subsystem with code: e2e-tc4-subsystem is added
    Then Subsystem with code: e2e-tc4-subsystem and status: Unregistered is listed

  Scenario: Can be delete an unregistered subsystem
    Given Members tab is selected
    And A new member with name: E2E TC3 Member with Subsystems, code: e2e-tc3-member-subsystem & member class: E2E-TC1 is added
    And Member E2E TC3 Member with Subsystems is selected
    And Subsystems tab is selected
    When A new subsystem with code: e2e-tc3-subsystem is added
    And Subsystem with code: e2e-tc3-subsystem and status: Unregistered is listed
    When Subsystem with code: e2e-tc3-subsystem and status: Unregistered is deleted
    Then Subsystem with code: e2e-tc3-subsystem and status: Unregistered not listed any more
    When A new subsystem with code: e2e-tc3-subsystem is added
    And Subsystem with code: e2e-tc3-subsystem and status: Unregistered is listed

