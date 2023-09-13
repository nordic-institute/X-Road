@SecurityServer
@Client
Feature: 0500 - SS: Client Add

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Add client was cancelled
    When Add client wizard is opened
    And Add Client details is filled with preselected client "CS:COM:1710128-9:TestClient" is opened
    And Add Client Token wizard page is closed
    Then Client "TestClient" is missing in the list

  Scenario Outline: Already existing client <$client> is added
    When Add client wizard is opened
    And Add Client details is filled with preselected client "<$clientIdentifier>" is opened
    And Add Client Token is set as "Token softToken-0"
    And Add Client Sign key label set to "<$label>"
    And Add Client CSR details Certification Service to "X-Road Test CA CN" and CSR format "PEM"
    And Add Client Generate CSR is set to organization "test-org" and csr is created
    Then Client "<$client>" with status "<$status>" is present in the list
    Examples:
      | $label           | $client    | $clientIdentifier           | $status    |
      | label-TestClient | TestClient | CS:COM:1710128-9:TestClient | REGISTERED |
      | label-Management | Management | CS:ORG:2908758-4:Management | REGISTERED |
