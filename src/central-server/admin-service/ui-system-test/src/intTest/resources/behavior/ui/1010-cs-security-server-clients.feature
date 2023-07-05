@CentralServer
@SecurityServers
@SecurityServerClients
Feature: 1010 - CS: Security server: Authentication certificates

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Security Servers tab is selected

  Scenario: Security server authentication certificates for "SS-X" are listed
    Given user opens security server details for "E2E-SS1"
    And navigates to security server clients tab
    Then A client with name: E2E TC2 Member with Subsystems, code: e2e-tc2-member-subsystem, class: E2E-TC1 & subsystem: e2e-tc2-subsystem is listed
    And A client with name: E2E TC3 Member with Subsystems, code: e2e-tc3-member-subsystem, class: E2E-TC1 & subsystem: e2e-tc3-subsystem is listed

  Scenario: Security server clients can be sorted by subsystem
    Given user opens security server details for "E2E-SS1"
    And navigates to security server clients tab
    Then user can sort list by subsystem


