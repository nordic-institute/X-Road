@SecurityServer
@ApiKeys
Feature: 0360 - SS: TLS key

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Keys and certificates tab is selected
    And Security Server TLS Key sub-tab is selected

  Scenario: User can export TLS key certificate
    When TLS certificate is exported
    Then TLS certificate is successfully downloaded and contains expected contents

  Scenario: User can generate new TLS key and certificate
    When Generate key button is clicked
    And New TLS key and certificate generation is confirmed
    Then New TLS key and certificate are successfully generated

  Scenario: User can import new TLS certificate
    Given TLS CSR generation view is opened
    And Distinguished name CN=localhost is entered
    And Generate CSR button is clicked
    And TLS CSR is successfully downloaded and contains expected contents
    And Done button in TLS CSR generation view is clicked
    When CSR is processed by test CA
    Then Generated TLS certificate is successfully imported
