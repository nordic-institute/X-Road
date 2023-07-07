@TokensApi
Feature: Tokens API

  @Modifying
  Scenario: Login token
    Given Authentication header is set to SECURITY_OFFICER
    And Signer.getToken response is mocked for token 'token-id-1'
    And Signer.activateToken is mocked for token 'token-id-1'
    When User can login token 'token-id-1' with password '1234'
    Then Token login is successful


  Scenario: Login token is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When User can login token 'token-id-1' with password '1234'
    Then Response is of status code 403

  @Modifying
  Scenario: Logout token
    Given Authentication header is set to SECURITY_OFFICER
    And Signer.getToken response is mocked for active token 'token-id-2'
    And Signer.deactivateToken is mocked for token 'token-id-2'
    When User can logout token 'token-id-2'
    Then Token logout token is successful

  Scenario: Logout token is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When User can logout token 'token-id-2'
    Then Response is of status code 403
