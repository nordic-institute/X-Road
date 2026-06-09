@SecurityServer
@UI
@Initialization
Feature: 0300 - SS: Keys and certificates

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And signer service is restarted

  @Download
  Scenario Outline: <$label> key is added and imported
    Given Proxy healthcheck check "PROXY_AUTH_KEY_OCSP_READINESS_CHECK" is "UP" with status "AWAITING_CERT_CHAIN"
    And Keys and certificates tab is selected
    And Token: <$token> is present and expanded
    When Token: <$token> - Add key wizard is opened
    And Key Label is set to "<$label>"
    And CSR details Usage is set to "<$usage>", Client set to "<$client>", Certification Service to "<$certService>" and CSR format "PEM"
    And Generate "<$usage>" CSR is set to DNS "<$dns>" and Organization "ui-test"
    And CSR with extension "pem" successfully generated
    And Token: <$token> - has key with label "<$label>"
    Then CSR is processed by test CA
    And Token: <$token> - Generated certificate is imported
    And Token: <$token> - has key "<$label>" with status "<$certStatus>" and ocsp status "<$ocspStatus>"
    And Token: <$token> - has "<$usage>" key "<$label>" with correct fixed automatic renewal status
    And Token: <$token>, key "<$label>" generate CSR button is disabled
    Examples:
      | $token      | $usage         | $label             | $client      | $dns  | $certService | $certStatus | $ocspStatus |
      | softToken-0 | SIGNING        | test signing key   | DEV:COM:1234 | ui   | Test CA      | Registered  | Good        |
      | softToken-0 | AUTHENTICATION | test auth key      |              | ui   | Test CA      | Saved       | Disabled    |

  Scenario: Token edit page is navigable
    Given Keys and certificates tab is selected
    When Token: softToken-0 edit page is opened
    Then Token Alert about token policy being enforced is present


  Scenario: Add key wizard is navigable
    Given Keys and certificates tab is selected
    And Token: softToken-0 is present and expanded
    When Token: softToken-0 - Add key wizard is opened
    Then Add key wizard is closed
    When Token: softToken-0 - Add key wizard is opened
    And Key Label is set to ""
    And CSR details Usage is set to "AUTHENTICATION", Client set to "", Certification Service to "Test CA" and CSR format "DER"
    And Add key wizard Generate CSR step is closed
    And Token: softToken-0 - has 2 auth keys, 2 sign keys



  Scenario: Certificate format is preselected
    Given Keys and certificates tab is selected
    And Token: softToken-0 is present and expanded
    When Token: softToken-0 - Add key wizard is opened
    And Key Label is set to "csr format fixed to pem"
    And CSR details Usage is set to "AUTHENTICATION", Client set to "", Certification Service to "New CA" and CSR format "PEM" preselected
    And Generate "AUTHENTICATION" CSR is set to DNS "ss0" and Organization "ui-test"
    And CSR with extension "pem" successfully generated

