@CentralServer
@SecurityServers
@SecurityServerDetails
Feature: 1000 - CS: Security server: Details

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Security Servers tab is selected

  Scenario Outline: Security server details for <serverCode> are visible
    Given user opens security server details for "<serverCode>"
    Then security server owner name: "<ownerName>", class: "<ownerClass>" and code: "<ownerCode>" are properly displayed
    And security server code: "<serverCode>" is properly displayed
    And security server address: "<address>" is displayed
    And security server registration date and time is properly displayed
    Examples:
      | serverCode | ownerName                      | ownerClass | ownerCode                | address                         |
      | E2E-SS1    | E2E TC1 Member with Subsystems | E2E-TC1    | e2e-tc1-member-subsystem | security-server-address-E2E-SS1 |

  Scenario: User can change security server address
    Given user opens security server details for "E2E-SS1"
    And security server address: "security-server-address-E2E-SS1" is displayed
    When user opens server edit address dialog
    And enters new server address: "security-server-address-E2E-SS1-updated"
    But closes server address dialog
    Then security server address: "security-server-address-E2E-SS1" is displayed
    When user opens server edit address dialog
    And enters new server address: "security-server-address-E2E-SS1-updated"
    And saves server address
    Then security server address: "security-server-address-E2E-SS1-updated" is displayed

  Scenario: User can delete security server
    Given user opens security server details for "E2E-SS3"
    When user opens delete security server dialog
    Then delete button is disabled
    When enters server code: "invalid-code"
    Then delete button is disabled
    And closes delete security server dialog
    When user opens delete security server dialog
    When enters server code: "E2E-SS3"
    And deletes security server
    Then security servers list is displayed
    And list doesn't contain security server with code "E2E-SS3"
