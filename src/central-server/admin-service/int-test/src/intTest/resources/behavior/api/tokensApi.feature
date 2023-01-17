@TokensApi
Feature: Tokens API

  Scenario: Login token
    Given Signer.getToken response is mocked for token 'token-id-1'
    And Signer.activateToken is mocked for token 'token-id-1'
    Then User can login token 'token-id-1' with password '1234'

  Scenario: Logout token
    Given Signer.getToken response is mocked for active token 'token-id-2'
    And Signer.deactivateToken is mocked for token 'token-id-2'
    Then User can logout token 'token-id-2'
