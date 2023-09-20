@SecurityServer
@Initialization
Feature: 0300 - SS: Keys and certificates

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret

  Scenario Outline: <$label> key is added and imported
    Given Keys and certificates tab is selected
    And Token: <$token> is present and expanded
    When Token: <$token> - Add key wizard is opened
    And Key Label is set to "<$label>"
    And CSR details Usage is set to "<$usage>", Client set to "<$client>", Certification Service to "<$certService>" and CSR format "PEM"
    And Generate CSR is set to DNS "<$dns>", Organization "ui-test" and CSR successfully generated
    And Token: <$token> - has key with label "<$label>"
    Then CSR is processed by test CA
    And Token: <$token> - Generated certificate is imported
    And Token: <$token> - has key "<$label>" with status "<$certStatus>"
    Examples:
      | $token      | $usage         | $label             | $client          | $dns | $certService      | $certStatus |
      | softToken-0 | SIGNING        | test signing token | CS:GOV:0245437-2 |      | X-Road Test CA CN | Registered  |
      | softToken-0 | AUTHENTICATION | test auth token    |                  | ss1  | X-Road Test CA CN | Saved       |


  Scenario Outline: New key with with empty label is created
    Given Keys and certificates tab is selected
    And Token: <$token> is present and expanded
    When Token: <$token> - Add key wizard is opened
    And Key Label is set to "<$label>"
    And CSR details Usage is set to "<$usage>", Client set to "<$client>", Certification Service to "<$certService>" and CSR format "PEM"
    And Generate CSR is set to DNS "<$dns>", Organization "ui-test" and CSR successfully generated
    And Token: <$token> - has <$authKeyAmount> auth keys, <$signKeyAmount> sign keys
    Examples:
      | $token      | $usage         | $label | $client          | $dns | $certService      | $authKeyAmount | $signKeyAmount |
      | softToken-0 | SIGNING        |        | CS:GOV:0245437-2 |      | X-Road Test CA CN | 1              | 2              |
      | softToken-0 | AUTHENTICATION |        |                  | ss1  | X-Road Test CA CN | 2              | 2              |

  Scenario: Add key wizard is navigable
    Given Keys and certificates tab is selected
    And Token: softToken-0 is present and expanded
    When Token: softToken-0 - Add key wizard is opened
    Then Add key wizard is closed
    When Token: softToken-0 - Add key wizard is opened
    And Key Label is set to ""
    And CSR details Usage is set to "AUTHENTICATION", Client set to "", Certification Service to "X-Road Test CA CN" and CSR format "DER"
    And Add key wizard Generate CSR step is closed
    And Token: softToken-0 - has 2 auth keys, 2 sign keys

  Scenario: CSR can be deleted
    Given Keys and certificates tab is selected
    And Token: softToken-0 is present and expanded
    When Token: softToken-0 - "AUTHENTICATION" CSR in position 1 is deleted
    Then Token: softToken-0 - has 1 auth keys, 2 sign keys
    When Token: softToken-0 - "SIGNING" CSR in position 1 is deleted
    Then Token: softToken-0 - has 1 auth keys, 1 sign keys
