@CentralServer
@MemberClass
Feature: 0300 - CS: System Settings -> System parameters  -> Member Classes

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And System settings sub-tab is selected
    And Member class list is set to All rows per page

  Scenario: Multiple member classes are created and present in the list
    When A set of member classes are added
      | $code   | $description          |
      | E2E-TC1 | generated description |
      | E2E-TC2 | generated description |
      | E2E-TC3 | generated description |
      | E2E-TC4 | generated description |
      | E2E-TC5 | generated description |
      | E2E-TC6 | generated description |
    And Member class list is set to All rows per page
    Then A set of member classes are visible
      | $code   | $description          |
      | E2E-TC1 | generated description |
      | E2E-TC2 | generated description |
      | E2E-TC3 | generated description |
      | E2E-TC4 | generated description |
      | E2E-TC5 | generated description |
      | E2E-TC6 | generated description |

  Scenario: Member Class description can be modified
    And A new member class E2E-TC0400-1 with description initial-desc is added
    When Member class E2E-TC0400-1 edit dialog is opened
    And Member class description is set to modified-desc in popup
    And Dialog Save button is clicked
    Then A member with code E2E-TC0400-1 and description modified-desc is visible

  Scenario: Member Class can be deleted
    And A new member class E2E-TC0400-2 with description generated description is added
    When Member class E2E-TC0400-2 delete button is clicked
    And Dialog Save button is clicked
    Then A member with code E2E-TC0400-2 is not present
