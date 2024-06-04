@SecurityServer
@Initialization
Feature: 0505 - SS: ACME

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And signer service is restarted

  Scenario Outline: New key is added certificate ordered and imported
    Given Keys and certificates tab is selected
    And Token: <$token> is present and expanded
    When Token: <$token> - Add key wizard is opened
    And Key Label is set to "<$label>"
    And CSR details Usage is set to "<$usage>", Client set to "<$client>", Certification Service to "<$certService>" and CSR format "DER"
    And Generate "<$usage>" CSR is set to DNS "<$dns>" and Organization "ui-test"
    And ACME order is made
    Then Token: <$token> - has key "<$label>" with status "<$certStatus>"
    And Token: <$token>, key "<$label>" generate CSR button is disabled
    Examples:
      | $token      | $usage         | $label                  | $client           | $dns | $certService | $certStatus |
      | softToken-0 | SIGNING        | test acme signing key   | DEV:COM:1234      | ss0  | Test CA     | Registered  |
  #    | softToken-0 | AUTHENTICATION | test acme auth key      |                  | ss0  | Test CA     | Saved       |

  Scenario: Certificate ordering is disabled when external account binding credentials are required but missing
    Given Keys and certificates tab is selected
    And Token: softToken-0 is present and expanded
    When Token: softToken-0 - Add key wizard is opened
    And Key Label is set to "sign key for eab"
    And CSR details Usage is set to "SIGNING", Client set to "4321", Certification Service to "Test CA" and CSR format "DER"
    And Generate "SIGNING" CSR is set to DNS "ss0" and Organization "ui-test"
    Then Wizard CSR page ACME order button is disabled
    When CSR with extension "der" successfully generated
    And Token: softToken-0 - ACME order dialog is opened for key "sign key for eab"
    Then ACME order dialog Order button is disabled

  @Skip
  Scenario: Certificate is ordered on existing CSR
    Given Keys and certificates tab is selected
    And Token: softToken-0 is present and expanded
    When Token: softToken-0 - CSR of key "key for multiple csr" is used to order certificate
    Then Token: softToken-0 - has key "key for multiple csr" with status "Saved"

