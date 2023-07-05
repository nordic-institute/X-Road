@CentralServer
@GlobalGroupMembers
Feature: 0480 - CS: Manage Global Group members

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And Global Resources sub-tab is selected

  Scenario: Member or subsystem cannot be added to server owner group
    Given user opens global group: "security-server-owners" details
    And group has 0 members
    When user opens add members dialog
    And user selects members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    And user adds selected members
    Then error: "Cannot add members to server owner group" was displayed
    And user closes add members dialog
    And group has 0 members

  Scenario: Member and subsystem can be added to non owner group
    Given user opens global group: "e2e-test-group" details
    And group has 0 members
    When user opens add members dialog
    And user selects members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    And user adds selected members
    Then selected members are successfully added
    And group has 2 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |

  Scenario: Added members are filter out from member candidates list
    Given user opens global group: "e2e-test-group" details
    And group has 2 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    When user opens add members dialog
    Then user can't select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    When user filters selectable members list with query: "e2e-tc2-subsystem"
    Then user can't select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
    And user closes add members dialog

  Scenario: User can filter member candidates
    Given user opens global group: "e2e-test-group" details
    And group has 2 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    When user opens add members dialog
    Then user can select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
    When user filters selectable members list with query: "non-existing-code"
    Then user can't select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
    When user filters selectable members list with query: "e2e-tc2-member-subsystem"
    Then user can't select members:
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
    Then user can select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
    And user closes add members dialog

  Scenario: Selection is preserved while filtering
    Given user opens global group: "e2e-test-group" details
    And group has 2 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    When user opens add members dialog
    Then user can select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
    Then user selects members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
    When user filters selectable members list with query: "e2e-tc3-member-subsystem"
    Then user selects members:
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
    And user can't select members:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
    When user deletes selectable members filter query
    Then selected members are:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
    When user adds selected members
    Then selected members are successfully added
    And group has 4 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |

  Scenario: Group members can be deleted
    Given user opens global group: "e2e-test-group" details
    And group has 4 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem                   |
    When user opens delete member dialog for "CS-E2E:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem"
    Then user can't press delete button
    When user enters member code: "invalid code"
    Then user can't press delete button
    When user enters member code: "e2e-tc2-member-subsystem"
    Then user deletes group member
    And group has 3 members
    And group members list contains:
      | CS-E2E:E2E-TC1:e2e-tc1-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc2-member-subsystem |
      | CS-E2E:E2E-TC1:e2e-tc3-member-subsystem |
