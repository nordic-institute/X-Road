@CentralServer
@CertificationService
Feature: 0340 - CS: Settings -> Global Resources

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
    And Global Resources sub-tab is selected

  Scenario: Global group is added and listed
    Given Global group security-server-owners is present in list
    When Add Global Group button is clicked
    And Dialog Save button is of disabled status
    And Add Global Group dialog is submitted with code "e2e-test-group" and description "generic desc"
    Then Global group list elements are validated
      | $code                  | $condition |
      | security-server-owners | present    |
      | e2e-test-group         | present    |
