@CentralServer
@Initialization
Feature: 0100 - CS: Initialization
  Verify that CS can be initialized from fresh state.

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret

  Scenario: Central server repeat PIN field shows check-mark only when it matches with PIN prompt
    Given Initial Configuration form is visible
    And PIN input is visible, but not confirmed
    When PIN 1111 is entered
    Then PIN confirmation is required
    When Confirmation PIN 1111 is entered
    Then PIN should be marked as matching
    When Confirmation PIN 1234 is entered
    Then PIN should be marked as mismatching
    And Submit button is disabled

  Scenario: Submit enabled only when all fields are filled
    Given Initial Configuration form is visible
    And Submit button is disabled
    When Instance identifier cs-e2e is entered
    And Central Server Address valid.example.org is entered
    And PIN 1111 is entered
    And Confirmation PIN 1111 is entered
    Then PIN should be marked as matching
    And Submit button is enabled
    When Confirmation PIN wrong-one is entered
    Then Submit button is disabled

  Scenario Outline: Submit <desc>
    Given Initial Configuration form is visible
    And Submit button is disabled
    And Instance identifier <instance-identifier> is entered
    And Central Server Address <cs-address> is entered
    And PIN <pin> is entered
    And Confirmation PIN <pin-repeat> is entered
    And Submit button is enabled
    When Submit button is clicked
    Then Submission failed with highlighted errors <result>
    Examples:
      | desc                                   | instance-identifier | cs-address             | pin          | pin-repeat   | result           |
      | fails with invalid instance identifier | INVALID&&::INSTANCE | valid.example.org      | Valid_Pin_11 | Valid_Pin_11 | IDENTIFIER-ERROR |
      | fails with invalid CS address          | CS-E2E              | invalid...address...fo | Valid_Pin_11 | Valid_Pin_11 | ADDRESS-ERROR    |
      | fails with weak PIN                    | CS-E2E              | valid.example.org      | 1            | 1            | PIN-ERROR        |

  Scenario: Central server is successfully initialized
    Given Initial Configuration form is visible
    And Submit button is disabled
    And Instance identifier CS-E2E is entered
    And Central Server Address valid.example.org is entered
    And PIN Valid_Pin_11 is entered
    And Confirmation PIN Valid_Pin_11 is entered
    And Submit button is enabled
    When Submit button is clicked
    Then Central Server is successfully initialized
