@SecurityServer
@Client
Feature: 0530 - SS: Client internal servers

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client Internal servers are configured
    When Client "TestService" is opened
    And Internal servers sub-tab is selected
    And Internal server connection type is "HTTPS"
    Then Internal server connection type is set to "HTTPS NO AUTH"
    And Internal server certificate is exported
    When Information System TLS certificate is uploaded
    Then Information System TLS certificate is deleted
