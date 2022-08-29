@SecurityServer
Feature: Test samples

  A set of samples for future test writing.
  More examples can be found at: https://cucumber.io/docs/gherkin/reference/

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret

  Scenario: A failing scenario
    When Settings tab is selected
    Then Security Server TLS Key tab is selected

