@CentralServer
@SecurityServers
Feature: CS: Security server: Authentication certificates

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Security Servers tab is selected

  Scenario: Security server authentication certificates for "SS-X" are listed
    Given user opens security server details for "SS-X"
    And navigates to security server authentication certificates tab
    Then A client with name: E2E TC1 Member with Subsystems, code: e2e-tc1-member-subsystem, class: E2E-TC1 & subsystem: 1122 is listed
    And A client with name: E2E TC3 Member with Subsystems, code: e2e-tc3-member-subsystem, class: E2E-TC1 & subsystem: 4455 is listed

  Scenario: Security server clients can be sorted by subsystem
    Given user opens security server details for "SS-X"
    And navigates to security server clients tab
    Then user can sort list by subsystem


