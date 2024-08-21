Feature: 0050 - Signer: general

  Scenario: Signer client timeout works
    Given signer client initialized with timeout 10 milliseconds
    Then getTokens fails with timeout exception
    And signer client initialized with default settings

  Scenario: Signer admin port returns diagnostics
    When certification service diagnostics is requested
    Then diagnostic response contains diagnostics for "CN=Test CA, O=Test"
